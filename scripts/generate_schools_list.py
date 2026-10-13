#!/usr/bin/env python3
"""Regenerate the human-readable school catalogue from the Android asset."""
from collections import Counter
from datetime import date
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]

def cell(value):
    return str(value).replace('|', '&#124;').replace('\n', ' ')

def main():
    schools = json.loads((ROOT / 'app/src/main/assets/schools.json').read_text())
    counts = Counter(s['type'] for s in schools)
    matcher = (ROOT / 'app/src/main/java/com/lingion/sleepy/util/PinyinMatcher.kt').read_text()
    initial_map = dict(re.findall(r"'(.)' to \"([A-Z])\"", matcher))

    def search_terms(school):
        # Reuse the app's character mapping; no external pinyin dependency.
        initials = ''.join(ch.lower() if ch.isascii() and ch.isalnum() else initial_map.get(ch, '').lower() for ch in school['name'])
        values = [*school.get('aliases', []), school.get('sortKeyFull', ''), initials]
        return ' / '.join(dict.fromkeys(value for value in values if value))
    lines = [
        '# 已收录学校名单', '',
        f'> 由 `app/src/main/assets/schools.json` 单源生成，共 {len(schools)} 个学校条目、{len(counts)} 种协议标识。最后更新：{date.today().isoformat()}。', '',
        '收录不代表每所学校当前的登录流程都已实机验证。可用 Ctrl+F 搜索全名、别名或拼音缩写；教务地址用于核对登录入口。应用内学校目录随版本更新。', '',
        '重新生成：`python3 scripts/generate_schools_list.py`。', ''
    ]
    groups = {}
    for school in schools:
        groups.setdefault(school.get('sortKey', '#'), []).append(school)
    for group in sorted(groups):
        lines += [f'## {cell(group)}', '', '| 学校 | 别名 / 检索词 | 教务登录地址 | 协议 |', '|---|---|---|---|']
        for school in sorted(groups[group], key=lambda s: s.get('sortKeyFull', s['name'])):
            lines.append(f"| {cell(school['name'])} | {cell(search_terms(school))} | {cell(school['url'])} | `{cell(school['type'])}` |")
        lines.append('')
    lines += ['## 协议分布', '', '| 协议 | 学校条目数 |', '|---|---|']
    lines += [f'| `{protocol}` | {count} |' for protocol, count in sorted(counts.items(), key=lambda item: (-item[1], item[0]))]
    lines += ['', '## 你的学校不在这张表里？', '',
        '1. 先尝试全名、别名或拼音缩写检索。',
        '2. 在应用学校选择页的搜索框输入教务登录 URL，尝试协议识别和导入。能否成功取决于学校页面、登录方式和解析器。',
        '3. 若失败，提交[学校适配申请](https://github.com/lingion/sleepy/issues/new?template=school_adaptation.yml)，说明 URL、失败现象和操作步骤。需要采集时参考[适配教程](adapt-kit/README.md)。不要提交账号、密码、验证码或未脱敏个人信息。', '']
    (ROOT / 'docs/schools-list.md').write_text('\n'.join(lines))
    print(f'Generated {len(schools)} school entries and {len(counts)} protocols')

if __name__ == '__main__':
    main()
