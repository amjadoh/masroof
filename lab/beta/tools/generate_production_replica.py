#!/usr/bin/env python3
"""Generate a locked production replica; only reviewed bootstrap overlays may differ."""
import argparse, hashlib, json, pathlib, re, shutil, xml.etree.ElementTree as ET
ROOT = pathlib.Path(__file__).resolve().parents[3]
BETA = ROOT / 'lab/beta'
SOURCE = ROOT / 'app/src/main'
PREFIX = 'lab_replica_'
PACKAGE = 'com.baraa.masroof.lab.replica'
LOCK = BETA / 'PRODUCTION_REPLICA_SOURCES.json'
def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def inventory():
    return {str(p.relative_to(ROOT)): digest(p) for p in sorted([*SOURCE.joinpath('kotlin').rglob('*.kt'), *SOURCE.joinpath('res').rglob('*')]) if p.is_file()}
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
    actual = {'format': 1, 'sourceCommit': '0d86c5d3a209bc31c72bc63cd05775d471310afa', 'files': inventory(), 'excluded': ['kotlin/com/baraa/masroof/MasroofApplication.kt'], 'bootstrapSha256': digest(bootstrap)}
    if args.lock: LOCK.write_text(json.dumps(actual, indent=2, ensure_ascii=False) + '\n')
    if not LOCK.exists() or json.loads(LOCK.read_text()) != actual: raise SystemExit('Production or bootstrap drift. Review and explicitly update the replica lock.')
    if args.check:
        print(f'Verified {len(actual["files"])} sources; production files are read-only.'); return
    out = args.output
    if out.exists(): shutil.rmtree(out)
    resources = resource_names()
    symbols = {(kind, old.replace('.', '_')): new.replace('.', '_') for (kind, old), new in resources.items()}
    for rel in actual['files']:
        path = ROOT / rel; source_rel = str(path.relative_to(SOURCE))
        if source_rel in actual['excluded']: continue
        if path.suffix == '.kt':
            text = path.read_text()
            for op in specs.get(source_rel, []):
                if text.count(op['old']) != op.get('count', 1): raise SystemExit(f'Overlay mismatch {source_rel}: {op["old"][:80]} got {text.count(op["old"])}')
                text = text.replace(op['old'], op['new'])
            text = relocate(text)
            for name in ('R', 'BuildConfig'):
                imp = f'import com.baraa.masroof.lab.beta.{name}'
                if imp not in text: text = re.sub(r'(^package [^\n]+\n)', r'\1\n' + imp + '\n', text, count=1)
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
    print(f'Generated production replica in {out}')
if __name__ == '__main__': main()
