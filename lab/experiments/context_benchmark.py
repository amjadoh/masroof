"""Explicit local experiment: no SMS upload, app dependency, DB write or rule activation."""
import argparse
import collections
import hashlib
import json
import platform
import sqlite3
import statistics
import time
import urllib.request
from pathlib import Path

MODEL = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
REVISION = "e8f8c211226b894fcb81acc59f3b34ba3efd5f42"
MODEL_FILE = "onnx/model_quint8_avx2.onnx"
# Fixed before evaluating the corpus. These are NOT calibrated financial probabilities.
MIN_SIMILARITY = 0.55
MIN_MARGIN = 0.05
PROTOTYPES = {
    "PURCHASE": ["تم تنفيذ عملية شراء لدى متجر باستخدام بطاقة", "A card purchase was completed at a merchant"],
    "TRANSFER_IN": ["وصلت حوالة واردة وتم إيداع المبلغ في حسابك", "An incoming transfer was credited to your account"],
    "TRANSFER_OUT": ["تم إرسال حوالة صادرة من حسابك إلى المستفيد", "An outgoing transfer was sent from your account"],
    "BILL_PAYMENT": ["تم سداد فاتورة خدمات أو مدفوعات حكومية", "A utility bill or government payment was paid"],
    "CARD_PAYMENT": ["تم سداد مستحقات البطاقة الائتمانية", "A repayment was received for the credit card balance"],
    "FINANCING_INSTALLMENT": ["تم خصم قسط التمويل أو سداد القرض", "A loan or financing installment repayment was deducted"],
    "WITHDRAWAL": ["تم سحب مبلغ نقدي من جهاز الصراف الآلي", "A cash withdrawal was completed at an ATM"],
    "REFUND": ["تم استرداد مبلغ وإلغاء عملية شراء سابقة", "A previous purchase was refunded or reversed"],
    "FEE": ["تم خصم رسوم خدمة بنكية من الحساب", "A bank service fee was charged to the account"],
    "BALANCE_NOTICE": ["إشعار برصيد الحساب الحالي دون تنفيذ عملية جديدة", "An account balance notification without a new transaction"],
    "NON_FINANCIAL": ["إشعار تسجيل دخول أو إضافة مستفيد أو إصدار كشف حساب", "A login, beneficiary activation or statement notification"],
    "OTP": ["رمز تحقق سري لإتمام عملية لم تنفذ بعد", "A one time verification code to authorize a transaction"],
}
FINANCIAL = set(PROTOTYPES) - {"BALANCE_NOTICE", "NON_FINANCIAL", "OTP"}


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def prepare(args):
    root = Path(__file__).resolve().parents[1]
    cases = []
    for file in sorted((root / "engine/src/test/resources/testdata/bank_aljazira").rglob("*.json")):
        if "reference" in file.parts:
            continue
        row = read_json(file)
        e = row["expected"]
        fields = {"family": "messageFamily", "direction": "direction", "amount": "amount",
                  "currency": "currency", "card": "cardLast4", "source": "sourceAccountLast4",
                  "destination": "destinationAccountLast4", "merchant": "merchant",
                  "balance": "availableBalance", "dueAmount": "outstandingBalance",
                  "occurredAtLocal": "occurredAt"}
        facts = {key: e[value] for key, value in fields.items() if e.get(value) is not None}
        if "occurredAtLocal" in facts:
            # Kotlin LocalDateTime.toString omits zero seconds; retain nonzero seconds.
            facts["occurredAtLocal"] = facts["occurredAtLocal"].removesuffix(":00") if len(facts["occurredAtLocal"]) == 19 else facts["occurredAtLocal"]
        cases.append({"id": "fixture:" + row["id"], "dataset": "fixtures", "sender": row["sender"],
                      "body": row["body"], "receivedAt": "2026-10-14T12:00:00Z",
                      "banks": [{"id": "BANK_ALJAZIRA", "name": "Fixture bank", "senders": [row["sender"]]}],
                      "expected": {"status": e["parseStatus"], "facts": facts}})
    if args.lab_db:
        if Path(args.lab_db).name.lower() == "masroof.db":
            raise ValueError("Production masroof.db is outside this lab experiment")
        # Snapshot must include a coherent WAL if present. Caller makes a backup first.
        with sqlite3.connect(Path(args.lab_db).resolve().as_uri() + "?mode=ro", uri=True) as db:
            banks = [json.loads(r[0])["bank"] for r in db.execute("SELECT packJson FROM lab_banks ORDER BY id")]
            library_row = db.execute("SELECT libraryJson FROM lab_shared_rule_versions ORDER BY version DESC LIMIT 1").fetchone()
            for row in db.execute("SELECT id,sender,body,receivedAt,expectedJson FROM lab_samples ORDER BY id"):
                import datetime
                case = {"id": "lab:" + row[0], "dataset": "lab_snapshot", "sender": row[1], "body": row[2],
                        "receivedAt": datetime.datetime.fromtimestamp(row[3] / 1000, datetime.timezone.utc).isoformat(),
                        "banks": banks, "expected": json.loads(row[4]) if row[4] else None}
                if library_row:
                    case["library"] = json.loads(library_row[0])
                cases.append(case)
    # Fixtures always use the actual built-in library exported by the JVM, not a Python copy.
    write_json(args.output, cases)
    print(json.dumps(dict(collections.Counter(c["dataset"] for c in cases))))


def download(args):
    target = Path(args.model_dir)
    target.mkdir(parents=True, exist_ok=True)
    files = [MODEL_FILE, "tokenizer.json", "tokenizer_config.json", "1_Pooling/config.json", "sentence_bert_config.json", "README.md"]
    manifest = {"model": MODEL, "revision": REVISION, "files": {}}
    for name in files:
        path = target / name
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists():
            temp = path.with_suffix(path.suffix + ".partial")
            with urllib.request.urlopen(f"https://huggingface.co/{MODEL}/resolve/{REVISION}/{name}", timeout=120) as response, temp.open("wb") as out:
                while chunk := response.read(1024 * 1024):
                    out.write(chunk)
            temp.replace(path)
        manifest["files"][name] = {"sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "bytes": path.stat().st_size}
        print("Ready:", name, flush=True)
    write_json(target / "manifest.json", manifest)


def hybrid(rules, model_family):
    """Classification suggestions only. Model can never promote parsing status."""
    family = rules.get("facts", {}).get("family") or "UNKNOWN"
    status = rules["status"]
    if family == "OTP" or status in {"SUCCESS", "NON_FINANCIAL"}:
        return family, status, "rules_authority"
    if family == "UNKNOWN":
        return model_family, status, "model_suggestion_review_retained"
    if model_family == family:
        return family, status, "agreement_review_retained"
    # Disagreement stays explicit rather than guessing one interpretation.
    return "UNKNOWN", status, "disagreement_review_retained"


def summary(rows):
    def metric(mode):
        labeled = [r for r in rows if r["expectedFamily"] is not None]
        known_truth = [r for r in labeled if r["expectedFamily"] != "UNKNOWN"]
        correct = sum(r[mode]["family"] == r["expectedFamily"] for r in labeled)
        predicted = [r for r in labeled if r[mode]["family"] != "UNKNOWN"]
        values = [x for r in rows for x in r[mode]["elapsedMs"]]
        per_family = {}
        for family in sorted({r["expectedFamily"] for r in labeled}):
            group = [r for r in labeled if r["expectedFamily"] == family]
            per_family[family] = {"correct": sum(r[mode]["family"] == family for r in group), "total": len(group)}
        return {"correct": correct, "labeled": len(labeled), "accuracyPct": 100 * correct / len(labeled) if labeled else None,
                "nonUnknownCoveragePct": 100 * len(predicted) / len(labeled) if labeled else None,
                "knownFamilyRecallPct": 100 * sum(r[mode]["family"] == r["expectedFamily"] for r in known_truth) / len(known_truth) if known_truth else None,
                "nonUnknownPrecisionPct": 100 * sum(r[mode]["family"] == r["expectedFamily"] for r in predicted) / len(predicted) if predicted else None,
                "financialLabelOnNonfinancialTruth": sum(r["expectedFamily"] in {"OTP", "NON_FINANCIAL", "BALANCE_NOTICE"} and r[mode]["family"] in FINANCIAL for r in labeled),
                "medianMs": statistics.median(values), "p95Ms": sorted(values)[max(0, int(len(values) * .95) - 1)],
                "meanMs": statistics.mean(values), "perFamily": per_family,
                **({"top1BeforeAbstentionCorrect": sum(r["model"]["topFamily"] == r["expectedFamily"] for r in labeled)} if mode == "model" else {})}
    return {m: metric(m) for m in ("rules", "model", "hybrid")}


def evaluate(args):
    import numpy as np
    import onnxruntime as ort
    from tokenizers import Tokenizer
    cases = read_json(args.input)
    rules = {r["id"]: r for r in read_json(args.rules)}
    assert set(rules) == {c["id"] for c in cases}, "Rules and model must evaluate exactly the same cases"
    model_dir = Path(args.model_dir)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    start = time.perf_counter()
    session = ort.InferenceSession(str(model_dir / MODEL_FILE), sess_options=options, providers=["CPUExecutionProvider"])
    tokenizer = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
    tokenizer.enable_truncation(max_length=128)
    load_ms = (time.perf_counter() - start) * 1000
    inputs = {i.name for i in session.get_inputs()}

    def embed(text):
        encoded = tokenizer.encode(text)
        data = {"input_ids": np.array([encoded.ids], dtype=np.int64),
                "attention_mask": np.array([encoded.attention_mask], dtype=np.int64),
                "token_type_ids": np.array([encoded.type_ids], dtype=np.int64)}
        hidden = session.run(None, {k: v for k, v in data.items() if k in inputs})[0]
        mask = data["attention_mask"][..., None]
        pooled = (hidden * mask).sum(axis=1) / np.maximum(mask.sum(axis=1), 1)
        vector = pooled[0]
        return vector / max(np.linalg.norm(vector), 1e-12), len(encoded.ids), bool(encoded.overflowing)

    start = time.perf_counter()
    prototypes = [(family, embed(text)[0]) for family, texts in PROTOTYPES.items() for text in texts]
    prototype_ms = (time.perf_counter() - start) * 1000
    vectors = np.stack([v for _, v in prototypes])
    family_order = list(PROTOTYPES)
    indices = {f: [i for i, (family, _) in enumerate(prototypes) if family == f] for f in family_order}

    def classify(body):
        vector, length, truncated = embed(body)
        sims = vectors @ vector
        scores = sorted(((f, float(max(sims[indices[f]]))) for f in family_order), key=lambda x: -x[1])
        top, second = scores[:2]
        margin = top[1] - second[1]
        family = top[0] if top[1] >= MIN_SIMILARITY and margin >= MIN_MARGIN else "UNKNOWN"
        return family, top, margin, length, truncated

    rows = []
    for case in cases:
        classify(case["body"])
        elapsed = []
        for _ in range(10):
            start = time.perf_counter()
            prediction = classify(case["body"])
            elapsed.append((time.perf_counter() - start) * 1000)
        family, top, margin, length, truncated = prediction
        reference = rules[case["id"]]
        outcome = reference["outcome"]
        hybrid_family, hybrid_status, reason = hybrid(outcome, family)
        # Both engines are executed in this shadow comparison; include both measured costs.
        combined = [value + reference["elapsedMs"][i % len(reference["elapsedMs"]) ] for i, value in enumerate(elapsed)]
        rows.append({"id": case["id"], "dataset": case["dataset"],
                     "expectedFamily": (case.get("expected") or {}).get("facts", {}).get("family"),
                     "rules": {"family": outcome.get("facts", {}).get("family") or "UNKNOWN", "status": outcome["status"],
                               "elapsedMs": reference["elapsedMs"], "fieldDifferences": reference.get("differences")},
                     "model": {"family": family, "topFamily": top[0], "similarity": top[1], "margin": margin,
                               "elapsedMs": elapsed, "tokenCount": length, "truncated": truncated},
                     "hybrid": {"family": hybrid_family, "status": hybrid_status, "reason": reason, "elapsedMs": combined}})
        print("Measured:", case["id"], flush=True)
    result = {"metadata": {"model": MODEL, "revision": REVISION, "modelFile": MODEL_FILE,
               "manifest": read_json(model_dir / "manifest.json"), "platform": platform.platform(),
               "processor": platform.processor(), "onnxruntime": ort.__version__, "numpy": np.__version__,
               "threads": 1, "provider": "CPUExecutionProvider", "loadMs": load_ms,
               "prototypePreparationMs": prototype_ms, "minSimilarity": MIN_SIMILARITY, "minMargin": MIN_MARGIN,
               "modelWarmupsPerCase": 1, "modelRepetitions": 10, "rulesWarmupsPerCase": 5, "rulesRepetitions": 25,
               "scope": "message-family classification only; model has no field extractor or financial acceptance",
               "timing": "desktop; JVM full parser versus Python tokenizer+ONNX classifier; hybrid sums both costs, not a joint Android measurement",
               "corpusSha256": hashlib.sha256(Path(args.input).read_bytes()).hexdigest(),
               "prototypes": PROTOTYPES},
              "datasets": {d: summary([r for r in rows if r["dataset"] == d]) for d in sorted({r["dataset"] for r in rows})},
              "rows": rows}
    write_json(args.output, result)
    print(json.dumps(result["datasets"], indent=2))


def report(args):
    from html import escape
    data = read_json(args.input)
    names = {"rules": "القواعد الحالية", "model": "النموذج", "hybrid": "الجمع المحافظ"}
    sections = []
    for dataset, metrics in data["datasets"].items():
        heading = "رسائل الاختبار الموثقة" if dataset == "fixtures" else "الرسائل المحفوظة في لقطة المختبر"
        rows = "".join(f"<tr><td>{names[mode]}</td><td>{m['correct']} / {m['labeled']}</td>"
                       f"<td>{m['accuracyPct']:.1f}%</td><td>{m['nonUnknownCoveragePct']:.1f}%</td>"
                       f"<td>{m['medianMs']:.2f}</td><td>{m['p95Ms']:.2f}</td></tr>" for mode, m in metrics.items())
        sections.append(f"<h2>{heading}</h2><div class='table'><table><thead><tr><th>الطريقة</th>"
                        f"<th>تصنيف صحيح</th><th>الدقة</th><th>التصنيف غير المجهول</th>"
                        f"<th>وسيط الزمن ms</th><th>المئين 95 ms</th></tr></thead><tbody>{rows}</tbody></table></div>")
    fixtures = [r for r in data["rows"] if r["dataset"] == "fixtures"]
    critical = [r for r in fixtures if r["expectedFamily"] == "UNKNOWN" and r["rules"]["status"] == "SUCCESS"]
    details = "".join("<tr>" + "".join(f"<td>{escape(str(value))}</td>" for value in
                      (r["id"].removeprefix("fixture:"), r["expectedFamily"], r["rules"]["family"],
                       r["model"]["family"], r["hybrid"]["family"])) + "</tr>" for r in fixtures)
    top1 = data["datasets"]["fixtures"]["model"]["top1BeforeAbstentionCorrect"]
    fields = sum(r["rules"]["fieldDifferences"] == [] for r in fixtures)
    fixture_count = len(fixtures)
    lab_count = sum(r["dataset"] == "lab_snapshot" for r in data["rows"])
    meta = data["metadata"]
    document = f"""<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>مقارنة محرك المختبر بالنموذج</title>
<style>body{{font-family:Tahoma,Arial,sans-serif;max-width:1060px;margin:32px auto;padding:0 18px;color:#173128;background:#fafbf9;line-height:1.9}}
h1{{font-size:26px}}h2{{font-size:20px;margin-top:32px}}table{{border-collapse:collapse;width:100%;font-size:14px}}
td,th{{padding:10px;text-align:right;border-bottom:1px solid #d9e4dd}}th{{background:#eaf1eb}}.table{{overflow-x:auto}}code{{direction:ltr;display:inline-block;overflow-wrap:anywhere}}small{{color:#496156}}</style>
<h1>تجربة مقارنة فهم الرسائل — 14 أكتوبر 2026</h1>
<p><strong>القرار: إبقاء القواعد الحالية للتجارب المعتادة. هذا النموذج وسياسة الجمع المختارة لم يحققا تحسنًا.</strong></p>
<p>المقارنة في تحديد نوع الرسالة فقط؛ النموذج لا يستخرج المبلغ أو البطاقة ولا يعتمد عمليات مالية.
الرسائل محفوظة محليًا، ولم تُرسل إلى خدمة ذكاء اصطناعي. لم يتغير تطبيق مصروف أو محركه المالي.</p>
{''.join(sections)}
<h2>كيف نقرأ النتيجة؟</h2>
<p>UNKNOWN يعني الامتناع عن الحسم. دقة التصنيف تحسبه صحيحًا فقط إذا كانت الإجابة المرجعية UNKNOWN.
تشمل التغطية التصنيفات غير المجهولة سواء كانت صحيحة أم خاطئة. قبل تطبيق حد الامتناع، أصاب الاختيار الأول للنموذج
في {top1} من {fixture_count} رسالة؛ لذلك ضعف النتيجة لا يرجع إلى حد الامتناع وحده.</p>
<p>النموذج أخطأ بين شراء وسداد بطاقة، وبين بعض إشعارات المستحقات والحركات المالية.
الجمع المحافظ أبقى القرارات التي قبلتها القواعد، واقترح تصنيفًا للمجهول، وامتنع عند تعارض حالات المراجعة.
حقق بعض التصحيحات، لكنه أضاع تصنيفات صحيحة أخرى؛ لا يثبت ذلك فشل جميع طرق الجمع أو النماذج المتخصصة.</p>
<h2>حدود المقارنة</h2><ul>
<li>القياس على كمبيوتر ويندوز، وليس جوال المستخدم. JVM يقيس المحلل كاملًا؛ Python/ONNX يقيس التصنيف فقط.</li>
<li>زمن الجمع تقدير بجمع قياسي المسارين، وليس تشغيلًا مباشرًا لمسار أندرويد مدمج.</li>
<li>القياس بعد الإحماء. تحميل النموذج استغرق {meta['loadMs']:.0f} ms، وإعداد الأوصاف {meta['prototypePreparationMs']:.0f} ms.</li>
<li>{fixture_count} رسالة من أمثلة الجزيرة المستخدمة أصلًا في التطوير؛ لا تمثل اختبارًا مستقلاً لبنوك جديدة. لقطة المختبر تحوي {lab_count} رسائل وتُعرض منفصلة.</li>
<li>لم يُدرب النموذج على تصحيحات مصروف. استُخدم نموذج تشابه جاهز مع وصفين لكل فئة، وحدود ثابتة 0.55 و0.05 دون ضبطها على الإجابات.</li>
<li>لم تُحذف أي رسالة بسبب خطأ في تصنيفها. عدد الرسائل التي تجاوزت حد 128 رمزًا: {sum(r['model']['truncated'] for r in data['rows'])}.</li>
</ul>
<h2>ملاحظات مهمة على القواعد نفسها</h2>
<p>تطابقت الحالة والحقول الممثلة والمعلّمة في {fields} من {fixture_count} رسالة. هذا مقياس أوسع من دقة النوع،
ولا يشمل كل حقول المحلل المرجعي. توجد صيغ حقول وتواريخ لا يغطيها المحرك العام بعد.</p>
<p>وجدت {len(critical)} حالات متعارضة يُفترض مراجعتها لكن محرك المختبر أعاد SUCCESS.
سياسة الجمع الحالية أبقتها لأنها تثق بنتائج القواعد المقبولة. هذه ملاحظة على المختبر العام؛ لم نختبر أو نغير
مسار مصروف الإنتاجي في هذه التجربة. لم نصلح الحالات أثناء المقارنة حتى يبقى خط الأساس ثابتًا.</p>
<h2>الخطوة المقترحة قبل الاعتماد</h2>
<p>إضافة هذه التعارضات كاختبارات للمحرك العام، ثم تجهيز مجموعة تعلم وتحقق واختبار منفصلة حسب صيغ الرسائل والبنوك.
نجرب بعدها مصنفًا متخصصًا مثل SetFit ونقارنه بالقواعد على الاختبار المحجوز، ثم نقيسه على أندرويد.
لا توجد حاليًا نتيجة تبرر تفعيل النموذج تلقائيًا.</p>
<details><summary>تفاصيل التصنيف لكل رسالة اختبار — دون نصوص SMS</summary><div class="table"><table>
<thead><tr><th>العينة</th><th>المتوقع</th><th>القواعد</th><th>النموذج</th><th>الجمع</th></tr></thead><tbody>{details}</tbody></table></div></details>
<h2>التوثيق الفني للتكرار</h2><p><code>{escape(meta['model'])}</code><br>
النسخة: <code>{escape(meta['revision'])}</code><br>ONNX Runtime: {escape(meta['onnxruntime'])}، CPU بخيط واحد.
حجم ملف الأوزان: {meta['manifest']['files'][MODEL_FILE]['bytes'] / 1_000_000:.1f} MB.</p>
<small>تُحفظ التفاصيل الخام وإجابات الرسائل محليًا؛ يعرض هذا التقرير المقاييس والتصنيفات فقط.</small></html>"""
    Path(args.output).write_text(document, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("prepare")
    p.add_argument("--output", required=True)
    p.add_argument("--lab-db")
    d = sub.add_parser("download")
    d.add_argument("--model-dir", required=True)
    r = sub.add_parser("evaluate")
    for name in ("input", "rules", "model-dir", "output"):
        r.add_argument("--" + name, required=True)
    p = sub.add_parser("report")
    p.add_argument("--input", required=True)
    p.add_argument("--output", required=True)
    args = parser.parse_args()
    {"prepare": prepare, "download": download, "evaluate": evaluate, "report": report}[args.command](args)


if __name__ == "__main__":
    main()
