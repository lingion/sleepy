#!/usr/bin/env python3
"""Generate a status-aware human-readable catalogue from the Android asset."""
from collections import Counter
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
def cell(value):return str(value or '').replace('|','&#124;').replace(chr(10),' ')
def main():
 schools=json.loads((ROOT/'app/src/main/assets/schools.json').read_text())
 links=json.loads((ROOT/'docs/catalog/source-links.json').read_text())
 manifest=json.loads((ROOT/'docs/catalog/manifest.json').read_text())
 status=Counter(s.get('status','supported') for s in schools)
 supported=status['supported']+status['grad_supported'];unverified=status['unverified']+status['grad_unverified'];pending=status['pending']+status['grad_pending']
 counts=Counter(s['type'] for s in schools if s.get('type'))
 labels={'supported':'已实现配置','grad_supported':'研究生·已实现配置','unverified':'待验证·可尝试','grad_unverified':'研究生·待验证·可尝试','pending':'待适配','grad_pending':'研究生·待适配','legacy':'历史条目'}
 lines=['# 学校目录','',f'> 共 {len(schools)} 个目录条目：已实现配置 {supported}；待验证、需确认后尝试 {unverified}；待适配 {pending}。数据基准：{manifest["generatedAsOf"]}。','',
 '目录收录不等于教务导入支持。已实现配置沿用历史状态，也不保证每校当前登录均已实机验证；待验证条目仅有公开入口和候选协议证据，未登录测试；待适配条目没有可启用的导入配置。','',
 '来源、地域口径、许可说明和准入规则见 [来源说明](catalog/README.md)；精确计数与输入哈希见 [manifest](catalog/manifest.json)，每条来源记录见 [source-links](catalog/source-links.json)，可尝试证据见 [import-admissions](catalog/import-admissions.json)。旧名和高校变体可能形成目录条目，条目数不是独立高校数。','',
 '重建：python3 scripts/generate_school_catalog.py；人读列表：python3 scripts/generate_schools_list.py。','']
 groups={}
 for s in schools:groups.setdefault(s.get('sortKey','#'),[]).append(s)
 for group in sorted(groups):
  lines += ['## '+cell(group),'','| 学校 | 状态 | 别名 / 检索词 | 教务入口 | 协议候选 | 来源 / 目录 ID |','|---|---|---|---|---|---|']
  for s in sorted(groups[group],key=lambda r:r.get('sortKeyFull',r['name'])):
   key=s.get('catalogId') or s['sortKey']+'_'+s['name'];source=links.get(key,[]);source_ids=', '.join(sorted({x['sourceId'] for x in source})) or 'existing-baseline'
   terms=' / '.join(dict.fromkeys([*s.get('aliases',[]),s.get('sortKeyFull','')]))
   lines.append('| '+ ' | '.join(cell(v) for v in [s['name'],labels.get(s.get('status','supported'),'待适配'),terms,s.get('url'),s.get('type'),source_ids+'; '+key])+' |')
  lines.append('')
 lines += ['## 已配置的协议标识分布','','待适配空协议不计入此表；可尝试配置也不等于完成校级验证。','','| 协议 | 已配置条目数 |','|---|---|']
 lines += ['| '+cell(k)+' | '+str(v)+' |' for k,v in sorted(counts.items(),key=lambda x:(-x[1],x[0]))]
 lines += ['','## 申请适配','','先提供公开教务 URL 与失败现象，按 [适配教程](adapt-kit/README.md) 在需要时采集脱敏资料。禁止提交账户、密码、验证码或未脱敏课表。','']
 (ROOT/'docs/schools-list.md').write_text(chr(10).join(lines));print(f'Generated {len(schools)} entries: {supported} existing, {unverified} unverified, {pending} pending')
if __name__=='__main__':main()
