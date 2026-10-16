"""Read-only source discovery and template grouping for bank-shaped SMS test examples."""
import argparse
import hashlib
import json
import re
import textwrap
from collections import Counter, defaultdict
from difflib import SequenceMatcher
from pathlib import Path
from context_benchmark import read_json, write_json
from setfit_benchmark import normalized

ROOT = Path(__file__).resolve().parents[2]


def inventory():
    rows = []
    for path in sorted((ROOT / "app/src/test/kotlin").rglob("*.kt")):
        source = path.read_text(encoding="utf-8-sig")
        for index, match in enumerate(re.finditer(r'"""(.*?)"""', source, re.S)):
            text = textwrap.dedent(match.group(1)).strip()
            if "$" in text or len(text.splitlines()) < 2:
                continue
            # Reject SQL/JSON/layout assertions; only bank-message shaped literals.
            if re.search(r"\b(?:SELECT|CREATE TABLE|INSERT INTO|UPDATE|ALTER TABLE)\b", text, re.I):
                continue
            if not re.search(r"شراء|حوالة|بطاقة|تمويل|سداد|تسديد|المستفيد|رمز التحقق|كشف حساب|رصيد|purchase|transfer|credit card|withdrawal|statement", text, re.I):
                continue
            functions = list(re.finditer(r"\bfun\s+(`[^`]+`|[\w]+)\s*\(", source[:match.start()]))
            function = functions[-1].group(1) if functions else "file"
            next_function = re.search(r"\bfun\s+", source[match.end():])
            tail = source[match.end():match.end() + next_function.start()] if next_function else source[match.end():]
            hints = sorted(set(re.findall(r"MessageFamily\.([A-Z_]+)", tail)))
            relative = path.relative_to(ROOT).as_posix()
            rows.append({"id": f"{path.stem}:{index}", "text": text, "source": relative,
                         "line": source[:match.start()].count("\n") + 1, "test": function,
                         "assertionHints": hints, "sha256": hashlib.sha256(text.encode()).hexdigest(),
                         "provenance": "bank-shaped literal in existing application test; capture provenance not independently verified"})
    return rows


# Human review of the test literals and their relevant assertions, not parser predictions.
REGRESSION_LABELS = ["PURCHASE", "PURCHASE", "TRANSFER_OUT", "TRANSFER_OUT", "TRANSFER_IN", "TRANSFER_IN",
    "TRANSFER_IN", "PURCHASE", "BILL_PAYMENT", "WITHDRAWAL", "PURCHASE", "PURCHASE", "CARD_PAYMENT",
    "CARD_PAYMENT", "OTP", "BALANCE_NOTICE", "BILL_PAYMENT", "TRANSFER_IN", "PURCHASE", "REFUND", "REFUND",
    "NON_FINANCIAL", "NON_FINANCIAL", "OTP", "NON_FINANCIAL", "OTP", "PURCHASE", "TRANSFER_IN", "TRANSFER_OUT", "PURCHASE"]
HEURISTIC_LABELS = ["PURCHASE", "PURCHASE", "TRANSFER_IN", "PURCHASE", "PURCHASE", "PURCHASE", "PURCHASE"]
FACT_LABELS = {0: "PURCHASE", 1: "PURCHASE", 2: "PURCHASE", 3: "BALANCE_NOTICE", 4: "PURCHASE", 5: "PURCHASE",
               7: "FINANCING_INSTALLMENT", 8: "PURCHASE", 9: "TRANSFER_IN", 10: "NON_FINANCIAL"}
REFERENCE_LABELS = ["TRANSFER_OUT", "TRANSFER_IN", "TRANSFER_IN", "PURCHASE", "PURCHASE", "TRANSFER_OUT",
                    "PURCHASE", "TRANSFER_OUT", "TRANSFER_IN", "FINANCING_INSTALLMENT"]


def canonical(text):
    text = "\n".join(normalized(line).replace("ـ", "") for line in text.splitlines())
    # Preserve field names/financial wording; discard names, locations and reference values.
    for label in ("لدى", "لدي", "اسم المرسل", "اسم المستفيد", "الاسم المختصر", "المفوتر", "البنك المرسل", "مكان السحب", "رقم المعاملة", "from", "at", "for"):
        key = normalized(label)
        text = re.sub(r"(?<!\w)" + re.escape(key) + r"\s*[:：]\s*[^\n]+", key + ": <value>", text)
    text = re.sub(r"\[[^\]]+\]", "[bank]", text)
    return text


def semantic_group(row):
    text = normalized(row["text"]).replace("ـ", "")
    label = row["label"]
    if label == "OTP" or "كلمة مرور صالحة لمرة واحدة" in text:
        return "security_ar" if re.search(r"[\u0600-\u06ff]", text) else "security_en"
    if label == "PURCHASE" or (label == "UNKNOWN" and text.startswith("شراء")):
        if not re.search(r"[\u0600-\u06ff]", text):
            return "purchase_en_online" if re.search("online|internet", text) else "purchase_en_pos"
        return "purchase_ar_online" if "انترنت" in text else "purchase_ar_pos"
    if label == "TRANSFER_IN":
        return "transfer_in_deposited" if "اودعت" in text else "transfer_in_labeled"
    if label == "TRANSFER_OUT" or (label == "UNKNOWN" and "حوالة صادرة" in text):
        return "transfer_out_external" if "خصمت من حساب" in text or "رسوم حوالة" in text else "transfer_out_internal"
    if label == "CARD_PAYMENT":
        return "card_repayment_settlement" if "تسديد" in text else "card_repayment_title"
    if label == "REFUND":
        return "refund_en" if "refund" in text else "refund_ar"
    if label == "NON_FINANCIAL":
        return "notice_statement" if "كشف" in text or "statement" in text else "notice_beneficiary" if "مستفيد" in text else "notice_login"
    return label.lower()


def collect():
    rows = []
    for row in inventory():
        stem, index = row["id"].split(":")
        index = int(index)
        if stem == "AlJaziraParserRegressionTest":
            label = REGRESSION_LABELS[index]
        elif stem == "CreditCardMessageHeuristicsTest":
            label = HEURISTIC_LABELS[index]
        elif stem == "ParsedEventDetailsFactsCharacterizationTest":
            label = FACT_LABELS[index]
        else:
            continue
        if len(row["assertionHints"]) == 1 and label != row["assertionHints"][0]:
            raise ValueError("Source assertion changed; re-review label for " + row["id"])
        rows.append({**row, "id": "inline:" + row["id"], "label": label, "labelBasis": "reviewed inline SMS and corresponding test assertion/context"})
    root = ROOT / "app/src/test/resources/testdata/bank_aljazira"
    for path in sorted(root.rglob("*.json")):
        if path.parent.name == "reference":
            continue
        fixture = read_json(path)
        rows.append({"id": "fixture:" + fixture["id"], "text": fixture["body"], "label": fixture["expected"]["messageFamily"],
                     "source": path.relative_to(ROOT).as_posix(), "labelBasis": "existing fixture expectation",
                     "provenance": "bank-shaped fixture; includes constructed collision/negative controls"})
    path = root / "reference/jazira_sms_bodies.json"
    for index, row in enumerate(read_json(path)["messages"]):
        rows.append({"id": "reference:" + row["id"], "text": row["body"], "label": REFERENCE_LABELS[index],
                     "source": path.relative_to(ROOT).as_posix(), "labelBasis": "reviewed body and reference description; legacy parser labels not used",
                     "provenance": "legacy SMS reference corpus with anonymized values"})
    # Prefer an archived source SMS over a fixture when the bodies are value variants.
    priority = {"inline": 0, "reference": 1, "fixture": 2}
    return sorted(rows, key=lambda row: priority[row["id"].split(":")[0]])


def grouped(rows):
    # Collapse value-only variants. Do not inflate test counts with repeated messages.
    unique = {}
    for row in rows:
        key = canonical(row["text"])
        if key in unique:
            if unique[key]["label"] != row["label"]:
                raise ValueError("Conflicting labels for a value-normalized message: " + row["id"])
            unique[key]["aliases"].append({k: row[k] for k in ("id", "source", "labelBasis", "provenance")})
        else:
            unique[key] = {**row, "aliases": [], "fingerprint": hashlib.sha256(key.encode()).hexdigest()}
    rows = list(unique.values())
    parent = list(range(len(rows)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    def union(i, j):
        a, b = find(i), find(j)
        if a != b:
            parent[b] = a
    keys = [semantic_group(r) for r in rows]
    for i, a in enumerate(rows):
        for j in range(i):
            b = rows[j]
            # Transitive grouping also joins similar headers/layouts with different labels.
            near = max(SequenceMatcher(None, normalized(a["text"]), normalized(b["text"]), autojunk=False).ratio(),
                       SequenceMatcher(None, canonical(a["text"]), canonical(b["text"]), autojunk=False).ratio())
            if keys[i] == keys[j] or near >= .80:
                union(i, j)
    groups = defaultdict(list)
    for i, row in enumerate(rows):
        groups[find(i)].append(row)
    ordered = sorted(groups.values(), key=lambda group: (-len(group), min(r["id"] for r in group)))
    fold_sizes = [0, 0, 0]
    fold_labels = [Counter(), Counter(), Counter()]
    for group in ordered:
        counts = Counter(r["label"] for r in group)
        # Deterministic balancing by sample count and repeated labels, no model outcomes.
        fold = min(range(3), key=lambda f: (fold_sizes[f] + sum(fold_labels[f][k] for k in counts), fold_sizes[f], f))
        identifier = "template-" + hashlib.sha256("|".join(sorted(r["fingerprint"] for r in group)).encode()).hexdigest()[:12]
        for row in group:
            row["group"] = identifier
            row["fold"] = fold
        fold_sizes[fold] += len(group)
        fold_labels[fold].update(counts)
    return rows


def prepare(output):
    output = Path(output)
    raw = collect()
    rows = grouped(raw)
    for row in rows:
        # Fixture-only cases, including injected collisions, are evaluated but never trained.
        row["trainingEligible"] = training_eligible(row)
    manifest = {"sourceCounts": dict(Counter(r["id"].split(":")[0] for r in raw)), "rawCount": len(raw),
                "uniqueCount": len(rows), "groups": len({r["group"] for r in rows}),
                "trainingEligibleUnique": sum(r["trainingEligible"] for r in rows),
                "labelCounts": dict(Counter(r["label"] for r in rows)), "nearDuplicateThreshold": .80,
                "provenance": "Existing bank-message test literals, fixtures and legacy reference SMS. Capture authenticity is not independently verified; constructed safety cases are tagged.",
                "stage": "message family classification only; no independent direction prediction or entity extraction",
                "rows": [{k: v for k, v in r.items() if k not in {"text", "assertionHints"}} for r in rows]}
    write_json(output / "manifest.json", manifest)
    cases = []
    for row in rows:
        cases.append({"id": row["id"], "dataset": "bank_sms", "sender": "AlJazira", "body": row["text"],
                      "receivedAt": "2026-10-14T12:00:00Z", "banks": [{"id": "BANK_ALJAZIRA", "name": "Source test bank", "senders": ["AlJazira"]}],
                      "expected": {"status": "PARTIAL", "facts": {"family": row["label"]}}})
    write_json(output / "all-cases.json", cases)
    for fold in range(3):
        train = [r for r in rows if r["fold"] != fold and r["trainingEligible"]]
        test = [r for r in rows if r["fold"] == fold]
        assert {r["group"] for r in train}.isdisjoint(r["group"] for r in test)
        # No inner validation/tuning: frozen previous training settings and raw top-1 prediction.
        write_json(output / f"fold-{fold}-train.json", {"schema": "grouped_bank_sms_v1",
            "splits": {"train": [{"id": r["id"], "text": r["text"], "label": r["label"], "group": r["group"]} for r in train], "validation": []}})
        test_ids = {r["id"] for r in test}
        write_json(output / f"fold-{fold}-test.json", [c for c in cases if c["id"] in test_ids])
        manifest.setdefault("folds", []).append({"fold": fold, "train": len(train), "test": len(test),
            "trainGroups": len({r["group"] for r in train}), "testGroups": len({r["group"] for r in test}),
            "unseenTestClasses": sorted({r["label"] for r in test} - {r["label"] for r in train})})
    write_json(output / "manifest.json", manifest)
    print(json.dumps({k: v for k, v in manifest.items() if k != "rows"}, indent=2))


def training_eligible(row):
    return any(r["id"].startswith(("inline:", "reference:")) for r in [row] + row.get("aliases", []))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--prepare", action="store_true")
    args = parser.parse_args()
    if args.prepare:
        prepare(args.output)
        return
    rows = inventory()
    write_json(args.output, rows)
    for row in rows:
        print(json.dumps({k: row[k] for k in ("id", "test", "assertionHints")}, ensure_ascii=True))
    print("COUNT", len(rows))


if __name__ == "__main__":
    main()
