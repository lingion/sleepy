import urllib.request, json, os

TOKEN = open('/Users/lingion_k/Desktop/sleepy/.verify/token').read().strip()

print("Recent commits on remote main:")
req = urllib.request.Request(
    "https://api.github.com/repos/lingion/sleepy/commits?sha=main&per_page=6",
    headers={'Authorization': 'token ' + TOKEN},
)
with urllib.request.urlopen(req) as r:
    for c in json.loads(r.read()):
        print("  " + c['sha'][:10] + "  " + c['commit']['message'].split('\n')[0])

print()
print("v1.0.0 release:")
req = urllib.request.Request(
    "https://api.github.com/repos/lingion/sleepy/releases/340314875",
    headers={'Authorization': 'token ' + TOKEN},
)
with urllib.request.urlopen(req) as r:
    rel = json.loads(r.read())
    print("  tag: " + rel['tag_name'])
    print("  body: " + rel['body'][:200] + "...")
    print("  assets: " + str([(a['name'], a['size'], a['browser_download_url']) for a in rel['assets']]))
