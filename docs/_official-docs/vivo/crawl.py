import json,urllib.request,time,re,os
pages=json.load(open('pages.json'))
def slug(s): return re.sub(r'[^\w-]','_',s)[:40]
done=0
for pid,name,bc in pages:
    fp=f"{pid}_{slug(name)}.json"
    if os.path.exists(fp): done+=1; continue
    try:
        r=urllib.request.urlopen(urllib.request.Request(f"https://dev.vivo.com.cn/webapi/doc/info?id={pid}",headers={"User-Agent":"Mozilla/5.0"}),timeout=15).read()
        open(fp,'wb').write(r); done+=1
    except Exception as e:
        print("ERR",pid,name,e)
    time.sleep(0.15)
print("DONE",done,"/",len(pages))
