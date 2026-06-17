import urllib.request
import json
import base64

with open('/Users/lingion_k/Desktop/sleepy/.verify/token') as f:
    GH_TOKEN = f.read().strip()

OWNER = "lingion"
REPO = "sleepy"
BRANCH = "main"

FILES = [
    {
        "path": "app/src/main/java/com/lingion/sleepy/data/parser/ScheduleParser.kt",
        "message": "feat: support 开始节数+结束节数 列 + 多区间周数 (2-5,7-9,11-14)",
    },
    {
        "path": "app/src/main/AndroidManifest.xml",
        "message": "fix: re-register DailyNotifyReceiver (got dropped in cleanup)",
    },
]


def get_current_sha(path):
    url = f"https://api.github.com/repos/{OWNER}/{REPO}/contents/{path}?ref={BRANCH}"
    req = urllib.request.Request(url, headers={'Authorization': f'token {GH_TOKEN}'})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())['sha']


def push_file(path, message):
    local = "/Users/lingion_k/Desktop/sleepy/" + path
    with open(local, 'rb') as f:
        content = f.read()
    encoded = base64.b64encode(content).decode()
    sha = get_current_sha(path)
    url = f"https://api.github.com/repos/{OWNER}/{REPO}/contents/{path}"
    data = json.dumps({
        "message": message,
        "content": encoded,
        "sha": sha,
        "branch": BRANCH,
    }).encode()
    req = urllib.request.Request(
        url, data=data,
        headers={
            'Authorization': f'token {GH_TOKEN}',
            'Content-Type': 'application/json',
        },
        method='PUT',
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            result = json.loads(resp.read())
            print(f"  PUSHED: {path}")
            print(f"    commit: {result['commit']['sha'][:10]}")
            print(f"    size: {len(content)} bytes")
    except urllib.error.HTTPError as e:
        print(f"  HTTP {e.code} on {path}: {e.read().decode()[:300]}")


for f in FILES:
    push_file(f['path'], f['message'])
