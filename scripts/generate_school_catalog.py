#!/usr/bin/env python3
"""Generate reproducible school inventory; factual listing never implies tested import."""
import argparse,copy,hashlib,json,re
from collections import Counter,defaultdict
from pathlib import Path
from catalog_url_safety import audit
from catalog_ownership import apply_ownership
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'scripts/data'
def read(name):
 value=json.loads((DATA/name).read_text())
 findings=audit(value,name)
 if findings:raise ValueError('Unsanitized URL parameters in '+name+': '+str(findings))
 return value
def encoded(data):return json.dumps(data,ensure_ascii=False,indent=2)+chr(10)
def norm(name):return name.strip()
def is_grad(name):return '研究生' in name

def generate():
 renames=read('school_catalog_renames.json'); canonical=lambda name:renames.get(norm(name),{}).get('newName',norm(name))
 baseline=read('school_catalog_baseline.json'); official=read('official_schools.json'); wakeup=read('wakeup_school_sources.json'); pinyin=read('school_catalog_pinyin.json'); admissions=read('school_import_admissions.json')
 rows=copy.deepcopy(baseline); by_name={norm(r['name']):r for r in rows}; source_links=defaultdict(list); official_links=defaultdict(list)
 # Baseline fields are preserved byte-for-value; their pre-existing stable keys stay valid.
 def key(row):return row.get('catalogId') or row['sortKey']+'_'+row['name']
 def make(name,identity,sort_key=None):
  py=pinyin.get(name,'');initial=(sort_key or (py[:1].upper() if py and py[:1].isalpha() else '#'))
  row={'sortKey':initial,'name':name,'url':'','type':None,'status':'grad_pending' if is_grad(name) else 'pending','aliases':[],'sortKeyFull':py,'enableFetch':False,'catalogId':identity}
  rows.append(row);by_name[name]=row;return row
 # School codes identify official rows; exact names are the only automatic cross-source join.
 for src in official:
  name=norm(src['name']); row=by_name.get(name)
  if row is None: row=make(name,src['id'])
  official_links[key(row)].append({'sourceId':src['source_id'],'sourceRow':src.get('source_row'),'sourceSchoolId':src['id'],'match':'exact-name','name':src['name']})
 for src in wakeup['records']:
  name=canonical(src['name']);row=by_name.get(name)
  if row is None: row=make(name,'wakeup:'+hashlib.sha256(('wakeup-school-v1'+chr(0)+name).encode()).hexdigest()[:20],src.get('sortKey'))
  source_links[key(row)].append({'sourceId':src['sourceId'],'sourceRow':src['sourceRow'],'match':'explicit-rename' if name!=norm(src['name']) else 'trimmed-exact-name','sourceName':src['sourceName']})
 # Admissions are explicit, reviewed current public-page evidence. Never supported/verified.
 baseline_names={norm(x['name']) for x in baseline}; accepted=[]
 for decision in admissions:
  name=canonical(decision['name']);row=by_name.get(name)
  if row is None or name in baseline_names:continue
  row.update(url=decision['probe']['finalUrl'],type=decision['mappedType'],status='grad_unverified' if is_grad(name) else 'unverified',enableFetch=False)
  accepted.append({'catalogId':key(row),'name':name,'url':row['url'],'type':row['type'],'sourceId':decision['sourceId'],'sourceRow':decision['sourceRow'],'checkedAt':decision['probe']['checkedAt'],'http':decision['probe']['http'],'title':decision['probe'].get('title'),'fingerprints':decision['probe'].get('fingerprints',[]),'limitation':decision['verification']})
 assert rows[:len(baseline)]==baseline,'Existing school fields changed before ownership review'
 ownership=apply_ownership(rows,read('school_url_evidence.json'),baseline_names,renames)
 decisions={x['catalogId']:x for x in ownership}
 accepted=[dict(x, url=decisions[x['catalogId']]['operationalUrl'],ownershipAction=decisions[x['catalogId']]['action']) for x in accepted if decisions[x['catalogId']]['operationalUrl']]
 rows=rows[:len(baseline)]+sorted(rows[len(baseline):],key=lambda x:(x['sortKey'],x['sortKeyFull'],x['name']))
 ids=[key(x) for x in rows];assert len(ids)==len(set(ids))
 assert len(rows)==len(by_name)
 assert all(x['name'] in by_name for x in official)
 assert all(canonical(x['name']) in by_name for x in wakeup['records'])
 assert all(not x['url'] and x['type'] is None for x in rows if x.get('status') in ('pending','grad_pending'))
 assert all(x.get('status') not in ('supported','grad_supported') for x in rows[len(baseline):])
 sources=[]
 for source_file in ('school_catalog_baseline.json','official_schools.json','wakeup_school_sources.json','school_catalog_pinyin.json','school_import_admissions.json','school_catalog_renames.json','school_url_evidence.json'):
  path=DATA/source_file
  sources.append({'path':str(path.relative_to(ROOT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
 manifest={'generatedAsOf':'2026-10-14','baselineCount':len(baseline),'catalogCount':len(rows),'statusCounts':dict(Counter(x.get('status','supported') for x in rows)),'sourceCounts':{'official':len(official),'wakeupSchoolSourceRows':len(wakeup['records']),'wakeupExcludedTemplateRows':len(wakeup['excludedTemplates'])},'coverage':{'officialMissing':[],'wakeupMissing':[]},'sources':sources,'matchingPolicy':'Exact trimmed full names plus reviewed school_catalog_renames.json; no fuzzy aliases or graduate/campus suffix removal. Historical source rows remain independently traceable.','sourceLinks':{k:official_links[k]+source_links[k] for k in ids if official_links[k] or source_links[k]}}
 manifest['ownershipAudit']={'evidenceCounts':dict(Counter(x['ownershipStatus'] for x in ownership)), 'actions':dict(Counter(x['action'] for x in ownership)), 'decisions':ownership}
 links=manifest.pop('sourceLinks');return rows,manifest,accepted,links

def main():
 args=argparse.ArgumentParser();args.add_argument('--check',action='store_true');opts=args.parse_args();rows,manifest,accepted,links=generate()
 assert not audit(rows,'generated-catalog'), 'Unsafe operational URL in generated catalog'
 assert all('REDACTED' not in row.get('url','') for row in rows), 'Redacted provenance URL cannot be operational'
 outputs={'app/src/main/assets/schools.json':rows,'app/src/test/resources/jw/schools.json':rows,'docs/catalog/manifest.json':manifest,'docs/catalog/import-admissions.json':accepted,'docs/catalog/source-links.json':links}
 for name,value in outputs.items():
  path=ROOT/name;text=encoded(value)
  if opts.check: assert path.read_text()==text,'Stale generated output: '+name
  else:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text)
 print(encoded({k:manifest[k] for k in ('baselineCount','catalogCount','statusCounts','sourceCounts')}))
if __name__=='__main__':main()
