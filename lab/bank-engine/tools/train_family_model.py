#!/usr/bin/env python3
"""Reproducible local NB training. Features come from the Kotlin engine, never a second tokenizer."""
import argparse, collections, hashlib, json, math, pathlib, subprocess, os
ROOT=pathlib.Path(__file__).resolve().parents[3]
OUT=ROOT/'lab/beta/src/replica/assets/ml/family-seed.json'
REPORT=ROOT/'lab/beta/ML_SEED_EVALUATION.json'
FINANCIAL={'PURCHASE','TRANSFER_IN','TRANSFER_OUT','CARD_PAYMENT','BILL_PAYMENT','FINANCING_INSTALLMENT','WITHDRAWAL','REFUND','FEE'}
def canonical(value): return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'))
def fit(rows):
    vocabulary=set(); counts=collections.defaultdict(collections.Counter); documents=collections.Counter()
    for row in rows:
        documents[row['family']]+=1;counts[row['family']].update(row['features']);vocabulary.update(row['features'])
    # Preserve frequent features, stable lexical tie-break, bounded runtime model size.
    total=collections.Counter()
    for row in counts.values(): total.update(row)
    vocabulary=set(sorted(vocabulary,key=lambda x:(-total[x],x))[:4096])
    counts={family:{k:v for k,v in row.items() if k in vocabulary} for family,row in counts.items()}
    return sorted(vocabulary),dict(documents),counts

def predict(row,vocabulary,documents,counts):
    known={k:v for k,v in row['features'].items() if k in vocabulary}
    scores={family:math.log(docs/sum(documents.values()))+sum(v*math.log((counts[family].get(k,0)+1)/(sum(counts[family].values())+len(vocabulary))) for k,v in known.items()) for family,docs in documents.items()}
    maximum=max(scores.values());probs={k:math.exp(v-maximum) for k,v in scores.items()};total=sum(probs.values());ranked=sorted(probs,key=lambda f:-probs[f])
    return dict(family=ranked[0],score=probs[ranked[0]]/total,margin=(probs[ranked[0]]-probs[ranked[1]])/total,coverage=len(known)/max(1,len(row['features'])))

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');parser.add_argument('--skip-export',action='store_true');args=parser.parse_args()
    if not args.skip_export:
        env=os.environ.copy()
        subprocess.run(['./gradlew',':lab:bank-engine:test','--tests','*FamilyFeatureExportTest','--no-daemon','-Dorg.gradle.jvmargs=-Xmx1536m','-Pkotlin.compiler.execution.strategy=in-process','--max-workers=1','--no-parallel','--console=plain'],cwd=ROOT,env=env,check=True,stdout=subprocess.DEVNULL)
    exported=json.loads((ROOT/'lab/bank-engine/build/model-training/features.json').read_text())
    groups={}
    for row in exported['rows']:
        key=(row['family'],row['template']);groups.setdefault(key,row)
    by_family=collections.defaultdict(list)
    for row in groups.values():by_family[row['family']].append(row)
    partitions={'train':[],'validation':[],'test':[]}
    for family,rows in sorted(by_family.items()):
        rows.sort(key=lambda r:hashlib.sha256(('masroof-family-v1:'+r['template']).encode()).hexdigest())
        if len(rows)>=3:
            partitions['validation'].append(rows[-2]);partitions['test'].append(rows[-1]);partitions['train'].extend(rows[:-2])
        else:partitions['train'].extend(rows)
    vocabulary,documents,counts=fit(partitions['train']);vocab=set(vocabulary)
    evaluated=[]
    for split in ('validation','test'):
        for row in partitions[split]:evaluated.append(dict(id=row['id'],partition=split,expected=row['family'],features=row['features'],**predict(row,vocab,documents,counts)))
    enabled=[]
    for family in sorted(FINANCIAL):
        positives=[r for r in evaluated if r['expected']==family]
        if {r['partition'] for r in positives}=={'validation','test'} and all(r['family']==family for r in positives) and not any(r['family']==family and r['expected']!=family for r in evaluated):enabled.append(family)
    payload=dict(schema=1,featuresVersion=exported['featuresVersion'],bankId=exported['bankId'],vocabulary=vocabulary,documents=documents,counts=counts,enabledFamilies=enabled,
                 policy=dict(scoreThreshold=0.98,marginThreshold=0.2,coverageThreshold=0.6),
                 training=dict(sourceRows=len(exported['rows']),uniqueTemplates=len(groups),trainGroups=len(partitions['train']),validationGroups=len(partitions['validation']),testGroups=len(partitions['test']),trainIds=sorted(r['id'] for r in partitions['train']),sourceSha256=hashlib.sha256(canonical(exported).encode()).hexdigest()),validation=evaluated)
    payload['version']='family-nb-v1-'+hashlib.sha256(canonical(payload).encode()).hexdigest()[:16]
    report=dict(version=payload['version'],training=payload['training'],enabledFamilies=enabled,heldOutCases=[{k:v for k,v in r.items() if k!='features'} for r in evaluated],note='Template groups are disjoint; repeated bodies/number-only variants are not independent evaluation examples. Small initial corpus; no universal accuracy claim.')
    text=json.dumps(payload,ensure_ascii=False,sort_keys=True,indent=2)+'\n';report_text=json.dumps(report,ensure_ascii=False,sort_keys=True,indent=2)+'\n'
    if args.check:
        if not OUT.exists() or OUT.read_text()!=text or REPORT.read_text()!=report_text:raise SystemExit('Seed model drift. Train and review the new artifact.')
    else:
        OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_text(text);REPORT.write_text(report_text)
    print(json.dumps({'version':payload['version'],'enabledFamilies':enabled,**{k:payload['training'][k] for k in ['sourceRows','uniqueTemplates','trainGroups','validationGroups','testGroups']}},ensure_ascii=False))
if __name__=='__main__':main()
