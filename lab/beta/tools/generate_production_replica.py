#!/usr/bin/env python3
"""Generate a locked replica with reviewed bootstrap and additive ML evidence hooks."""
import argparse, hashlib, json, pathlib, re, shutil, xml.etree.ElementTree as ET
ROOT = pathlib.Path(__file__).resolve().parents[3]
BETA = ROOT / 'lab/beta'
SOURCE = ROOT / 'app/src/main'
PREFIX = 'lab_replica_'
PACKAGE = 'com.baraa.masroof.lab.replica'
LOCK = BETA / 'PRODUCTION_REPLICA_SOURCES.json'
REGRESSION = ROOT / 'app/src/test/kotlin/com/baraa/masroof/bank/aljazira/AlJaziraParserRegressionTest.kt'
def digest(p):
    data = p.read_bytes()
    if p.suffix in ('.kt', '.xml', '.json', '.py'):
        data = data.replace(b'\r\n', b'\n')
    return hashlib.sha256(data).hexdigest()
def inventory():
    return {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted([*SOURCE.joinpath('kotlin').rglob('*.kt'), *SOURCE.joinpath('res').rglob('*')], key=lambda p:p.relative_to(ROOT).parts) if p.is_file()}
def resource_names():
    names = {}
    for p in SOURCE.joinpath('res').rglob('*'):
        if not p.is_file(): continue
        kind = p.parent.name.split('-')[0]
        if kind == 'values':
            for node in ET.parse(p).getroot():
                if node.get('name'): names[(node.get('type', node.tag), node.get('name'))] = PREFIX + node.get('name')
        else: names[(kind, p.name.split('.')[0])] = PREFIX + p.name.split('.')[0]
    return names
def relocate(text):
    text = re.sub(r'com\.baraa\.masroof\.(?!ui\b|lab\b)', PACKAGE + '.', text)
    text = text.replace('package com.baraa.masroof\n', 'package ' + PACKAGE + '\n')
    return text.replace(PACKAGE + '.R', 'com.baraa.masroof.lab.beta.R').replace(PACKAGE + '.BuildConfig', 'com.baraa.masroof.lab.beta.BuildConfig')
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--lock', action='store_true'); parser.add_argument('--check', action='store_true')
    parser.add_argument('--output', type=pathlib.Path, default=BETA / 'build/generated/productionReplica')
    args = parser.parse_args()
    bootstrap = BETA / 'replica-overlays/bootstrap.json'; specs = json.loads(bootstrap.read_text())
    provenance = BETA / 'replica-overlays/provenance.json'
    for path, ops in json.loads(provenance.read_text()).items():
        specs.setdefault(path, []).extend(ops)
    fixes = BETA / 'replica-overlays/reported-fixes.json'
    for path, ops in json.loads(fixes.read_text()).items():
        specs.setdefault(path, []).extend(ops)
    actual = {'format': 1, 'sourceCommit': '0d86c5d3a209bc31c72bc63cd05775d471310afa', 'files': inventory(), 'excluded': ['kotlin/com/baraa/masroof/MasroofApplication.kt'] + sorted(p.relative_to(SOURCE).as_posix() for p in SOURCE.joinpath('kotlin/com/baraa/masroof/bank/aljazira').rglob('*.kt') if p.name != 'AlJaziraBankDetector.kt'), 'bootstrapSha256': digest(bootstrap), 'provenanceSha256': digest(provenance), 'reportedFixesSha256': digest(fixes)}
    actual['regressionSha256'] = digest(REGRESSION)
    if args.lock: LOCK.write_text(json.dumps(actual, indent=2, ensure_ascii=False) + '\n')
    if not LOCK.exists() or json.loads(LOCK.read_text()) != actual: raise SystemExit('Production or bootstrap drift. Review and explicitly update the replica lock.')
    if args.check:
        print(f'Verified {len(actual["files"])} sources; production files are read-only.'); return
    out = args.output
    if out.exists(): shutil.rmtree(out)
    resources = resource_names()
    symbols = {(kind, old.replace('.', '_')): new.replace('.', '_') for (kind, old), new in resources.items()}
    for rel in actual['files']:
        path = ROOT / rel; source_rel = path.relative_to(SOURCE).as_posix()
        if source_rel in actual['excluded']:
            if '/bank/aljazira/' in source_rel:
                text = relocate(path.read_text()).replace(PACKAGE + '.bank.aljazira', 'com.baraa.masroof.lab.reference.bank.aljazira')
                dest = out / 'referenceKotlin' / source_rel.removeprefix('kotlin/')
                dest.parent.mkdir(parents=True,exist_ok=True); dest.write_text(text)
            continue
        if path.suffix == '.kt':
            text = path.read_text()
            for op in specs.get(source_rel, []):
                if text.count(op['old']) != op.get('count', 1): raise SystemExit(f'Overlay mismatch {source_rel}: {op["old"][:80]} got {text.count(op["old"])}')
                text = text.replace(op['old'], op['new'])
            text = relocate(text)
            for name in ('R', 'BuildConfig'):
                imp = f'import com.baraa.masroof.lab.beta.{name}'
                if imp not in text and re.search(r'(?<![.\w])' + name + r'\.', text): text = re.sub(r'(^package [^\n]+\n)', r'\1\n' + imp + '\n', text, count=1)
            text = re.sub(r'(?<!android\.)(?<![\w])R\.([a-z]+)\.([A-Za-z0-9_]+)', lambda m: 'R.'+m[1]+'.'+symbols.get((m[1],m[2]),m[2]), text)
            dest = out / 'kotlin' / source_rel.removeprefix('kotlin/')
        else:
            kind = path.parent.name.split('-')[0]
            dest = out / 'res' / path.parent.name / (path.name if kind == 'values' else PREFIX + path.name)
            if path.suffix != '.xml':
                dest.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(path,dest); continue
            text = path.read_text()
            if kind == 'values':
                text = re.sub(r'(<(?:string|style|color|dimen|bool|integer|plurals|string-array|integer-array|array)\b[^>]*\bname=")([^"]+)(")', lambda m: m[1]+PREFIX+m[2]+m[3],text)
            text = re.sub(r'@([a-z]+)/([\w.]+)',lambda m: '@'+m[1]+'/'+resources.get((m[1],m[2]),m[2]),text)
        dest.parent.mkdir(parents=True,exist_ok=True); dest.write_text(text)
        if source_rel.endswith('/bank/aljazira/AlJaziraBankDetector.kt'):
            ref = out / 'referenceKotlin' / source_rel.removeprefix('kotlin/')
            ref.parent.mkdir(parents=True,exist_ok=True)
            ref.write_text(text.replace(PACKAGE + '.bank.aljazira', 'com.baraa.masroof.lab.reference.bank.aljazira'))
    # Test-only production regressions replay every parse through a comparison probe.
    # Runtime still contains no legacy parsing pipeline.
    regression = relocate(REGRESSION.read_text()).replace(PACKAGE + '.bank.aljazira', 'com.baraa.masroof.lab.reference.bank.aljazira')
    regression = regression.replace('class AlJaziraParserRegressionTest', 'class ProductionRegressionReplayTest')
    regression = regression.replace('private val pipeline = AlJaziraParsingPipeline()', 'private val pipeline = ProductionRegressionProbe()')
    regression = regression.replace('rawSmsId = "regression"', 'rawSmsId = Thread.currentThread().stackTrace.first { it.className.endsWith("ProductionRegressionReplayTest") && it.methodName != "parse" }.methodName')
    dest = out / 'regressionKotlin/com/baraa/masroof/lab/reference/bank/aljazira/ProductionRegressionReplayTest.kt'
    dest.parent.mkdir(parents=True, exist_ok=True); dest.write_text(regression)
    fixtures = ROOT / 'app/src/test/resources/testdata/bank_aljazira'
    for fixture in sorted([*fixtures.rglob('*.json'), *BETA.joinpath('testdata/reported').rglob('*.json'), *BETA.joinpath('testdata/supported').rglob('*.json'), *BETA.joinpath('testdata/device-reported').rglob('*.json')]):
        if 'expected' not in json.loads(fixture.read_text()): continue
        dest = out / 'fixtures/bank-fixtures' / fixture.name
        dest.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(fixture,dest)
    print(f'Generated production replica in {out}')
if __name__ == '__main__': main()
