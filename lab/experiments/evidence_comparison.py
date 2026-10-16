"""Prepare and score a paired lab-only experiment. Raw SMS/analysis stay local."""
import argparse
import hashlib
import html
import json
import math
import platform
import statistics
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from bank_sms_dataset import collect, grouped, ROOT


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def write(path, value):
    path = Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


FIELD_MAP = {"messageFamily": "family", "direction": "direction", "amount": "amount", "currency": "currency",
             "cardLast4": "card", "sourceAccountLast4": "source", "destinationAccountLast4": "destination",
             "merchant": "merchant", "availableBalance": "balance", "outstandingBalance": "dueAmount", "occurredAt": "occurredAtLocal"}
NUMERIC = {"amount", "balance", "dueAmount", "feeAmount", "exchangeRate"}


def prepare(directory):
    directory = Path(directory)
    rows = grouped(collect())
    fixtures = {}
    for path in (ROOT / "lab/engine/src/test/resources/testdata/bank_aljazira").rglob("*.json"):
        if path.parent.name == "reference":
            continue
        value = read(path); fixtures["fixture:" + value["id"]] = value["expected"]
    cases = []; manifest = []
    for row in rows:
        refs = [fixtures[r["id"]] for r in [row] + row.get("aliases", []) if r["id"] in fixtures]
        reference = {"status": "PARTIAL", "facts": {"family": row["label"]}}
        scope = "family_only"
        if refs:
            e = refs[0]
            facts = {target: e[source] for source, target in FIELD_MAP.items() if e.get(source) is not None}
            if facts.get("occurredAtLocal"):
                facts["occurredAtLocal"] = datetime.fromisoformat(facts["occurredAtLocal"]).isoformat(timespec="seconds").removesuffix(":00")
            reference = {"status": e["parseStatus"], "facts": facts}
            scope = "existing_fixture_fields"
        cases.append({"id": row["id"], "dataset": "known_repository_regression", "sender": "AlJazira", "body": row["text"],
                      "receivedAt": "2026-10-14T12:00:00Z", "banks": [{"id": "BANK_ALJAZIRA", "name": "Source bank", "senders": ["AlJazira"]}],
                      "expected": reference})
        manifest.append({"id": row["id"], "group": row["group"], "sha256": hashlib.sha256(row["text"].encode()).hexdigest(),
                         "scope": scope, "source": row["source"], "sourceBacked": row["id"].startswith(("inline:", "reference:"))})
    write(directory / "cases.json", cases)
    write(directory / "manifest.json", {"schema": 1, "rows": manifest, "count": len(cases),
        "purpose": "Known development/regression set, not blind new-bank validation. References are source expectations, never parser outputs.",
        "referenceLimits": "Optional absent source keys remain unreviewed. Status scored only for fixture-backed references. Existing expected fields are partial, not a complete-event gold standard."})
    print(f"Prepared {len(cases)} known cases, {sum(r['scope'] != 'family_only' for r in manifest)} with fixture fields")


def prepare_tuned(directory, knowledge_path):
    directory = Path(directory)
    knowledge = read(knowledge_path)
    knowledge["version"] = 2
    for rule in knowledge["rules"]:
        if rule["concept"] == "field.source":
            rule["phrases"] = list(dict.fromkeys(rule["phrases"] + ["من", "رقم حساب المرسل"]))
    cases = read(directory / "cases.json")
    for case in cases: case["knowledge"] = knowledge
    write(directory / "knowledge-tuned.json", knowledge)
    write(directory / "cases-tuned.json", cases)
    print("Prepared explicit development-only source-label tuning; app defaults unchanged")


def same(field, expected, actual):
    if expected is None or actual is None:
        return expected == actual
    if field in NUMERIC:
        from decimal import Decimal, InvalidOperation
        try:
            return Decimal(expected) == Decimal(actual)
        except InvalidOperation:
            return False
    if field == "occurredAtLocal":
        try:
            return datetime.fromisoformat(expected) == datetime.fromisoformat(actual)
        except ValueError:
            return False
    return expected == actual


def differences(reference, outcome, scope):
    assert not any(reference["facts"].get(f) is not None for f in reference.get("absentFields", [])), "Conflicting reviewed absence"
    result = []
    if scope != "family_only" and reference["status"] != outcome["status"]:
        result.append("status")
    facts = outcome.get("facts", {})
    for field, expected in reference["facts"].items():
        if expected is not None and not same(field, expected, facts.get(field)):
            result.append(field)
    for field in reference.get("absentFields", []):
        if facts.get(field) is not None and field not in result:
            result.append(field)
    return result


def percentile(values, quantile):
    ordered = sorted(values)
    return ordered[min(len(ordered)-1, math.ceil(len(ordered)*quantile)-1)] if ordered else None


def summarize(rows, manifest):
    by_id = {r["id"]: r for r in manifest["rows"]}
    assert len(rows) == len(by_id) and {r["id"] for r in rows} == set(by_id), "Missing or duplicate results"
    public = []
    for row in rows:
        meta = by_id[row["id"]]
        item = {**meta, "expectedFamily": row["expected"]["facts"]["family"], "modes": {}}
        for mode in ("shared", "evidence"):
            outcome = row[mode] if mode == "shared" else row[mode]["outcome"]
            diff = differences(row["expected"], outcome, meta["scope"])
            annotated = [k for k, v in row["expected"]["facts"].items() if v is not None] + row["expected"].get("absentFields", [])
            item["modes"][mode] = {"family": outcome["facts"].get("family"), "status": outcome["status"],
                "knowledgeVersion": next((t.split(":",1)[1] for t in outcome.get("trace", []) if t.startswith("knowledge:")), None),
                "differences": diff, "scoredFields": annotated, "elapsedMs": row[mode + "Ms"],
                "firstEncounterMs": row[mode + "FirstMs"], "criticalAcceptedErrors": [f for f in diff if f in {"family", "amount", "currency", "direction", "card", "source", "destination"} or f == "status" and row["expected"]["status"] != "SUCCESS"] if outcome["status"] == "SUCCESS" else []}
        public.append(item)
    metrics = {}
    subsets = {"all": public, "source_backed": [r for r in public if r["sourceBacked"]],
               "fixture_fields": [r for r in public if r["scope"] != "family_only"]}
    for name, subset in subsets.items():
        metrics[name] = {}
        for mode in ("shared", "evidence"):
            modes = [r["modes"][mode] for r in subset]
            fields = defaultdict(lambda: {"correct": 0, "total": 0})
            for row in modes:
                for field in row["scoredFields"]:
                    fields[field]["total"] += 1; fields[field]["correct"] += field not in row["differences"]
            covered = [r for r in subset if r["scope"] != "family_only"]
            accepted = [r for r in covered if r["modes"][mode]["status"] == "SUCCESS"]
            latency = [statistics.median(r["elapsedMs"]) for r in modes]
            metrics[name][mode] = {"total": len(subset), "familyCorrect": sum(r["modes"][mode]["family"] == r["expectedFamily"] for r in subset),
                "annotatedMatch": sum(not r["modes"][mode]["differences"] for r in covered), "annotatedTotal": len(covered),
                "acceptedAnnotated": len(accepted), "acceptedAnnotatedMatch": sum(not r["modes"][mode]["differences"] for r in accepted),
                "criticalAcceptedRows": sum(bool(r["modes"][mode]["criticalAcceptedErrors"]) for r in subset),
                "review": sum(r["status"] == "REVIEW_REQUIRED" for r in modes),
                "warmMedianMs": statistics.median(latency) if latency else None, "p95CaseMedianMs": percentile(latency, .95),
                "fields": dict(fields)}
    improvements = [r["id"] for r in public if set(r["modes"]["shared"]["differences"]) - set(r["modes"]["evidence"]["differences"])]
    regressions = [r["id"] for r in public if set(r["modes"]["evidence"]["differences"]) - set(r["modes"]["shared"]["differences"])]
    return {"schema": 1, "engines": {"shared": "shared-2", "evidence": "evidence-1"}, "metrics": metrics,
            "knowledgeVersions": sorted({r["modes"]["evidence"]["knowledgeVersion"] for r in public if r["modes"]["evidence"]["knowledgeVersion"] is not None}),
            "improvedRows": improvements, "regressedRows": regressions, "rows": public,
            "limits": ["Known repository development/regression examples, not an unseen-bank or blind test.",
                "Expected fields are partial; no complete financial-event accuracy claim.",
                "No fresh real phone SMS or verified multi-bank holdout is available.",
                "Desktop JVM, 5 warmups and 25 alternating paired measurements; full extraction plus explanation in evidence workload.",
                "Published output contains no SMS text, extracted account/card/amount values or model weights."],
            "host": {"system": platform.system(), "machine": platform.machine()},
            "sourceHashes": {str(p.relative_to(ROOT)).replace('\\', '/'): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted((ROOT / "lab/engine/src/main/kotlin/com/baraa/masroof/lab/engine/evidence").glob("*.kt"))}}


def report(result, target):
    def table(name):
        rows = []
        for mode, label in (("shared", "قواعد المختبر الحالية"), ("evidence", "محرك الأدلة")):
            m = result["metrics"][name][mode]
            rows.append(f"<tr><td>{label}</td><td>{m['familyCorrect']} / {m['total']}</td><td>{m['annotatedMatch']} / {m['annotatedTotal']}</td><td>{m['review']}</td><td>{m['warmMedianMs']:.3f}</td><td>{m['p95CaseMedianMs']:.3f}</td></tr>")
        return "<table><tr><th>الطريقة</th><th>صحة النوع</th><th>مطابقة كل الحقول المراجعة</th><th>للمراجعة</th><th>وسيط الزمن ms</th><th>p95 لوسيط الرسائل</th></tr>" + "".join(rows) + "</table>"
    fields = []
    for field, a in result["metrics"]["fixture_fields"]["shared"]["fields"].items():
        b = result["metrics"]["fixture_fields"]["evidence"]["fields"].get(field, {"correct": 0, "total": 0})
        fields.append(f"<tr><td>{html.escape(field)}</td><td>{a['correct']} / {a['total']}</td><td>{b['correct']} / {b['total']}</td></tr>")
    changes = []
    for row in result["rows"]:
        a = row["modes"]["shared"]; b = row["modes"]["evidence"]
        if a["differences"] != b["differences"]:
            changes.append(f"<tr><td>{html.escape(row['id'])}</td><td>{html.escape(', '.join(a['differences'])) or 'مطابق'}</td><td>{html.escape(', '.join(b['differences'])) or 'مطابق'}</td></tr>")
    markup = """<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>نتائج تجربة محرك الأدلة</title><style>body{font:17px/1.9 'Segoe UI',Tahoma;background:#f1f5f5;color:#17363c;margin:0}main{max-width:1140px;margin:auto;padding:25px}section,header{background:white;border-radius:16px;padding:25px;margin-bottom:20px}header{background:#173f44;color:white}table{width:100%;border-collapse:collapse;font-size:14px}td,th{padding:12px;text-align:right;border-bottom:1px solid #dce6e6}th{background:#eaf3f1}a{color:#087265}.note{padding:18px;background:#fff3d6;border-radius:10px}.scroll{overflow:auto}li{margin:8px 0}@media print{body{background:white}main{padding:0}section{border:1px solid #ddd}tr{break-inside:avoid}}</style><main><header><h1>نتائج تنفيذ وتجربة محرك الأدلة</h1><p>Masroof Parsing Lab • 14 أكتوبر 2026 • قياس فعلي على أمثلة المستودع</p></header>"""
    markup += "<section><h2>النتائج المقاسة</h2><p>الاختبار يشغّل قواعد المختبر الحالية shared-2 ومحرك الأدلة على الرسائل نفسها. المقارنة لا تقيس دقة محلل الجزيرة الأساسي في مصروف. لا يستعمل الاختبار نموذجًا لغويًا ولا يغير المحرك الافتراضي. نتائج SetFit السابقة تخص تصنيف النوع ولا تدخل هذه القياسات.</p><div class='note'>الأمثلة معروفة من التطوير السابق. هذا اختبار انحدار وتجربة بنية، وليس اختبارًا أعمى على بنك جديد. لا تتوفر مجموعة جديدة مستقلة من رسائل جوال فعلية لإثبات التعميم.</div><h3>جميع الرسائل المعروفة</h3><div class='scroll'>" + table("all") + "</div><h3>النصوص ذات المصدر المضمّن أو الأرشيف</h3><div class='scroll'>" + table("source_backed") + "</div><h3>الرسائل ذات الحقول المرجعية في ملفات الاختبار</h3><div class='scroll'>" + table("fixture_fields") + "</div><p>المطابقة تشمل حالة التحليل والحقول المرجعية المتاحة فقط. بعض الحقول غير معلّمة؛ لذلك لا نطلق عليها دقة العملية كاملة. وسيط الزمن يحسب من 25 تكرارًا لكل رسالة بعد 5 تهيئات، مع تبديل ترتيب المحركين. p95 هنا عبر أوساط الرسائل، وليس عبر كل الاستدعاءات.</p><p>الإصدار الأول للأدلة يتضمن علاقات وعبارات إضافية؛ لذلك نقارن نظامين كاملين ولا نعزل أثر البنية وحدها. تجربة وسمي المصدر اللاحقة تثبت الكود وتغير المعرفة فقط.</p></section>"
    markup += "<section><h2>استخراج الحقول</h2><table><tr><th>الحقل</th><th>الحالي</th><th>الأدلة</th></tr>" + "".join(fields) + "</table></section>"
    markup += "<section><h2>الفروق والأخطاء</h2><p>يمكن أن تتحسن بعض حقول رسالة وتتراجع أخرى؛ تظهر في القائمتين ولا نخفي ذلك وراء المجموع.</p><div class='scroll'><table><tr><th>معرف المصدر</th><th>اختلافات الحالي عن المرجع</th><th>اختلافات الأدلة عن المرجع</th></tr>" + "".join(changes) + "</table></div></section>"
    old = result["metrics"]["all"]["shared"]; new = result["metrics"]["all"]["evidence"]
    ratio = new["p95CaseMedianMs"] / old["p95CaseMedianMs"]
    markup += f"<section><h2>ما الذي تعنيه المقارنة؟</h2><p>صحة النوع: الحالي {100*old['familyCorrect']/old['total']:.1f}%، والأدلة {100*new['familyCorrect']/new['total']:.1f}%. ظهرت أخطاء حرجة ضمن الحقول المراجعة في {old['criticalAcceptedRows']} نتائج مقبولة للمحرك الحالي، و{new['criticalAcceptedRows']} لمحرك الأدلة. هذا العدد لا يكشف أخطاء الحقول غير المعلّمة.</p><p>نسبة p95 لوسيط الرسائل: {ratio:.2f} من الحالي؛ هدف ألا يزيد عن الضعف {'تحقق في القياس المكتبي' if ratio <= 2 else 'لم يتحقق في القياس المكتبي'}. قياس المحاكي منفصل، ولا يثبت سرعة هاتف المستخدم.</p><p>نطاق النسخة الأولى: العربية والإنجليزية، وعملات SAR/USD/EUR/GBP، ومعرفات من أربعة أرقام بصيغ مدعومة، وتاريخ عملية ISO. صيغ التاريخ الأخرى، الأرقام الكاملة غير المدعومة، والملكية وتعدد الأحداث تحتاج مراجعة أو توسعة. المكتبات اللغوية والنموذج ليست مطلوبة لتشغيل هذه النسخة.</p></section>"
    if result.get("verification"):
        verification = result["verification"]
        markup += "<section><h2>الفحوص المنفذة</h2><ul>" + "".join(f"<li>{html.escape(str(k))}: {html.escape(str(v))}</li>" for k, v in verification.items()) + "</ul></section>"
    if result.get("android"):
        device = result["android"]
        device_rows = "".join(f"<tr><td>{label}</td><td>{device[mode]['medianMs']:.2f}</td><td>{device[mode]['p95Ms']:.2f}</td></tr>" for mode, label in (("shared", "قواعد المختبر"), ("evidence", "الأدلة — معرفة 1")))
        memory = device["instrumentationProcessPss"]
        markup += f"<section><h2>قياس أندرويد</h2><p>محاكي API 30، x86_64؛ أربع رسائل مصطنعة، 25 تكرارًا بعد التهيئة. هذه عينة تشخيص صغيرة، ولا تدخل دقة رسائل المستودع.</p><table><tr><th>الطريقة</th><th>وسيط الزمن ms</th><th>p95 للاستدعاءات ms</th></tr>{device_rows}</table><p>ذاكرة عملية الاختبار PSS: بداية {memory['initialKb']/1024:.1f} MiB، وأعلى عينة مسجلة {memory['sampledMaxKb']/1024:.1f} MiB. تشمل الواجهة وأدوات الاختبار والمحركين؛ ليست ذروة استهلاك محرك منفرد، ولا قياسًا على هاتف حقيقي.</p></section>"
    if result.get("tunedExperiment"):
        tuned = result["tunedExperiment"]["metrics"]["all"]["evidence"]
        markup += f"<section><h2>تجربة تحسين المعرفة دون محلل بنك جديد</h2><p>بعد فحص أخطاء مجموعة التطوير، أضفنا عبارتي «من» و«رقم حساب المرسل» إلى وسوم حساب المصدر في نسخة معرفة منفصلة 2. لم يتغير كود المحرك أو اسم البنك، ولم تتغير المعرفة الافتراضية داخل التطبيق. هذه معايرة على رسائل معروفة، وليست اختبارًا مستقلًا.</p><table><tr><th>الطريقة</th><th>صحة النوع / 70</th><th>مطابقة الحقول / 40</th><th>أخطاء حرجة في نتائج مقبولة ضمن الحقول المراجعة</th></tr><tr><td>القواعد الحالية</td><td>{old['familyCorrect']}</td><td>{old['annotatedMatch']}</td><td>{old['criticalAcceptedRows']}</td></tr><tr><td>الأدلة — معرفة 1 الافتراضية</td><td>{new['familyCorrect']}</td><td>{new['annotatedMatch']}</td><td>{new['criticalAcceptedRows']}</td></tr><tr><td>الأدلة — معرفة 2 التجريبية</td><td>{tuned['familyCorrect']}</td><td>{tuned['annotatedMatch']}</td><td>{tuned['criticalAcceptedRows']}</td></tr></table><p>لتكرارها من الواجهة: القواعد ← معرفة الأدلة ← وسم حساب المصدر، ثم أضف العبارتين إلى القائمة الحالية، وعاين الأثر قبل حفظ الإصدار. الرقم صفر إن ظهر يخص هذه الحقول وهذه المجموعة فقط؛ ولا يثبت غياب الأخطاء على رسائل أخرى.</p></section>"
    images = [name for name in ("evidence-comparison.png", "evidence-knowledge.png") if (Path(target).parent / name).exists()]
    if images:
        markup += "<section><h2>الواجهة الفعلية على المحاكي</h2><p>لقطات من حزمة اختبار معزولة تعرض مكوّن المختبر نفسه ببيانات مصطنعة. نسخة المستخدم تبقى داخل تطبيق مصروف المعتاد.</p>" + "".join(f"<img src='{name}' alt='واجهة المختبر على المحاكي' style='max-width:100%;width:410px;margin:8px;border:1px solid #ddd;border-radius:16px'>" for name in images) + "</section>"
    markup += "<section><h2>كيف تجربها على جوالك؟</h2><p>الخطوات تخص نسخة مصروف التي تتضمن هذا التعديل؛ رفع PR وحده لا يحدّث النسخة المثبتة على جوالك.</p><ol><li>افتح الإعدادات ← عن التطبيق ← إعدادات المطورين ← مختبر تحليل رسائل البنوك.</li><li>أضف البنك والمرسل الدقيق، ثم افتح التجربة واختر البنك وتاريخ البداية والنهاية بمنتقي التاريخ واستورد الرسائل.</li><li>اختر مقارنة المحركين على الرسائل المحفوظة، أو النتائج ← مقارنة الأدلة، وحدد الفترة ثم شغّل المقارنة.</li><li>افتح الحقول وأسباب القرار لمشاهدة النص المميز والنتيجتين والتفسيرات، ثم راجع الإجابة الصحيحة. اختَر غير موجود للحقل الغائب فعلًا، واترك غير المراجع دون قيمة.</li><li>أعد المقارنة بعد تعليم الإجابات؛ التشغيل السابق يبقى محفوظًا بإجاباته وقت التشغيل.</li><li>من القواعد ← معرفة الأدلة، عدّل عبارة أو علاقة، وعاين أثرها على جميع بنوك المختبر قبل حفظ إصدار جديد، ثم أعد المقارنة.</li></ol><p>المقارنة يدوية في قاعدة المختبر وحدها. السرعات داخل الواجهة تشمل أول تشغيل وتصلح للتشخيص؛ قياس هاتفك الفعلي قد يختلف عن الكمبيوتر والمحاكي.</p></section>"
    markup += "<section><h2>قرار الاعتماد</h2><p>يبقى محرك الأدلة خيار مقارنة. يجب فحص التراجعات والأخطاء الحرجة على مجموعة مستقلة قبل جعله الافتراضي في المختبر. لا توجد آلية لترحيل النتائج إلى العمليات المالية الأساسية.</p><p>سجل القياس والاختبارات وحدود التنفيذ مرفقة في توثيق التجربة داخل المستودع. تقرير التنفيذ المخطط: <a href='masroof-lab-evidence-implementation-2026-10-14.html'>الآلية والمراحل</a>.</p></section></main></html>"
    Path(target).write_text(markup, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prepare");parser.add_argument("--prepare-tuned");parser.add_argument("--knowledge");parser.add_argument("--input");parser.add_argument("--manifest");parser.add_argument("--output");parser.add_argument("--report");parser.add_argument("--verification");parser.add_argument("--android");parser.add_argument("--tuned-input")
    args = parser.parse_args()
    if args.prepare:
        prepare(args.prepare);return
    if args.prepare_tuned:
        prepare_tuned(args.prepare_tuned, args.knowledge);return
    result = summarize(read(args.input), read(args.manifest))
    if args.verification: result["verification"] = read(args.verification)
    if args.android: result["android"] = read(args.android)
    if args.tuned_input:
        result["tunedExperiment"] = summarize(read(args.tuned_input), read(args.manifest))
        assert result["tunedExperiment"]["knowledgeVersions"] == ["2"], "Tuned measurement did not use knowledge v2"
        result["tunedExperiment"]["calibration"] = {"knowledgeVersion": 2, "addedSourceLabels": ["من", "رقم حساب المرسل"],
            "dataUse": "Known development examples; explicit after-error-analysis tuning, not blind evaluation; app defaults remain knowledge v1."}
    write(args.output, result)
    if args.report: report(result, args.report)
    print(json.dumps(result["metrics"], ensure_ascii=True, indent=2))


if __name__ == "__main__": main()
