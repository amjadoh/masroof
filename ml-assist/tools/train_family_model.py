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
APPROVED_FAMILIES = {'TRANSFER_IN'}  # Separation does not promote any new family.
POLICY = dict(scoreThreshold=0.98, marginThreshold=0.2, coverageThreshold=0.6)


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


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
                      **predict(row, set(vocabulary), documents, counts))
                 for split in ('validation', 'test') for row in partitions[split]]
    # Test results report performance; they NEVER select a family, threshold, or vocabulary.
    validation = [r for r in evaluated if r['partition'] == 'validation']
    enabled = sorted(f for f in APPROVED_FAMILIES
                     if any(r['expected'] == f for r in validation)
                     and all(r['family'] == f for r in validation if r['expected'] == f)
                     and not any(r['family'] == f and r['expected'] != f for r in validation))
    training = dict(sourceRows=len(exported['rows']) + len(exported['rejected']),
                    cleanRows=len(exported['rows']), rejected=exported['rejected'], conflicts=conflicts,
                    uniqueTemplates=sum(map(len, partitions.values())),
                    trainGroups=len(partitions['train']), validationGroups=len(partitions['validation']),
                    testGroups=len(partitions['test']), trainIds=sorted(r['id'] for r in partitions['train']),
                    sourceSha256=hashlib.sha256(canonical(exported).encode()).hexdigest(), splitVersion='family-holdout-v1')
    payload = dict(schema=2, cleaningVersion=exported['cleaningVersion'], featuresVersion=exported['featuresVersion'],
                   bankId=exported['bankId'], vocabulary=vocabulary, documents=documents, counts=counts,
                   enabledFamilies=enabled, policy=POLICY, evaluationSource='ML_SEED_EVALUATION.json', training=training,
                   validation=[dict(expected=r['expected'], features=r['features']) for r in validation])
    payload['version'] = 'family-nb-v2-' + hashlib.sha256(canonical(payload).encode()).hexdigest()[:16]
    report = dict(version=payload['version'], cleaningVersion=exported['cleaningVersion'],
                  featuresVersion=exported['featuresVersion'], training=training, enabledFamilies=enabled,
                  heldOutCases=[dict(**{k: v for k, v in r.items() if k != 'features'},
                                     accepted=accepts(r, enabled)) for r in evaluated],
                  note='Frozen template-disjoint holdout. Test never selects model settings. '
                       'Small initial corpus; NB scores are not calibrated probabilities. No universal accuracy claim.')
    return payload, report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--skip-export', action='store_true')
    args = parser.parse_args()
    if not args.skip_export:
        subprocess.run(['./gradlew', ':app:testDebugUnitTest', '--tests', '*FamilyFeatureExportTest',
                        '--no-daemon', '-Dorg.gradle.jvmargs=-Xmx1536m',
                        '-Pkotlin.compiler.execution.strategy=in-process', '--max-workers=1',
                        '--no-parallel', '--console=plain'], cwd=ROOT, check=True)
    payload, report = train(json.loads(EXPORT.read_text()), json.loads(SPLITS.read_text())['partitions'])
    outputs = ((OUT, payload), (REPORT, report))
    for path, value in outputs:
        text = json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n'
        if args.check:
            if not path.exists() or path.read_text() != text:
                raise SystemExit('Clean model drift. Regenerate and review the artifact.')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
    print(json.dumps(dict(version=payload['version'], enabledFamilies=payload['enabledFamilies'], **report['training']), ensure_ascii=False))


if __name__ == '__main__':
    main()
