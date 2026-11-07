// Execute the actual WHUT collector against offline responses, including legacy HTML.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt','utf8');
const script=source.match(/const val WHUT_FETCH_JS = """([\s\S]*?)"""/)[1];
async function execute({legacy=false,fallback=false}={}) {
  const calls=[],results=[];
  const raw=JSON.parse(fs.readFileSync('app/src/test/resources/jw/whut-live/whut_cxxszhxqkb_live.json','utf8'));
  const html='<div id="xqkb"><table class="table-class-even"><tr><td style="x"><a>旧课<span>@A</span><span>第1周(8节)</span></a></td></tr></table></div>';
  const ids=[1,2,3,4,5,8,9,10,11,12,14,15,16];
  const fetch=async(url,options={})=>{
    calls.push({url,options});
    let data={};
    if(url.includes('currentUser')) data={datas:{userId:'offline-test',welcomeInfo:{xnxqdm:'test-term'}}};
    if(url.includes('jcjcx')) {
      if(fallback) throw Error('no periods');
      data={datas:{jcjcx:{rows:ids.map((id,i)=>({DM:String(id),MC:'第'+(i+1)+'节'}))}}};
    }
    if(url.includes('cxxszhxqkb')) data=JSON.parse(JSON.stringify(raw));
    return {ok:true,status:200,json:async()=>data};
  };
  function frame(active) {return {location:{hostname:'jwxt.whut.edu.cn',pathname:'/jwapp/sys/homeapp/index.do'},fetch,frames:[],
    document:{querySelector:selector=>active&&selector==='#xqkb .table-class-even'?{}:null,documentElement:{outerHTML:active?html:'<html>portal</html>'}}};}
  const root=frame(false);
  if(legacy)root.frames=[frame(true)];
  root.__sleepyBridge={onWiseduResult:text=>results.push(JSON.parse(text))};
  vm.runInNewContext(script,{window:root,location:root.location,document:root.document,fetch,URL},{timeout:1000});
  for(let i=0;i<30&&!results.length;i++)await new Promise(r=>setImmediate(r));
  assert.equal(results.length,1);assert.equal(results[0].ok,true);
  return {result:results[0],raw,html,calls};
}
(async()=>{
  const failures=[],fixtures={};
  for(const fallback of [false,true]) {
    try {
      const r=await execute({fallback}),data=JSON.parse(r.result.data);
      assert.deepEqual(data.datas,r.raw.datas,'transport must preserve all raw section DM values');
      assert.equal(data.sleepyWhutSectionMap['14'],11);assert.equal(data.sleepyWhutSectionMap['16'],13);
      fixtures[fallback?'fallback':'dynamic']=data;
      console.log('PASS '+(fallback?'fallback':'dynamic')+' map retains original rows');
    }catch(e){failures.push(e.message);console.error('FAIL '+e.message);}
  }
  try {const r=await execute({legacy:true});assert.equal(r.result.data,r.html);assert.equal(r.calls.length,0);console.log('PASS legacy HTML bypasses JSON collector');}
  catch(e){failures.push(e.message);console.error('FAIL '+e.message);}
  assert.deepEqual(failures,[]);
  const dir='app/src/test/resources/jw/fixtures/whut-collector';
  if(process.argv.includes('--write-fixtures')){
    fs.mkdirSync(dir,{recursive:true});
    for(const [name,data] of Object.entries(fixtures))fs.writeFileSync(dir+'/'+name+'.json',JSON.stringify(data,null,2)+'\n',{flag:'wx'});
  }
  if(process.argv.includes('--check-fixtures'))for(const [name,data] of Object.entries(fixtures))assert.deepEqual(JSON.parse(fs.readFileSync(dir+'/'+name+'.json','utf8')),data);
  console.log('3 offline WHUT collector scenarios passed.');
})().catch(e=>{console.error(e);process.exitCode=1;});
