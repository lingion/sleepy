const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = 'app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwPagedSchoolCapture.kt';
const template = fs.existsSync(path) ? fs.readFileSync(path, 'utf8').match(/const val PAGED_SCHOOL_FETCH_JS = """([\s\S]*?)"""/)[1] : '';

async function execute(type, {count=65, prefix='', failure='', frame=false, api='/studentportal.php/Jxxx/rows', unknownTotal=false}={}) {
  const calls=[], results=[], timers=new Map(); let timerId=0, pending;
  const data=Array.from({length:count}, (_,i)=>type==='fstvc' ?
    {skbjmc:'软件技术一班',kcmc:'课程'+i,skjsxm:'教师'+i,zc:1+i%20,skrq:null,jcshow:i%2?'11-12':'3-4',skcdmc:'教室'+i,sknl:'内容'+i,xq:1,xqs:1+i%7,xn:'2026-2027'} :
    {cname:'课程'+i,name:'教师'+i,croomno:'教室'+i,week:1+i%7,seq:1+i%6,startweek:1+i%10,endweek:1+i%10,xf:'2.5'});
  const location=new URL('https://school.example'+prefix+(type==='fstvc'?'/studentportal.php':'/Login/MainDesktop'));
  const win={location,frames:[],document:{querySelectorAll:()=>[]},fetch:async (url, options)=>{
    const u=new URL(url); calls.push({url,options});
    if(failure==='timeout')return new Promise(r=>pending=r);
    if(failure==='network')throw Error('network failure');
    let body;
    if(u.pathname.endsWith('/Jxxx/xxjdxx'))body='PAGE';
    else if(u.pathname.endsWith('/student/StuInfo'))body=JSON.stringify({term:failure==='term'?'':'2026 春 & 秋'});
    else {
      const p=options.body ? Number(new URLSearchParams(options.body.toString()).get('page')) : Number(u.searchParams.get('page'));
      const limit=type==='fstvc'?30:50;
      let rows=data.slice((p-1)*limit,p*limit);
      if(failure==='repeat'&&p>1)rows=data.slice(0,limit);
      if(failure==='short'&&p===1)rows=rows.slice(0,1);
      if(failure==='shape')rows={};
      let total=failure==='changed'&&p>1?count+1:count;
      body=JSON.stringify(type==='fstvc'?{rows,total}:{data:rows,...(unknownTotal?{}:{total})});
      if(failure==='second-http'&&p===2)return {ok:false,status:500,url,text:async()=>''};
    }
    return {ok:failure!=='http',status:403,url:failure==='redirect'?'https://other.example/login':url,
      text:async()=>failure==='login'?'<html>Login</html>':body};
  }};
  const top=frame?{location:new URL('https://school.example/home'),document:{querySelectorAll:()=>[]},frames:[win]}:win;
  top.__sleepyBridge={onWiseduResult:s=>results.push(JSON.parse(s))};
  class DOMParser {
    parseFromString(text) {return {querySelector:selector=>text==='PAGE'&&failure!=='missing-table'&&selector==='table#mainlist'?
      {getAttribute:()=>"pagination:true,url:'"+api+"'"}:null};}
  }
  vm.runInNewContext(template.replaceAll('__SCHOOL_TYPE__',type), {window:top,URL,URLSearchParams,AbortController,DOMParser,Date,
    setTimeout:fn=>{timers.set(++timerId,fn);return timerId;},clearTimeout:id=>timers.delete(id)},{timeout:1000});
  for(let i=0;i<100&&!results.length;i++)await new Promise(r=>setImmediate(r));
  if(failure==='timeout'){
    for(const f of timers.values())f();
    if(pending)pending({ok:true,url:location.href,text:async()=>JSON.stringify({rows:[],total:0})});
    for(let i=0;i<20;i++)await new Promise(r=>setImmediate(r));
  }
  assert.equal(results.length,1,'exactly one completion'); assert.equal(timers.size,0,'deadline cleared');
  return {result:results[0],calls,data};
}

(async()=>{
  const failures=[];let count=0;const fixtures={};
  async function check(name,fn){count++;try{await fn();console.log('PASS '+name);}catch(e){failures.push(name);console.error('FAIL '+name+': '+e.message);}}
  for(const type of ['fstvc','gdbh']) {
    for(const prefix of ['','/https/proxy'])await check(type+' complete pagination '+prefix,async()=>{
      const r=await execute(type,{prefix,frame:true});assert.equal(r.result.ok,true);
      const output=JSON.parse(r.result.data); assert.deepEqual(type==='fstvc'?output:output.data,r.data);
      const requests=r.calls.slice(1,-1);assert.equal(requests.length,type==='fstvc'?3:2);
      assert.equal(new URL(r.calls.at(-1).url).pathname,type==='fstvc'?prefix+'/studentportal.php/Jxxx/xxjdxx':prefix+'/student/StuInfo');
      for(let i=0;i<requests.length;i++){
        const {url,options}=requests[i],u=new URL(url);
        assert.equal(options.credentials,'include');assert.equal(options.mode,'same-origin');assert.equal(options.redirect,'error');
        assert.ok(u.pathname.startsWith(prefix+'/'));
        if(type==='fstvc'){
          assert.equal(options.method,'POST');assert.equal(u.pathname,prefix+'/studentportal.php/Jxxx/rows');
          assert.deepEqual(Object.fromEntries(new URLSearchParams(options.body.toString())),{page:String(i+1),rows:'30',zc:'0',sort:'skrq',order:'asc'});
        } else {
          assert.equal(options.method,'GET');assert.equal(u.pathname,prefix+'/student/getstutable');
          assert.equal(u.searchParams.get('term'),'2026 春 & 秋');assert.equal(u.searchParams.get('page'),String(i+1));
          assert.equal(u.searchParams.get('start'),String(i*50));assert.equal(u.searchParams.get('limit'),'50');
        }
      }
      fixtures[type]=output;
    });
    for(const failure of ['http','network','login','shape','short','changed','second-http','repeat','redirect','timeout'])await check(type+' fails atomically on '+failure,async()=>{
      const r=await execute(type,{failure});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
    });
    await check(type+' empty semester is complete',async()=>{
      const r=await execute(type,{count:0});assert.equal(r.result.ok,true);const d=JSON.parse(r.result.data);assert.deepEqual(type==='fstvc'?d:d.data,[]);
    });
  }
  await check('GDBH unknown total continues past fifty rows',async()=>{
    const r=await execute('gdbh',{count:100,unknownTotal:true});assert.equal(r.result.ok,true);
    assert.equal(r.calls.length,5);assert.deepEqual(JSON.parse(r.result.data).data,r.data);
  });
  await check('GDBH missing term never guesses',async()=>{const r=await execute('gdbh',{failure:'term'});assert.equal(r.result.ok,false);assert.equal(r.calls.length,1);});
  await check('FSTVC missing API marker',async()=>{const r=await execute('fstvc',{failure:'missing-table'});assert.equal(r.result.ok,false);assert.equal(r.calls.length,1);});
  for(const api of ['https://evil.example/upload','//evil.example/upload','javascript:alert(1)'])await check('FSTVC refuses foreign API '+api,async()=>{
    const r=await execute('fstvc',{api});assert.equal(r.result.ok,false);assert.equal(r.calls.length,1);
  });
  assert.deepEqual(failures,[]);
  for(const [name,data] of Object.entries(fixtures)) {
    const file='app/src/test/resources/jw/fixtures/school-api/'+name+'-offline-envelope.json';
    if(process.argv.includes('--write-fixtures'))fs.writeFileSync(file,JSON.stringify(data,null,2)+'\n',{flag:'wx'});
    if(process.argv.includes('--check-fixtures'))assert.deepEqual(JSON.parse(fs.readFileSync(file,'utf8')),data);
  }
  console.log(count+' offline paged collector scenarios passed.');
})().catch(e=>{console.error(e);process.exitCode=1;});
