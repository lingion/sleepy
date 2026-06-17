import urllib.request
import json
import base64
import re

# Read token from file
with open('/Users/lingion_k/Desktop/sleepy/.verify/token', 'r') as f:
    TOKEN = f.read().strip()

def fetch_file(path):
    url = f"https://api.github.com/repos/lingion/sleepy/contents/{path}?ref=main"
    req = urllib.request.Request(url, headers={'Authorization': f'token {TOKEN}'})
    with urllib.request.urlopen(req, timeout=15) as r:
        data = json.loads(r.read())
    return base64.b64decode(data['content']).decode(), data['size'], data['sha']

# 1. ImportScreen — check CSV/HTML
content, size, sha = fetch_file("app/src/main/java/com/lingion/sleepy/ui/screen/imports/ImportScreen.kt")
print("=== ImportScreen.kt ===")
print(f"  size: {size} bytes, sha: {sha[:10]}")
for i, line in enumerate(content.split(chr(10)), 1):
    if 'CSV 文件' in line or 'HTML 表格' in line:
        print(f"  L{i}: {line.strip()}")
print()

# 2. Manifest — check TestImportActivity gone
content, size, sha = fetch_file("app/src/main/AndroidManifest.xml")
print("=== AndroidManifest.xml ===")
print(f"  size: {size} bytes, sha: {sha[:10]}")
count = content.count("TestImportActivity")
print(f"  TestImportActivity 残留: {count} 处  ({'已删除' if count == 0 else '未删除！'})")
print()

# 3. ScheduleParser — check CSV/HTML functions
content, size, sha = fetch_file("app/src/main/java/com/lingion/sleepy/data/parser/ScheduleParser.kt")
print("=== ScheduleParser.kt ===")
print(f"  size: {size} bytes, sha: {sha[:10]}")
for i, line in enumerate(content.split(chr(10)), 1):
    m = re.match(r'\s*private fun (parseCsv|parseHtml|isLikelyCsv|extractHtmlTables|parseCsvRows)\(', line)
    if m:
        print(f"  L{i}: {m.group(0).strip()}")
print()

# 4. Recent commits
print("=== 最近 6 commit on remote main ===")
req = urllib.request.Request(
    "https://api.github.com/repos/lingion/sleepy/commits?sha=main&per_page=6",
    headers={'Authorization': f'token {TOKEN}'},
)
with urllib.request.urlopen(req, timeout=15) as r:
    for c in json.loads(r.read()):
        print(f"  {c['sha'][:10]}  {c['commit']['message'].split(chr(10))[0]}")
