const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const dir=__dirname,defaultSource=fs.existsSync(path.join(dir,'JwSchoolApiCapture.kt'))?dir:path.resolve(dir,'../app/src/main/java/com/lingion/sleepy/ui/screen/imports');
const sourceDir=process.env.API_PAGED_CAPTURE_SOURCE_DIR || defaultSource;
function source(name){const raw=fs.readFileSync(path.join(sourceDir,name),'utf8');const start=raw.indexOf('"""')+3;const end=raw.indexOf('"""',start);assert.ok(start>=3 && end>start);return raw.slice(start,end);}
const api=source('JwSchoolApiCapture.kt'),paged=source('JwPagedSchoolCapture.kt');
new vm.Script(api);new vm.Script(paged);
const types=['ccibe','cppu','jlu_post','fstvc','gdbh'];
const locations={ccibe:'https://my.ccibe.edu.cn/student',cppu:'https://jw.cppu.edu.cn/student',jlu_post:'https://gmis.jlu.edu.cn/gsapp/sys/wdkbapp/index.do',fstvc:'https://example.edu.cn/studentportal.php/Jxxx/xxjdxx',gdbh:'https://example.edu.cn/Login/MainDesktop'};
const selectors={ccibe:'#rc_select_0_list [aria-selected="true"]',cppu:'.je-grid-btnbar-queryStrategy',jlu_post:'#myXnxqSelect'};
const row=i=>({kcmc:'课程'+i,kcS:'课程'+i,cname:'课程'+i,teacher:'教师'+i,room:'教室'+i,week:1,seq:1});
function docFor(type){
  const state={fields:[{tagName:'SELECT',name:'term',id:'term',type:'select-one',value:'2026-1',checked:false,options:[{value:'2026-1',selected:true},{value:'2026-2',selected:false}]}]};
  state.selection=type==='ccibe'?{textContent:'2026-1'}:type==='cppu'?{parentElement:{childNodes:[{nodeType:3,textContent:'当前学期'},{nodeType:1,textContent:'嵌套按钮'}]}}:state.fields[0];
  state.querySelectorAll=s=>s==='input,select,textarea'?state.fields:s===selectors[type] && state.selection?[state.selection]:[];
  state.querySelector=s=>state.querySelectorAll(s)[0] || null;
  return state;
}
class FakeDOMParser{parseFromString(text){const match=/data-options="([^"]*)"/.exec(text);return {querySelector:s=>s==='table#mainlist' && match?{getAttribute:()=>match[1]}:null};}}
async function execute(type,options={}){
  const callbacks=[],calls=[],timers=new Map(),counts={},W={location:new URL(locations[type]),document:docFor(type),frames:[]};
  let tid=0,root={location:new URL('https://portal.example/'),document:docFor('portal'),frames:[W]};
  function duplicate(){return {...W,location:new URL(W.location.href),document:docFor(type),frames:[]};}
  function mutate(){
    if(options.mutation==='detach')root.frames=[];
    if(options.mutation==='replace-frame')root.frames=[duplicate()];
    if(options.mutation==='replace-document')W.document=docFor(type);
    if(options.mutation==='extra-candidate')root.frames.push(duplicate());
    if(options.mutation==='navigate')W.location=new URL(W.location.href+'?view=changed');
    if(options.mutation==='change-control')W.document.fields[0].value='2026-2';
    if(options.mutation==='remove-control')W.document.fields=[];
    if(options.mutation==='change-selection'){
      if(type==='ccibe')W.document.selection.textContent='2026-2';
      else if(type==='cppu')W.document.selection.parentElement.childNodes[0].textContent='下一学期';
      else W.document.fields[0].value='2026-2';
    }
    if(options.mutation==='remove-selector')W.document.selection=null;
    if(options.mutation==='deep-candidate'){
      let top={location:new URL('https://neutral.example/'),document:docFor('neutral'),frames:[]},cur=top;
      for(let i=0;i<8;i++){const next={location:new URL('https://neutral.example/'),document:docFor('neutral'),frames:[]};cur.frames.push(next);cur=next;}
      cur.frames.push(duplicate());root.frames.push(top);
    }
  }
  function body(url,init){
    const u=new URL(url),key=u.pathname;
    counts[key]=(counts[key]||0)+1;
    if(type==='ccibe'){
      if(u.searchParams.get('week'))return {data:{calendar:{id:options.badTotal?'2026-2':'2026-1'},wdkb:[row(Number(u.searchParams.get('week')))]}};
      const changed=counts[key]>3 && options.backendChange;
      return {data:{calendar:{id:changed==='id'?'2026-2':'2026-1',allWeek:changed==='count'?3:2,startTime:changed==='date'?'2026-09-07':'2026-08-31'},time:[{node:1}]}};
    }
    if(type==='cppu'){
      if(key.startsWith('/je/system/'))return {data:{values:{DM:options.backendChange==='term' && counts[key]>1?'2026-2':'2026-1'}}};
      if(key.endsWith('/getClassFormDefault.action'))return {jxzqsrq:options.backendChange==='date' && counts[key]>1?'2026-09-07':'2026-08-31'};
      return {rows:[row(1),row(2)],total:options.badTotal?3:2};
    }
    if(type==='jlu_post')return {datas:{xsjxrwcx:{rows:[row(1),row(2)],totalSize:options.badTotal?3:2}}};
    if(type==='fstvc' && key.endsWith('/xxjdxx'))return '<table id="mainlist" data-options="url:\'studentportal.php/Jxxx/data?term='+(options.backendChange==='endpoint' && counts[key]>1?'2026-2':'2026-1')+'\'"></table>';
    if(type==='gdbh' && key.endsWith('/StuInfo'))return {term:options.backendChange==='term' && counts[key]>1?'2026-2':'2026-1'};
    const form=new Map(init.body?Array.from(init.body.entries()):[]),page=Number(type==='fstvc'?form.get('page'):u.searchParams.get('page'));
    const size=type==='fstvc'?30:50,total=type==='fstvc'?61:101;
    let items=Array.from({length:Math.min(size,total-(page-1)*size)},(_,i)=>row((page-1)*size+i));
    if(options.duplicatePage && page>1)items=Array.from({length:size},(_,i)=>row(i));
    return type==='fstvc'?{rows:items,total:options.badTotal?62:total}:{data:items,total:options.badTotal?102:total};
  }
  W.fetch=async(url,init)=>{
    calls.push({url,init});const index=calls.length;
    if(options.hanging && index===1)return new Promise((resolve,reject)=>init.signal.addEventListener('abort',()=>reject(Error('aborted'))));
    const payload=body(url,init);
    if((options.at||1)===index && options.phase==='headers')mutate();
    const responseUrl=options.responsePath && index===1?new URL(options.responsePath,url).href:url;
    return {ok:!options.httpError,status:options.httpError?500:200,url:responseUrl,text:async()=>{
      if((options.at||1)===index && options.phase==='body')mutate();
      if(options.invalidJson)return '<html>login</html>';
      if(options.failure)return JSON.stringify({success:false});
      return typeof payload==='string'?payload:JSON.stringify(payload);
    }};
  };
  if(options.duplicateInitial)root.frames.push(duplicate());
  if(options.crossOriginSibling)root.frames.push(Object.defineProperties({},{location:{get(){throw Error('cross origin');}},document:{get(){throw Error('cross origin');}},frames:{get(){throw Error('cross origin');}}}));
  root.__sleepyBridge={onWiseduResult:value=>callbacks.push(JSON.parse(value))};
  const context={window:root,URL,URLSearchParams,FormData,AbortController,DOMParser:FakeDOMParser,Date,Set,console,
    setTimeout:fn=>{timers.set(++tid,fn);return tid;},clearTimeout:id=>timers.delete(id)};
  vm.runInNewContext((type==='fstvc'||type==='gdbh'?paged:api).replaceAll('__SCHOOL_TYPE__',type),context);
  for(let i=0;i<20 && !callbacks.length;i++)await new Promise(resolve=>setImmediate(resolve));
  if(options.hanging){assert.equal(timers.size,1);Array.from(timers.values())[0]();await new Promise(resolve=>setImmediate(resolve));}
  assert.equal(callbacks.length,1,'exactly one terminal callback');assert.equal(timers.size,0,'deadline cleared');
  return {result:callbacks[0],calls};
}
(async()=>{
  let passed=0;const failed=[];
  async function check(name,fn){try{await fn();passed++;console.log('PASS '+name);}catch(e){failed.push(name);console.error('FAIL '+name+'\n'+e.stack);}}
  for(const type of types){
    await check(type+' retains full response and original fields',async()=>{
      const {result,calls}=await execute(type);assert.equal(result.ok,true);const data=JSON.parse(result.data);
      const rows=type==='ccibe'?data.weeks.flatMap(p=>p.data.wdkb):type==='jlu_post'?data.datas.xsjxrwcx.rows:type==='fstvc'?data:type==='gdbh'?data.data:data.rows;
      assert.equal(rows.length,type==='fstvc'?61:type==='gdbh'?101:2);assert.equal(rows[0].teacher,'教师'+(type==='fstvc'||type==='gdbh'?0:1));
      for(const c of calls){assert.equal(c.init.credentials,'include');assert.equal(c.init.mode,'same-origin');assert.equal(c.init.redirect,'error');}
      if(type==='cppu')assert.equal(data.startDate,'2026-08-31');
      if(type==='jlu_post')assert.equal(new URL(calls[0].url).searchParams.get('XNXQDM'),'2026-1');
    });
    await check(type+' allows unreadable cross origin sibling',async()=>assert.equal((await execute(type,{crossOriginSibling:true})).result.ok,true));
    await check(type+' rejects two initial matching frames',async()=>assert.equal((await execute(type,{duplicateInitial:true})).result.ok,false));
    await check(type+' rejects controls changed during final response body',async()=>assert.equal((await execute(type,
      {phase:'body',mutation:'change-control',at:{ccibe:4,cppu:5,jlu_post:1,fstvc:5,gdbh:5}[type]})).result.ok,false));
    for(const phase of ['headers','body'])for(const mutation of ['detach','replace-frame','replace-document','extra-candidate','navigate','change-control','remove-control','change-selection','deep-candidate'])
      await check(type+' rejects '+mutation+' after '+phase,async()=>assert.equal((await execute(type,{phase,mutation})).result.ok,false));
    if(selectors[type])await check(type+' rejects removed protocol selector after body',async()=>assert.equal((await execute(type,{phase:'body',mutation:'remove-selector'})).result.ok,false));
    for(const responsePath of ['/login','?unexpected=term'])await check(type+' rejects same origin wrong response '+responsePath,async()=>assert.equal((await execute(type,{responsePath})).result.ok,false));
    for(const fault of ['httpError','invalidJson','failure','hanging'])await check(type+' rejects '+fault,async()=>assert.equal((await execute(type,{[fault]:true})).result.ok,false));
    await check(type+(type==='ccibe'?' rejects weekly calendar mismatch':' rejects inconsistent course total'),async()=>assert.equal((await execute(type,{badTotal:true})).result.ok,false));
  }
  for(const change of ['id','count','date'])await check('CCIBE rejects backend calendar '+change+' drift',async()=>assert.equal((await execute('ccibe',{backendChange:change})).result.ok,false));
  for(const change of ['term','date'])await check('CPPU rejects backend '+change+' drift',async()=>assert.equal((await execute('cppu',{backendChange:change})).result.ok,false));
  await check('FSTVC rejects backend data endpoint drift',async()=>assert.equal((await execute('fstvc',{backendChange:'endpoint'})).result.ok,false));
  await check('GDBH rejects backend current term drift',async()=>assert.equal((await execute('gdbh',{backendChange:'term'})).result.ok,false));
  for(const type of ['fstvc','gdbh']){
    await check(type+' rejects changed selection while later page arrives',async()=>assert.equal((await execute(type,{phase:'body',mutation:'change-control',at:3})).result.ok,false));
    await check(type+' rejects duplicate page without delivering partial rows',async()=>assert.equal((await execute(type,{duplicatePage:true})).result.ok,false));
  }
  console.log('RESULT '+passed+' passed, '+failed.length+' failed');if(failed.length)process.exitCode=1;
})();
