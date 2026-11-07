const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwSchoolApiCapture.kt','utf8');
const template=source.match(/const val SCHOOL_API_FETCH_JS = """([\s\S]*?)"""/)[1];
async function execute({prefix='',fallback=false,failure='',noControl=false,totalSize=1}={}){
  const calls=[],results=[],timers=new Map();let id=0;
  const row={KCMC:'研究方法',RKJS:'张',PKSJDD:'[1-3周 星期三 5-6节]研教楼'};
  const win={location:new URL('https://vpn.jlu.edu.cn'+prefix+'/gsapp/sys/wdkbapp/index.do'),frames:[],
    document:{querySelector:s=>s==='#myXnxqSelect'&&!noControl?{value:fallback?'':'2026-2027-1',options:[{value:'2025-2026-2'}]}:null},
    fetch:async(url,options)=>{
      calls.push({url,options});
      if(failure==='network')throw Error('offline');
      const payload={datas:{xsjxrwcx:{rows:[row],totalSize:failure==='truncated'?2:totalSize}}};
      if(totalSize==='absent')delete payload.datas.xsjxrwcx.totalSize;
      if(failure==='shape')payload.datas.xsjxrwcx.rows={};
      return {ok:failure!=='http',status:503,url,text:async()=>failure==='login'?'<html>login</html>':JSON.stringify(payload)};
    },__sleepyBridge:{onWiseduResult:s=>results.push(JSON.parse(s))}};
  vm.runInNewContext(template.replaceAll('__SCHOOL_TYPE__','jlu_post'),{window:win,URL,FormData,AbortController,
    setTimeout:fn=>{timers.set(++id,fn);return id;},clearTimeout:id=>timers.delete(id)},{timeout:1000});
  for(let i=0;i<30&&!results.length;i++)await new Promise(r=>setImmediate(r));
  assert.equal(results.length,1);assert.equal(timers.size,0);
  return {result:results[0],calls,row};
}
(async()=>{
 const failures=[];let count=0,fixture;
 const check=async(name,fn)=>{count++;try{await fn();console.log('PASS '+name);}catch(e){failures.push(name);console.error('FAIL '+name+': '+e.message);}};
 for(const prefix of ['','/https/proxy-prefix'])for(const fallback of [false,true])await check('JLU prefix='+prefix+' fallback='+fallback,async()=>{
  const r=await execute({prefix,fallback});assert.equal(r.result.ok,true);assert.equal(r.calls.length,1);
  const u=new URL(r.calls[0].url);assert.equal(u.pathname,prefix+'/gsapp/sys/wdkbapp/modules/xskcb/xsjxrwcx.do');
  assert.equal(u.searchParams.get('XNXQDM'),fallback?'2025-2026-2':'2026-2027-1');
  assert.deepEqual(JSON.parse(r.result.data).datas.xsjxrwcx.rows,[r.row]);
  fixture=JSON.parse(r.result.data);
 });
 for(const totalSize of [-1,'-1','absent',null])await check('JLU accepts unknown total '+JSON.stringify(totalSize),async()=>{
  const r=await execute({totalSize});assert.equal(r.result.ok,true);
  assert.deepEqual(JSON.parse(r.result.data).datas.xsjxrwcx.rows,[r.row]);
 });
 for(const totalSize of [-2,'1.5',{},0,2])await check('JLU rejects invalid or incomplete total '+JSON.stringify(totalSize),async()=>{
  const r=await execute({totalSize});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
 });
 for(const failure of ['http','shape','network','login','truncated'])await check('JLU rejects '+failure,async()=>{
  const r=await execute({failure});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
 });
 await check('JLU missing selector does not guess current term',async()=>{const r=await execute({noControl:true});assert.equal(r.result.ok,false);assert.equal(r.calls.length,0);});
 assert.deepEqual(failures,[]);
 const p='app/src/test/resources/jw/fixtures/school-api/jlu-offline-envelope.json';
 if(process.argv.includes('--write-fixtures'))fs.writeFileSync(p,JSON.stringify(fixture,null,2)+'\n',{flag:'wx'});
 if(process.argv.includes('--check-fixtures'))assert.deepEqual(JSON.parse(fs.readFileSync(p,'utf8')),fixture);
 console.log(count+' offline JLU collector scenarios passed.');
})().catch(e=>{console.error(e);process.exitCode=1;});
