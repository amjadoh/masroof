#!/usr/bin/env python3
"""Reproducible NB fit. Kotlin exports the exact runtime cleansing/features; no Python tokenizer."""
import argparse
import collections
import hashlib
import json
import math
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODULE = ROOT / 'ml-assist'
OUT = MODULE / 'src/main/resources/ml/family-seed.json'
REPORT = MODULE / 'ML_SEED_EVALUATION.json'
EXPORT = ROOT / 'app/build/model-training/features.json'
SPLITS = MODULE / 'tools/corpus-splits.json'
APPROVED_FAMILIES = {'PURCHASE', 'TRANSFER_IN', 'TRANSFER_OUT', 'CARD_PAYMENT', 'BILL_PAYMENT',
                     'FINANCING_INSTALLMENT', 'WITHDRAWAL', 'REFUND', 'FEE'}
MIN_ACCEPTED_VALIDATION_TEMPLATES = 8
POLICY = dict(scoreThreshold=0.98, marginThreshold=0.2, coverageThreshold=0.6)


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def freeze_splits(exported, existing):
    """Assign cleaned template groups before training, without consulting labels."""
    groups = collections.defaultdict(list)
    for row in exported['rows']:
        groups[row['template']].append(row['id'])
    partitions = {}
    priority = {'train': 0, 'validation': 1, 'test': 2}
    for template, identities in groups.items():
        prior = [existing[identity] for identity in identities if identity in existing]
        bucket = int(hashlib.sha256(('family-holdout-template-v1|' + template).encode()).hexdigest()[:8], 16) % 10
        split = max(prior, key=priority.get) if prior else 'train' if bucket < 6 else 'validation' if bucket < 8 else 'test'
        partitions.update({identity: split for identity in identities})
    for row in exported['rejected']:
        partitions[row['id']] = existing.get(row['id'], 'train')
    return dict(version='template-group-v1', partitions=partitions, provisional=False)


def partition_rows(rows, frozen):
    """Group before labels/split: contradictory labels cannot disguise template leakage."""
    by_template = collections.defaultdict(list)
    ids = set()
    for row in rows:
        if row['id'] in ids or frozen.get(row['id']) not in {'train', 'validation', 'test'}:
            raise ValueError('Duplicate source id or missing frozen partition')
        ids.add(row['id'])
        by_template[row['template']].append(row)
    partitions = {'train': [], 'validation': [], 'test': []}
    conflicts = []
    priority = {'train': 0, 'validation': 1, 'test': 2}
    for template, group in sorted(by_template.items()):
        if len({r['family'] for r in group}) != 1:
            conflicts.append(dict(template=template, sourceIds=sorted(r['id'] for r in group)))
            continue
        if len({canonical(r['features']) for r in group}) != 1:
            raise ValueError('Same cleaned template produced inconsistent features')
        split = max((frozen[r['id']] for r in group), key=priority.get)
        chosen = min(group, key=lambda r: (frozen[r['id']] != split, r['id']))
        partitions[split].append(chosen)
    return partitions, conflicts


def fit(rows):
    counts = collections.defaultdict(collections.Counter)
    documents = collections.Counter()
    for row in rows:
        documents[row['family']] += 1
        counts[row['family']].update(row['features'])
    if not documents:
        raise ValueError('No clean training examples')
    total = sum(counts.values(), collections.Counter())
    vocabulary = sorted(sorted(total, key=lambda k: (-total[k], k))[:4096])
    return vocabulary, dict(documents), {
        family: {k: v for k, v in terms.items() if k in vocabulary} for family, terms in counts.items()
    }


def predict(row, vocabulary, documents, counts):
    known = {k: v for k, v in row['features'].items() if k in vocabulary}
    if not known:
        return dict(family=None, score=0.0, margin=0.0, coverage=0.0)
    scores = {
        family: math.log(docs / sum(documents.values())) + sum(
            v * math.log((counts[family].get(k, 0) + 1) / (sum(counts[family].values()) + len(vocabulary)))
            for k, v in known.items())
        for family, docs in documents.items()
    }
    maximum = max(scores.values())
    probs = {k: math.exp(v - maximum) for k, v in scores.items()}
    total = sum(probs.values())
    ranked = sorted(probs, key=lambda f: (-probs[f], f))
    return dict(family=ranked[0], score=probs[ranked[0]] / total,
                margin=(probs[ranked[0]] - (probs[ranked[1]] if len(ranked) > 1 else 0)) / total,
                coverage=len(known) / max(1, len(row['features'])))


def accepts(result, enabled):
    return (result['family'] in enabled and result['score'] >= POLICY['scoreThreshold']
            and result['margin'] >= POLICY['marginThreshold'] and result['coverage'] >= POLICY['coverageThreshold'])


def train(exported, frozen):
    partitions, conflicts = partition_rows(exported['rows'], frozen)
    vocabulary, documents, counts = fit(partitions['train'])
    evaluated = [dict(id=row['id'], template=row['template'], partition=split,
                      expected=row['family'], features=row['features'],
                      runtimeEligible=row.get('runtimeEligible', False),
                      expectedPassesValidation=row.get('expectedPassesValidation', False),
                      candidateOutcomes=row.get('candidateOutcomes', {}),
                      **predict(row, set(vocabulary), documents, counts))
                 for split in ('validation', 'test') for row in partitions[split]]
    # Test results report performance; they NEVER select a family, threshold, or vocabulary.
    validation = [r for r in evaluated if r['partition'] == 'validation']
    # Evaluate policy-accepted suggestions on naturally reached fallback cases only.
    def accepted_for_family(row, family):
        return row['runtimeEligible'] and accepts(row, {family})
    enabled = sorted(f for f in APPROVED_FAMILIES
                     if sum(accepted_for_family(r, f) and r['expected'] == f and r['expectedPassesValidation'] for r in validation)
                     >= MIN_ACCEPTED_VALIDATION_TEMPLATES
                     and not any(accepted_for_family(r, f) and r['expected'] != f for r in validation))
    training = dict(sourceRows=len(exported['rows']) + len(exported['rejected']),
                    cleanRows=len(exported['rows']), rejected=exported['rejected'], conflicts=conflicts,
                    uniqueTemplates=sum(map(len, partitions.values())),
                    trainGroups=len(partitions['train']), validationGroups=len(partitions['validation']),
                    testGroups=len(partitions['test']), trainIds=sorted(r['id'] for r in partitions['train']),
                    sourceSha256=hashlib.sha256(canonical(exported).encode()).hexdigest(), splitVersion='family-runtime-holdout-v2')
    payload = dict(schema=2, cleaningVersion=exported['cleaningVersion'], featuresVersion=exported['featuresVersion'],
                   bankId=exported['bankId'], vocabulary=vocabulary, documents=documents, counts=counts,
                   enabledFamilies=enabled, policy=POLICY, evaluationSource='ML_SEED_EVALUATION.json', training=training,
                   validation=[dict(expected=r['expected'], features=r['features'], template=r['template'],
                                    runtimeEligible=r['runtimeEligible'], passesValidation=r['expectedPassesValidation']) for r in validation])
    payload['version'] = 'family-nb-v2-' + hashlib.sha256(canonical(payload).encode()).hexdigest()[:16]
    report = dict(version=payload['version'], cleaningVersion=exported['cleaningVersion'],
                  featuresVersion=exported['featuresVersion'], training=training, enabledFamilies=enabled,
                  heldOutCases=[dict(**{k: v for k, v in r.items() if k != 'features'},
                                     accepted=r['runtimeEligible'] and accepts(r, enabled),
                                     validationAccepted=r['runtimeEligible'] and accepts(r, enabled) and r['candidateOutcomes'].get(r['family'], {}).get('validationAccepted', False),
                                     assemblyAccepted=r['runtimeEligible'] and accepts(r, enabled) and r['candidateOutcomes'].get(r['family'], {}).get('assembly') == 'Assembled') for r in evaluated],
                  note='Frozen template-disjoint holdout. Test never selects model settings. '
                       'Small initial corpus; NB scores are not calibrated probabilities. No universal accuracy claim.')
    report['cleaningAudit'] = dict(
        rejectedByReason=dict(collections.Counter(r['reason'] for r in exported['rejected'])),
        rejectedByFamily=dict(collections.Counter(r.get('family', 'UNKNOWN') for r in exported['rejected'])))
    report['runtimeEvaluation'] = dict(
        validationEligible=sum(r['runtimeEligible'] for r in validation),
        testEligible=sum(r['runtimeEligible'] for r in evaluated if r['partition'] == 'test'),
        validationAccepted=sum(r['validationAccepted'] for r in report['heldOutCases']),
        assemblyAccepted=sum(r['assemblyAccepted'] for r in report['heldOutCases']),
        minimumAcceptedValidationTemplates=MIN_ACCEPTED_VALIDATION_TEMPLATES,
        acceptedSuggestions=sum(r['accepted'] for r in report['heldOutCases']),
        wrongAcceptedSuggestions=sum(r['accepted'] and r['family'] != r['expected'] for r in report['heldOutCases']))
    if any(r['partition'] == 'test' and r['accepted']
           and r['family'] != r['expected'] for r in report['heldOutCases']):
        raise ValueError('Unsafe final runtime holdout: accepted wrong family')
    for family in enabled:
        accepted_test = [r for r in report['heldOutCases'] if r['partition'] == 'test' and
                         r['accepted'] and r['family'] == family and r['expectedPassesValidation']]
        if len(accepted_test) < MIN_ACCEPTED_VALIDATION_TEMPLATES:
            raise ValueError('Insufficient final runtime holdout for enabled family')
    return payload, report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--skip-export', action='store_true')
    parser.add_argument('--export', type=pathlib.Path, default=EXPORT)
    parser.add_argument('--splits', type=pathlib.Path, default=SPLITS)
    parser.add_argument('--output', type=pathlib.Path, default=OUT)
    parser.add_argument('--report', type=pathlib.Path, default=REPORT)
    parser.add_argument('--freeze-splits', type=pathlib.Path, help='Freeze cleaned template groups before training')
    args = parser.parse_args()
    if not args.skip_export:
        subprocess.run(['./gradlew', ':app:testDebugUnitTest', '--tests', '*FamilyFeatureExportTest',
                        '--no-daemon', '-Dorg.gradle.jvmargs=-Xmx1536m',
                        '-Pkotlin.compiler.execution.strategy=in-process', '--max-workers=1',
                        '--no-parallel', '--console=plain'], cwd=ROOT, check=True)
    exported = json.loads(args.export.read_text())
    split_manifest = json.loads(args.splits.read_text())
    if args.freeze_splits:
        existing = {} if split_manifest.get('provisional') else split_manifest['partitions']
        frozen = freeze_splits(exported, existing)
        args.freeze_splits.write_text(json.dumps(frozen, sort_keys=True, indent=2) + '\n')
        print('Cleaned template partitions frozen; no model was trained.')
        return
    if split_manifest.get('provisional'):
        raise ValueError('Freeze cleaned template groups before training private inputs')
    payload, report = train(exported, split_manifest['partitions'])
    outputs = ((args.output, payload), (args.report, report))
    for path, value in outputs:
        text = json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n'
        if args.check:
            if not path.exists() or path.read_text() != text:
                raise SystemExit('Clean model drift. Regenerate and review the artifact.')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
    print(json.dumps(dict(version=payload['version'], enabledFamilies=payload['enabledFamilies'],
                          **{key:report['training'][key] for key in ('sourceRows','cleanRows','uniqueTemplates','trainGroups','validationGroups','testGroups')}), ensure_ascii=False))


if __name__ == '__main__':
    main()
