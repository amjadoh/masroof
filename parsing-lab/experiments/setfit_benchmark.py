"""Local SetFit experiment with separate authored train/validation and held-out fixture files."""
import argparse
import collections
import hashlib
import json
import os
import re
import time
import unicodedata
import urllib.request
from difflib import SequenceMatcher
from pathlib import Path

from context_benchmark import MODEL, REVISION, read_json, write_json

REGRESSIONS = {"fixture:collision_purchase_statement_due_ar_001", "fixture:collision_titled_fee_transfer_ar_001",
               "fixture:collision_transfer_beneficiary_notice_ar_001"}


def download_base(args):
    target = Path(args.base_dir)
    files = ["model.safetensors", "config.json", "modules.json", "config_sentence_transformers.json",
             "sentence_bert_config.json", "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json", "1_Pooling/config.json", "README.md"]
    hashes = {}
    for name in files:
        path = target / name
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists():
            partial = path.with_suffix(path.suffix + ".partial")
            with urllib.request.urlopen(f"https://huggingface.co/{MODEL}/resolve/{REVISION}/{name}", timeout=120) as source, partial.open("wb") as output:
                while chunk := source.read(1024 * 1024):
                    output.write(chunk)
            partial.replace(path)
        hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest()
        print("Ready", name, flush=True)
    write_json(target / "download-manifest.json", {"model": MODEL, "revision": REVISION, "sha256": hashes})


def normalized(text):
    text = unicodedata.normalize("NFKC", text).lower()
    text = re.sub(r"[أإآ]", "ا", text).replace("ى", "ي")
    text = re.sub(r"[\u064b-\u065f\u0670]", "", text)
    text = re.sub(r"\d+", "#", text)
    text = re.sub(r"test[_ ]\w+", "placeholder", text)
    return re.sub(r"\s+", " ", text).strip()


def corpus_rows(path):
    corpus = read_json(path)
    splits = {"train": [], "validation": []}
    for label, split in corpus["classes"].items():
        for name in splits:
            for index, text in enumerate(split[name]):
                splits[name].append({"id": f"{name}:{label}:{index}", "label": label, "text": text})
    return splits


def audit_splits(splits):
    result = {"counts": {name: len(rows) for name, rows in splits.items()}, "pairs": {}}
    names = list(splits)
    for index, first in enumerate(names):
        for second in names[index + 1:]:
            best = (0.0, None, None)
            for a in splits[first]:
                for b in splits[second]:
                    x, y = normalized(a["text"]), normalized(b["text"])
                    if x == y:
                        raise ValueError(f"Duplicate template across {first}/{second}: {a['id']}, {b['id']}")
                    score = SequenceMatcher(None, x, y, autojunk=False).ratio()
                    if score > best[0]:
                        best = (score, a["id"], b["id"])
            if best[0] >= .85:
                raise ValueError(f"Near duplicate across {first}/{second}: {best}")
            result["pairs"][first + "/" + second] = {"maxSimilarity": best[0], "pair": list(best[1:])}
    return result


def choose_threshold(probabilities, classes, labels):
    # Chosen ONLY on validation, never on held-out labels. Higher threshold breaks ties.
    best = None
    for threshold in [0.0, .15, .25, .35, .45, .55, .65, .75]:
        correct = 0
        for row, label in zip(probabilities, labels):
            index = max(range(len(row)), key=lambda i: row[i])
            prediction = classes[index] if row[index] >= threshold else "UNKNOWN"
            correct += prediction == label
        candidate = (correct, threshold)
        if best is None or candidate > best:
            best = candidate
    return {"correct": best[0], "total": len(labels), "threshold": best[1]}


def guarded_hybrid(outcome, predicted):
    family = outcome.get("facts", {}).get("family") or "UNKNOWN"
    status = outcome["status"]
    trace = " ".join(outcome.get("trace", []))
    if "ambiguous_classification" in trace or "shared.context." in trace or "تعارض اتجاه العملية" in trace:
        return family, status, "context_conflict_retained"
    if family != "UNKNOWN":
        return family, status, "known_rules_family_retained"
    # Sender/body errors are not an opportunity to infer a financial family.
    if not outcome.get("normalizedText"):
        return family, status, "routing_or_input_gate_retained"
    return predicted, status, "semantic_suggestion_review_retained"


def train(args):
    # Runtime settings precede imports: this path cannot send messages or fetch models.
    os.environ.update(HF_HUB_OFFLINE="1", HF_DATASETS_OFFLINE="1", TOKENIZERS_PARALLELISM="false", WANDB_DISABLED="true",
                      HF_HOME=str(Path(args.model_dir).resolve().parent / "lab-huggingface-cache"))
    import numpy as np
    import torch
    import setfit
    import sentence_transformers
    import transformers
    from datasets import Dataset
    from setfit import SetFitModel, Trainer, TrainingArguments
    torch.set_num_threads(4)
    torch.set_num_interop_threads(1)
    splits = corpus_rows(args.corpus)
    audit = audit_splits(splits)
    start = time.perf_counter()
    model = SetFitModel.from_pretrained(str(Path(args.base_dir).resolve()), local_files_only=True)
    model.model_body.max_seq_length = 128
    name, parameter = next((n, p) for n, p in model.model_body.named_parameters() if "encoder.layer.0.attention.self.query.weight" in n)
    before = parameter.detach().cpu().clone()
    load_ms = (time.perf_counter() - start) * 1000
    training = Dataset.from_list([{"text": r["text"], "label": r["label"]} for r in splits["train"]])
    config = TrainingArguments(output_dir=str(Path(args.model_dir) / "checkpoints"), batch_size=8,
        num_epochs=1, num_iterations=2, max_steps=32, max_length=128,
        body_learning_rate=2e-5, seed=20261014, report_to="none", save_strategy="no", logging_steps=8)
    trainer = Trainer(model=model, args=config, train_dataset=training)
    start = time.perf_counter()
    trainer.train()
    training_ms = (time.perf_counter() - start) * 1000
    assert not torch.equal(before, parameter.detach().cpu()), "SetFit must actually fine-tune the embedding body"
    model.save_pretrained(args.model_dir)
    texts = [r["text"] for r in splits["validation"]]
    probabilities = np.asarray(model.predict_proba(texts))
    classes = [str(c) for c in model.model_head.classes_]
    calibration = choose_threshold(probabilities.tolist(), classes, [r["label"] for r in splits["validation"]])
    metadata = {"model": MODEL, "revision": REVISION, "setfit": setfit.__version__, "torch": torch.__version__,
                "sentence_transformers": sentence_transformers.__version__, "transformers": transformers.__version__,
                "corpusSha256": hashlib.sha256(Path(args.corpus).read_bytes()).hexdigest(), "seed": 20261014,
                "trainCounts": dict(collections.Counter(r["label"] for r in splits["train"])),
                "validationCounts": dict(collections.Counter(r["label"] for r in splits["validation"])),
                "audit": audit, "calibration": calibration, "classes": classes,
                "loadMs": load_ms, "trainingMs": training_ms, "maxSteps": 32,
                "actualSteps": trainer.st_trainer.state.global_step,
                "embeddingBodyChanged": True, "checkedParameter": name,
                "modelHashes": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in Path(args.model_dir).iterdir() if p.suffix in {".safetensors", ".pkl"}},
                "cpuThreads": 4, "maxSequenceLength": 128,
                "training": "contrastive embedding fine-tuning plus logistic regression head; synthetic training data",
                "validation": [{"id": row["id"], "expected": row["label"], "predicted": classes[int(np.argmax(probabilities[i]))]} for i, row in enumerate(splits["validation"])]}
    write_json(Path(args.model_dir) / "experiment.json", metadata)
    print(json.dumps(metadata, indent=2), flush=True)


def score(rows, mode):
    labeled = [r for r in rows if r["expected"] is not None]
    correct = sum(r[mode]["family"] == r["expected"] for r in labeled)
    durations = sorted(x for r in rows for x in r[mode]["elapsedMs"])
    import statistics
    per_class = {}
    for family in sorted({r["expected"] for r in labeled}):
        tp = sum(r["expected"] == family and r[mode]["family"] == family for r in labeled)
        fp = sum(r["expected"] != family and r[mode]["family"] == family for r in labeled)
        fn = sum(r["expected"] == family and r[mode]["family"] != family for r in labeled)
        per_class[family] = {"tp": tp, "fp": fp, "fn": fn, "f1": 2 * tp / (2 * tp + fp + fn) if tp + fp + fn else 0}
    return {"correct": correct, "total": len(labeled), "accuracyPct": 100 * correct / len(labeled),
            "macroF1": statistics.mean(p["f1"] for p in per_class.values()),
            "medianMs": statistics.median(durations), "p95Ms": durations[max(0, int(len(durations)*.95)-1)],
            "perClass": per_class}


def evaluate(args):
    os.environ.update(HF_HUB_OFFLINE="1", HF_DATASETS_OFFLINE="1", TOKENIZERS_PARALLELISM="false",
                      HF_HOME=str(Path(args.model_dir).resolve().parent / "lab-huggingface-cache"))
    import numpy as np
    import torch
    from setfit import SetFitModel
    torch.set_num_threads(4)
    torch.set_num_interop_threads(1)
    cases = [c for c in read_json(args.input) if c["dataset"] == "fixtures"]
    splits = corpus_rows(args.corpus)
    splits["test"] = [{"id": c["id"], "text": c["body"]} for c in cases]
    audit = audit_splits(splits)
    metadata = read_json(Path(args.model_dir) / "experiment.json")
    assert metadata["corpusSha256"] == hashlib.sha256(Path(args.corpus).read_bytes()).hexdigest()
    start = time.perf_counter()
    model = SetFitModel.from_pretrained(args.model_dir, local_files_only=True)
    load_ms = (time.perf_counter() - start) * 1000
    model.model_body.max_seq_length = 128
    classes = [str(c) for c in model.model_head.classes_]
    threshold = metadata["calibration"]["threshold"]
    rules = {r["id"]: r for r in read_json(args.rules)}
    before = {r["id"]: r for r in read_json(args.before_rules)}
    rows = []
    for case in cases:
        def predict():
            p = np.asarray(model.predict_proba([case["body"]]))[0]
            index = int(np.argmax(p))
            return classes[index] if p[index] >= threshold else "UNKNOWN", classes[index], float(p[index])
        predict()
        durations = []
        for _ in range(10):
            start = time.perf_counter()
            family, top, confidence = predict()
            durations.append((time.perf_counter() - start) * 1000)
        reference = rules[case["id"]]
        old = before[case["id"]]
        hybrid_family, status, reason = guarded_hybrid(reference["outcome"], family)
        result = {"id": case["id"], "expected": case["expected"]["facts"]["family"],
                  "usedForConflictFix": case["id"] in REGRESSIONS,
                  "rulesBefore": {"family": old["outcome"]["facts"]["family"], "status": old["outcome"]["status"], "elapsedMs": old["elapsedMs"]},
                  "rules": {"family": reference["outcome"]["facts"]["family"], "status": reference["outcome"]["status"], "elapsedMs": reference["elapsedMs"]},
                  "setfit": {"family": family, "topFamily": top, "confidence": confidence, "elapsedMs": durations},
                  "hybrid": {"family": hybrid_family, "status": status, "reason": reason,
                             "elapsedMs": [d + reference["elapsedMs"][i] for i, d in enumerate(durations)]}}
        assert result["hybrid"]["status"] == result["rules"]["status"]
        rows.append(result)
        print("Measured", case["id"], flush=True)
    groups = {"all_40_training_held_out": rows,
              "not_used_for_three_conflict_fixes": [r for r in rows if not r["usedForConflictFix"]],
              "three_conflict_regressions": [r for r in rows if r["usedForConflictFix"]]}
    result = {"training": metadata, "splitAudit": audit, "modelLoadMs": load_ms,
              "timing": "desktop CPU; SetFit/PyTorch single-message classification; hybrid estimated sum; rules full JVM parsing",
              "groups": {name: {mode: score(group, mode) for mode in ("rulesBefore", "rules", "setfit", "hybrid")} for name, group in groups.items()},
              "rows": rows}
    write_json(args.output, result)
    print(json.dumps({name: {m: {k: v for k, v in r.items() if k != 'perClass'} for m, r in group.items()} for name, group in result["groups"].items()}, indent=2))


def report(args):
    from html import escape
    result = read_json(args.input)
    names = {"rulesBefore": "القواعد قبل الإصلاح", "rules": "القواعد بعد الإصلاح", "setfit": "SetFit المدرّب", "hybrid": "القواعد مع اقتراحات SetFit"}
    titles = {"all_40_training_held_out": "الرسائل الأربعون المحجوزة خارج التدريب",
              "not_used_for_three_conflict_fixes": "الرسائل الـ37 الأخرى بعد فصل حالات الإصلاح",
              "three_conflict_regressions": "حالات التعارض الثلاث المستخدمة في الإصلاح"}
    sections = []
    for group, modes in result["groups"].items():
        rows = "".join(f"<tr><td>{names[mode]}</td><td>{m['correct']} / {m['total']}</td><td>{m['accuracyPct']:.1f}%</td>"
                       f"<td>{m['macroF1']:.3f}</td><td>{m['medianMs']:.2f}</td><td>{m['p95Ms']:.2f}</td></tr>" for mode, m in modes.items())
        sections.append(f"<h2>{titles[group]}</h2><div class='table'><table><thead><tr><th>الطريقة</th><th>صحيح</th><th>دقة النوع</th><th>Macro F1</th><th>وسيط الزمن ms</th><th>المئين 95 ms</th></tr></thead><tbody>{rows}</tbody></table></div>")
    training = result["training"]
    details = "".join("<tr>" + "".join(f"<td>{escape(str(v))}</td>" for v in
        (r["id"].removeprefix("fixture:"), r["expected"], r["rulesBefore"]["family"], r["rules"]["family"], r["setfit"]["family"], r["hybrid"]["family"])) + "</tr>" for r in result["rows"])
    document = f"""<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>نتائج إصلاح المختبر وتدريب SetFit</title><style>body{{font-family:Tahoma,Arial,sans-serif;max-width:1100px;margin:30px auto;padding:0 18px;background:#fafbf9;color:#193a2a;line-height:1.9}}h1{{font-size:26px}}h2{{font-size:20px;margin-top:30px}}table{{border-collapse:collapse;width:100%;font-size:14px}}td,th{{padding:10px;text-align:right;border-bottom:1px solid #d7e2db}}th{{background:#e9f1eb}}.table{{overflow-x:auto}}code{{direction:ltr;display:inline-block;overflow-wrap:anywhere}}</style>
<h1>إصلاح تعارضات المختبر وتجربة SetFit — 14 أكتوبر 2026</h1>
<p><strong>النتيجة: نجح إصلاح التعارضات. نموذج SetFit المدرّب لم يتفوق على القواعد، والجمع لم يحسنها؛ يبقى النموذج تجربة غير مفعلة.</strong></p>
<p>جميع الأرقام تخص تصنيف نوع الرسالة، ولا تعني صحة استخراج كل الحقول أو الأثر المالي.
التدريب والقياس محليان على الكمبيوتر؛ لم يتغير محلل مصروف الأساسي أو قاعدة بياناته.</p>
{''.join(sections)}
<h2>ما الذي أصلحناه؟</h2><ul><li>جمع تاريخ الاستحقاق والمستحقات مع الشراء كأدلة متعارضة.</li>
<li>جمع حالة المستفيد غير النشط مع أدلة التحويل.</li><li>كشف عنوان رسوم حوالة مع أدلة أصل الحوالة.</li></ul>
<p>أصبحت الحالات الثلاث UNKNOWN وREVIEW_REQUIRED، مع أدلة توضح العائلات المتنافسة. لا تتغير مشتريات البطاقة التي تذكر المستحق فقط دون تاريخ استحقاق، ولا تُعامل تسمية المستفيد وحدها كإشعار متعارض.
تحسنت أيضًا رسالة إشعار المستفيد الموجودة ضمن العينات الأخرى نتيجة إضافة الدليل السياقي نفسه.</p>
<h2>كيف تم التدريب والفصل؟</h2><ul>
<li>78 مثالًا تعليميًا مصطنعًا: 6 لكل فئة، بالعربية والإنجليزية، مع 26 مثال تحقق منفصلًا. لم تستخدم رسائل العملاء في التدريب.</li>
<li>40 رسالة من ملفات الاختبار محجوزة بالكامل عن التدريب وعن اختيار الإعدادات. هذه العينات معروفة من تطوير القواعد والتقرير السابق؛ ليست اختبارًا أعمى أو لبنوك جديدة.</li>
<li>التدريب لم يقرأ ملف الاختبار. فُحص تكرار النصوص بعد توحيد الأرقام، وأُوقفت التجربة عند تشابه نصي مرتفع؛ أعلى تشابه تدريب/اختبار {result['splitAudit']['pairs']['train/test']['maxSimilarity']:.1%} وفق المقياس المستخدم. هذا لا يثبت غياب كل تشابه دلالي.</li>
<li>تم تعديل جسم النموذج اللغوي بالتدريب التقابلي ثم تدريب رأس انحدار لوجستي. تغيرت أوزان طبقة الانتباه التي فحصناها؛ الخطوات الفعلية {training['actualSteps']}، والزمن {training['trainingMs']/1000:.1f} ثانية.</li>
<li>حد الامتناع {training['calibration']['threshold']} اختير من مجموعة التحقق فقط، حيث أصاب {training['calibration']['correct']} من {training['calibration']['total']}.</li>
</ul><h2>ماذا تعني النتيجة؟</h2>
<p>تحسن القواعد من 31 إلى 35 إجابة صحيحة يرجع إلى الإصلاح السياقي: الحالات الثلاث وإشعار مستفيد مرتبط بها.
أمثلة SetFit التعليمية القليلة لا تمثل تنوع الصيغ البنكية الفعلية؛ انخفاض نتيجة الاختبار مقارنة بالتحقق يرجّح فجوة بين بيانات التعليم والرسائل.
هذه النتيجة تخص هذه البيانات والإعدادات، ولا تثبت عجز SetFit عن المهمة.</p>
<p>النموذج اقترح NON_FINANCIAL لرسالة غير معروفة، فخفض دقة الجمع بحالة واحدة. تظل حالة الرسالة للمراجعة؛ لا توجد ترقية تلقائية إلى عملية مالية.
حالات التعارض المحمية لا يستطيع النموذج تجاوزها.</p>
<h2>السرعة وحدود القياس</h2><p>زمن القواعد يشمل المحلل الكامل في JVM، وزمن SetFit يشمل تصنيف رسالة واحدة في PyTorch على CPU بأربعة خيوط. زمن الجمع تقدير بجمع القياسين.
القياسات بعد الإحماء وليست قياسات على الجوال. تحميل النموذج المدرّب استغرق {result['modelLoadMs']:.0f} ms.
مقارنة سرعة SetFit بسرعة نموذج ONNX السابق لا تعزل أثر التدريب، لاختلاف المشغل والضغط وعدد الخيوط.</p>
<h2>قبل الاعتماد</h2><p>أوصي بمراجعة إصلاح القواعد مستقلًا، وإبقاء النموذج غير مفعّل.
الخطوة اللازمة لتحسينه هي بيانات SMS تمثيلية معلّمة، مع فصل صيغ الرسائل والبنوك بين التدريب والتحقق والاختبار، ثم قياس أندرويد.
لا تتضمن التجربة نقل نتائج إلى السجل المالي، أو تعديل التصنيف الأساسي، أو تفعيل نموذج في التطبيق.</p>
<details><summary>تفاصيل التصنيف لكل عينة — دون نصوص SMS</summary><div class='table'><table><thead><tr><th>العينة</th><th>المتوقع</th><th>قبل</th><th>بعد</th><th>SetFit</th><th>الجمع</th></tr></thead><tbody>{details}</tbody></table></div></details>
<p>النموذج الأساسي: <code>{escape(training['model'])}</code><br>النسخة: <code>{escape(training['revision'])}</code><br>SetFit {escape(training['setfit'])}، PyTorch {escape(training['torch'])}.</p></html>"""
    Path(args.output).write_text(document, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    d = sub.add_parser("download")
    d.add_argument("--base-dir", required=True)
    t = sub.add_parser("train")
    for name in ("corpus", "base-dir", "model-dir"):
        t.add_argument("--" + name, required=True)
    e = sub.add_parser("evaluate")
    for name in ("corpus", "model-dir", "input", "rules", "before-rules", "output"):
        e.add_argument("--" + name, required=True)
    a = sub.add_parser("audit")
    a.add_argument("--corpus", required=True)
    a.add_argument("--input", required=True)
    r = sub.add_parser("report")
    r.add_argument("--input", required=True)
    r.add_argument("--output", required=True)
    args = parser.parse_args()
    if args.command == "audit":
        splits = corpus_rows(args.corpus)
        splits["test"] = [{"id": c["id"], "text": c["body"]} for c in read_json(args.input) if c["dataset"] == "fixtures"]
        print(json.dumps(audit_splits(splits), indent=2))
    else:
        {"train": train, "evaluate": evaluate, "download": download_base, "report": report}[args.command](args)


if __name__ == "__main__":
    main()
