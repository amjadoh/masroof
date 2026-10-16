"""Aggregate one held-out prediction per source sample across independent template folds."""
import argparse
from collections import defaultdict
from html import escape
from pathlib import Path
from context_benchmark import read_json, write_json
from setfit_benchmark import score


def aggregate(directory):
    directory = Path(directory)
    manifest = read_json(directory / "manifest.json")
    source = {r["id"]: r for r in manifest["rows"]}
    rows, training, audits = [], [], []
    for fold in range(3):
        data = read_json(directory / f"fold-{fold}-results.json")
        training.append({"fold": fold, **data["training"]})
        audits.append({"fold": fold, **data["splitAudit"]})
        for row in data["rows"]:
            assert source[row["id"]]["fold"] == fold
            rows.append({**{k: v for k, v in row.items() if k != "rulesBefore"}, "fold": fold, "group": source[row["id"]]["group"],
                         "trainingEligibleSource": source[row["id"]]["trainingEligible"],
                         "classSeenInFoldTraining": row["expected"] in data["training"]["classes"]})
    assert len(rows) == len(source) == len({r["id"] for r in rows})
    groups = {"bank_source_examples": [r for r in rows if r["trainingEligibleSource"]],
              "fixture_only_controls": [r for r in rows if not r["trainingEligibleSource"]],
              "all_examples": rows,
              "bank_source_classes_seen_in_training": [r for r in rows if r["trainingEligibleSource"] and r["classSeenInFoldTraining"]]}
    metrics = {}
    for name, group in groups.items():
        metrics[name] = {}
        for mode in ("rules", "setfit", "hybrid"):
            measured = score(group, mode)
            template_scores = defaultdict(list)
            for row in group:
                template_scores[row["group"]].append(row[mode]["family"] == row["expected"])
            measured["templateMeanAccuracyPct"] = 100 * sum(sum(v) / len(v) for v in template_scores.values()) / len(template_scores)
            measured["templates"] = len(template_scores)
            metrics[name][mode] = measured
    return {"manifest": manifest, "foldTraining": training, "splitAudits": audits, "groups": metrics, "rows": rows,
            "limitations": ["repository test examples, not a new user inbox or new bank", "authentic capture not independently verified",
                "fixture-only constructed controls never used for training", "no calibrated abstention: fixed top-1, threshold 0.0",
                "three independent models, each starts from the original pretrained base", "rare classes with one template can be absent from training folds",
                "desktop measurement, full JVM parser vs PyTorch classifier; hybrid is estimated sum", "classification only; extraction and direction are not independently modeled"]}


def report(data, output):
    names = {"rules": "قواعد المختبر", "setfit": "SetFit المدرّب", "hybrid": "القواعد مع اقتراحات SetFit"}
    titles = {"bank_source_examples": "أمثلة الرسائل من الاختبارات والأرشيف — النتيجة الأساسية",
              "fixture_only_controls": "حالات ملفات العينات الإضافية — لم تدخل التدريب مطلقًا",
              "all_examples": "المجموعة كاملة",
              "bank_source_classes_seen_in_training": "تشخيص إضافي: أمثلة الأنواع التي لها أمثلة تدريب في جولتها"}
    sections = []
    for group, modes in data["groups"].items():
        cells = "".join(f"<tr><td>{names[mode]}</td><td>{m['correct']} / {m['total']}</td><td>{m['accuracyPct']:.2f}%</td>"
                        f"<td>{m['templateMeanAccuracyPct']:.1f}%</td><td>{m['medianMs']:.2f}</td><td>{m['p95Ms']:.2f}</td></tr>" for mode, m in modes.items())
        sections.append(f"<h2>{titles[group]}</h2><div class='table'><table><thead><tr><th>الطريقة</th><th>صحيح</th><th>دقة النوع</th><th>متوسط دقة الصيغ</th><th>وسيط الزمن ms</th><th>المئين 95 ms</th></tr></thead><tbody>{cells}</tbody></table></div>")
    manifest = data["manifest"]
    source_rows = [r for r in data["rows"] if r["trainingEligibleSource"]]
    improved = [r for r in source_rows if r["rules"]["family"] != r["expected"] and r["hybrid"]["family"] == r["expected"]]
    reversed_transfer = [r for r in source_rows if r["expected"] == "TRANSFER_OUT" and r["setfit"]["family"] == "TRANSFER_IN"]
    folds = "".join(f"<tr><td>{f['fold'] + 1}</td><td>{f['train']}</td><td>{f['test']}</td><td>{f['trainGroups']}</td><td>{f['testGroups']}</td><td>{escape(', '.join(f['unseenTestClasses']) or 'لا يوجد')}</td></tr>" for f in manifest["folds"])
    detail = "".join("<tr>" + "".join(f"<td>{escape(str(x))}</td>" for x in (r["id"], r["fold"] + 1, r["expected"], r["rules"]["family"], r["setfit"]["family"], r["hybrid"]["family"])) + "</tr>" for r in data["rows"])
    document = f"""<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>SetFit على رسائل الاختبارات مع فصل الصيغ</title><style>body{{font-family:Tahoma,Arial,sans-serif;max-width:1150px;margin:30px auto;padding:0 18px;background:#fafbf9;color:#18372a;line-height:1.9}}h1{{font-size:26px}}h2{{font-size:20px;margin-top:30px}}table{{border-collapse:collapse;width:100%;font-size:14px}}td,th{{padding:10px;text-align:right;border-bottom:1px solid #d9e3dc}}th{{background:#e9f1eb}}.table{{overflow-x:auto}}</style>
<h1>تدريب SetFit على أمثلة الرسائل البنكية وفصل الصيغ</h1>
<h2>لأي مرحلة يستخدم النموذج؟</h2><p><strong>SetFit يصنّف نوع الرسالة فقط</strong>: شراء، حوالة، سداد، استرداد أو إشعار.
يقرأ النص ويستفيد من سياقه لتحديد الفئة؛ لكنه لا يستخرج المبلغ أو البطاقة أو الحساب أو التاجر أو التاريخ.
استخراج هذه الحقول يتم بمحرك القواعد. اتجاه الحركة يشتق من النوع والقواعد، وليس مخرجًا مستقلاً دُرّب عليه النموذج.
الحوالة الواردة لا تعني تلقائيًا دخلاً؛ ملكية الحسابات والأثر المالي مرحلتان أخريان.</p>
<h2>البيانات والفصل</h2><p>وسعنا البحث إلى 47 نص رسالة داخل اختبارات المحلل، و40 ملف عينة، و10 رسائل من الأرشيف المرجعي:
{manifest['rawCount']} إدخالًا. بعد دمج النصوص المكررة واختلاف القيم فقط، بقي {manifest['uniqueCount']} مثالًا في {manifest['groups']} مجموعات صيغة.</p>
<p>التدريب يستخدم فقط {manifest['trainingEligibleUnique']} مثالًا مصدره النصوص داخل الاختبارات والأرشيف. حالات ملفات العينات التي لا تملك مصدرًا مماثلاً تبقى للاختبار فقط، وتشمل ضوابط التعارض والحالات السلبية.
لم ننشئ جملًا تعليمية مصطنعة لهذه الجولة. الأمثلة من المستودع، وبعضها منزوعة البيانات أو معدّلة للاختبار؛ لا ندعي التحقق من أن كل نص التقط مباشرة من هاتف.</p>
<p>ثلاث جولات مستقلة؛ كل صيغة تنتمي لاختبار جولة واحدة. يبدأ كل نموذج من الأوزان الأصلية، ويتدرب على المصادر المؤهلة من المجموعات الأخرى فقط.
كل نتيجة معروضة لرسالة صادرة عن نموذج لم يتدرب عليها ولا على مجموعة صيغتها. قد تدخل الرسالة في تدريب جولة أخرى؛ هذا تقييم متقاطع، وليس نموذجًا نهائيًا واحدًا.</p>
<div class='table'><table><thead><tr><th>الجولة</th><th>رسائل التدريب</th><th>رسائل الاختبار</th><th>صيغ التدريب</th><th>صيغ الاختبار</th><th>أنواع غائبة من التدريب</th></tr></thead><tbody>{folds}</tbody></table></div>
<p>إعدادات التدريب مثبتة قبل الاطلاع على نتائج هذه الجولة: 32 خطوة، حجم دفعة 8، طول 128 رمزًا، ومعدل تعلم 0.00002.
لا يوجد بحث عن الإعدادات أو معايرة على الاختبار. استخدمنا الاختيار الأول مباشرة بحد 0.0 لأغراض قياس التصنيف فقط؛ هذا ليس حد اعتماد مالي.</p>
{''.join(sections)}
<h2>قراءة النتائج وحدودها</h2><p>دقة النوع تحسب كل رسالة مرة واحدة. متوسط دقة الصيغ يعطي كل مجموعة صيغة وزنًا متساويًا، حتى لا تطغى صيغ كثيرة التكرار.
بعض الأنواع لها صيغة واحدة؛ عندما تحجز للاختبار لا يجد النموذج أي مثال تدريبي من هذا النوع. نبقيها في النتيجة الأساسية، ونعرض تشخيص الأنواع المغطاة منفصلًا.</p>
<p>قياسات السرعة على كمبيوتر ويندوز بعد الإحماء، لا على الجوال. القواعد تنفذ المحلل الكامل في JVM، بينما SetFit ينفذ التصنيف في PyTorch على CPU بأربعة خيوط.
زمن الجمع تقديري. لا تقارن نسب هذه المجموعة مباشرة بنسب التجربة السابقة لاختلاف العينات وطريقة الفصل.</p>
<p>هذه أمثلة معروفة من تطوير مصروف ومصدرها بنك الجزيرة أساسًا؛ لا تثبت القدرة على بنك جديد. التسميات من مراجعة النصوص وتوقعات الاختبارات، وليست من تخمينات محلل القواعد.
تجميع الصيغ يدوي محافظ مع وصل النصوص المتشابهة بنسبة 80% أو أكثر، وفحص إضافي للتكرار بين التدريب والاختبار؛ لا يعد إثباتًا رياضيًا لاستقلال كل صيغة دلاليًا.</p>
<h2>التحسن والأخطاء المهمة</h2><p>صحح الجمع نوع {len(improved)} حالات مصدرية كانت غير معروفة للقواعد: صيغة تسديد بطاقة، وحوالة واردة بصيغة جديدة، وشراء إنجليزي مضغوط.
لكن النموذج اقترح حوالة واردة في {len(reversed_transfer)} حوالات صادرة. لذلك لا ينبغي استخدام النوع المقترح لتغيير اتجاه مالي تلقائيًا.</p>
<p>على الحالات الـ22 الإضافية انخفضت دقة الجمع من 95.5% للقواعد إلى 90.9%؛ اقتراحه لنوع رسالة غير معروفة كان خاطئًا.
عدد الرسائل التي تجاوزت حد 128 رمزًا: {sum(r['setfit']['inputTokenCount'] > 128 for r in data['rows'])}.
التحسن على 48 مثالًا لا يكفي لاعتماد إنتاجي، ولا يمكن مقارنة النسب مباشرة بتجربة الأمثلة التعليمية السابقة لأن البيانات وطريقة الاختبار تغيرتا.</p>
<h2>حدود التنفيذ</h2><p>التجربة داخل أدوات المختبر فقط. لم نغير المحلل الأساسي أو قاعدة بيانات مصروف، ولم نفعل SetFit في التطبيق.
اقتراحات المسار المشترك لا ترفع حالة المراجعة إلى عملية ناجحة، ولا تتجاوز تعارضًا صريحًا. جميع نصوص الرسائل والنماذج المدربة محلية، والتقرير لا يعرض نصوص SMS.</p>
<details><summary>التصنيف لكل عينة دون نص الرسالة</summary><div class='table'><table><thead><tr><th>العينة</th><th>جولة الاختبار</th><th>المتوقع</th><th>القواعد</th><th>SetFit</th><th>الجمع</th></tr></thead><tbody>{detail}</tbody></table></div></details></html>"""
    Path(output).write_text(document, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--report", required=True)
    args = parser.parse_args()
    data = aggregate(args.directory)
    write_json(args.output, data)
    report(data, args.report)
    print({g: {m: (v["correct"], v["total"], round(v["accuracyPct"], 1)) for m, v in modes.items()} for g, modes in data["groups"].items()})


if __name__ == "__main__":
    main()
