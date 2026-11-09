// Actual production collector with synthetic responses shaped after the pinned Chaoxing sources.
// moeshin/ai-schedule-chaoxing@4650f59887d119b9c9828fb19447f736b549654e.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt','utf8');
const script = source.match(/private const val CHAOXING_FETCH_JS = """([\s\S]*?)"""/)[1];
const row = {kcmc:'数据结构',djc:3,xingqi:2,zc:'1,3,7-9周',tmc:'教师甲',croommc:'教学楼301',xqmc:'南校区'};
const term = '2026-2027-1';
function doc({context=true,selected=true,multi=true,student='abc_student-id_123',campus='campus-a',semester=term}={}) {
  const fields = context ? {xhid:student,xqdm:campus} : {};
  const options = [{value:semester,hasAttribute:key=>key==='selected'&&selected}];
  if (multi) options.push({value:'2027-2028-1',hasAttribute:()=>false});
  const select = {value:selected?semester:'',options};
  const document = {body:{innerHTML:''},fields,select,frames:[],querySelector:key=>key==='#xnxq1'?(context?select:null):
    fields[key.slice(1)]?{value:fields[key.slice(1)]}:null,querySelectorAll:key=>key==='iframe'?document.frames:[]};
  return document;
}
async function execute(o={}) {
  const calls=[],results=[],timers=new Map(); let timerId=0,late;
  const current=doc({context:!!o.legacyContext,selected:o.selected!==false});
  const loaded=doc({selected:o.selected!==false,multi:o.multi!==false});
  const location=new URL('https://example.edu'+(o.path||'/admin'));
  const frame={contentDocument:doc(o.frameContext),contentWindow:{location:new URL(o.frameUrl||'https://example.edu/admin/pkgl/xskb/queryKbForXsd')}};
  if(o.unreadableFrame)Object.defineProperty(frame,'contentDocument',{get(){throw Error('cross-origin frame');}});
  if(o.iframe)current.frames.push(frame);
  const fetch=async (raw,init)=>{
    const url=new URL(raw,location);calls.push({url,init});
    let text, status=200;
    if(url.pathname.endsWith('getMenuList')) {
      if(o.menuFailure) throw Error('menu unavailable');
      text=JSON.stringify({jsxq:{dataXnxq:term,xqid:'campus-a'}});
    } else if(url.pathname.endsWith('queryKbForGrdb')) {
      if(o.rowFailure)throw Error('course unavailable');
      if(o.login)text='<html><form><input type="password"></form></html>';
      else if(o.legacy){text='<html>not found</html>';status=404;}
      else if(o.malformed)text='broken json';
      else text=JSON.stringify(o.array?[row]:{data:o.empty?[]:[row]});
    } else if(url.pathname.endsWith('queryKbForXsd')) {
      text='<html><input id="xhid"></html>';
    } else if(url.pathname.endsWith('sdpkkbList')) {
      if(o.xsd&&!url.pathname.includes('/xsd/')){status=404;text='<html>not found</html>';}
      else {assert.equal(url.searchParams.get('xhid'),'abc_student-id_123');
        assert.equal(url.searchParams.get('xnxq'),term);assert.equal(url.searchParams.get('xqdm'),'campus-a');
        text=JSON.stringify([row]);}
    } else if(url.pathname.endsWith('getZclistByXnxq')) {
      if(o.clockFailure)throw Error('clock unavailable');
      if(o.clockMalformed)text='<html>unavailable</html>';
      else text=JSON.stringify({data:{dqzc:9,jcsjszList:[{jc:'3',kssj:'10:00',jssj:'10:45'}]}});
    } else throw Error('unexpected '+url.pathname);
    const response={ok:status===200,status,url:url.href,headers:{get:()=>''},
      text:async()=>{if(o.mutate&&url.pathname.endsWith('getZclistByXnxq'))await o.mutate({current,frame,location});return text;},
      json:async()=>JSON.parse(text)};
    if(o.clockTimeout&&url.pathname.endsWith('getZclistByXnxq'))return new Promise(resolve=>late=()=>resolve(response));
    return response;
  };
  const window={location,document:current,__sleepyBridge:{onWiseduResult:text=>results.push(JSON.parse(text))}};
  class DOMParser{parseFromString(){return loaded;}}
  vm.runInNewContext(script,{window,location,document:current,fetch,URL,URLSearchParams,DOMParser,AbortController,
    setTimeout:fn=>{timers.set(++timerId,fn);return timerId;},clearTimeout:id=>timers.delete(id)},{timeout:1000});
  for(let i=0;i<35&&!results.length;i++)await new Promise(resolve=>setImmediate(resolve));
  if(o.clockTimeout){for(const fn of [...timers.values()])fn();if(late)late();
    for(let i=0;i<10;i++)await new Promise(resolve=>setImmediate(resolve));}
  assert.equal(results.length,1,'one terminal response');assert.equal(timers.size,0,'release optional request deadline');
  return {result:results[0],calls};
}
(async()=>{
  const failures=[];let passed=0;
  async function check(name,fn){try{await fn();passed++;}catch(e){failures.push(name+': '+e.message);}}
  for(const path of ['/admin','/admin/','/'])await check('prefix and exact clock fields '+path,async()=>{
    const r=await execute({path});assert.equal(r.result.ok,true,r.result.err);
    assert.deepEqual(JSON.parse(r.result.data).rows,[row]);
    assert.deepEqual(r.result.periods,[{node:3,start:'10:00',end:'10:45'}]);
    assert.equal(r.calls[0].url.pathname,(path==='/'?'':'/admin')+'/api/getMenuList');
  });
  await check('top level array remains opaque course rows',async()=>{
    const r=await execute({array:true});assert.equal(r.result.ok,true,r.result.err);assert.deepEqual(JSON.parse(r.result.data).rows,[row]);
  });
  for(const failure of ['clockFailure','clockMalformed','clockTimeout'])await check(failure+' keeps fetched courses',async()=>{
    const r=await execute({[failure]:true});assert.equal(r.result.ok,true,r.result.err);
    assert.deepEqual(JSON.parse(r.result.data).rows,[row]);assert.deepEqual(r.result.periods,[]);
  });
  for(const options of [{legacy:true,legacyContext:true},{legacy:true,menuFailure:true},
      {legacy:true,legacyContext:true,xsd:true}])await check('legacy route '+JSON.stringify(options),async()=>{
    const r=await execute(options);assert.equal(r.result.ok,true,r.result.err);
    assert.deepEqual(JSON.parse(r.result.data).rows,[row]);
    assert(r.calls.some(call=>call.url.pathname==='/admin/pkgl/xskb/sdpkkbList'));
    if(options.xsd)assert(r.calls.some(call=>call.url.pathname==='/admin/xsd/pkgl/xskb/sdpkkbList'));
  });
  await check('multiple server semesters without selection cannot guess',async()=>{
    const r=await execute({legacy:true,menuFailure:true,selected:false});assert.equal(r.result.ok,false);
    assert(!r.calls.some(call=>call.url.pathname.endsWith('sdpkkbList')));
  });
  for(const failure of ['rowFailure','malformed','login','empty'])await check(failure+' never imports a fabricated timetable',async()=>{
    const r=await execute({[failure]:true});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
    if(failure==='login')assert.equal(r.calls.length,2,'login HTML must not enter legacy fallback');
  });
  for(const legacyContext of [false,true])await check('stable iframe context succeeds with top controls '+legacyContext,async()=>{
    const r=await execute({iframe:true,legacyContext});assert.equal(r.result.ok,true,r.result.err);
    assert.equal(JSON.parse(r.result.data).xnxq,term);assert.deepEqual(JSON.parse(r.result.data).rows,[row]);
  });
  await check('stable iframe supplies the legacy timetable parameters',async()=>{
    const r=await execute({iframe:true,legacy:true});assert.equal(r.result.ok,true,r.result.err);
    assert(r.calls.some(call=>call.url.pathname.endsWith('sdpkkbList')));
  });
  await check('accessible about:blank iframe supplies the legacy context',async()=>{
    const r=await execute({iframe:true,frameUrl:'about:blank',legacy:true});assert.equal(r.result.ok,true,r.result.err);
    assert(!r.calls.some(call=>call.url.pathname.endsWith('queryKbForXsd')),'use the accessible live iframe context');
  });
  await check('unreadable foreign frame does not discard stable top context',async()=>{
    const r=await execute({iframe:true,unreadableFrame:true,legacyContext:true});assert.equal(r.result.ok,true,r.result.err);
  });
  for(const [name,mutate] of [
    ['semester',({frame})=>{frame.contentDocument.select.value='2027-2028-1';}],
    ['student',({frame})=>{frame.contentDocument.fields.xhid='different-student';}],
    ['campus',({frame})=>{frame.contentDocument.fields.xqdm='campus-b';}],
    ['frame navigation',({frame})=>{frame.contentWindow.location.pathname='/admin/other-table';}],
    ['frame document replacement',({frame})=>{frame.contentDocument=doc();}],
    ['frame removal',({current})=>{current.frames=[];}],
    ['top navigation',({location})=>{location.pathname='/admin/other-table';}],
    ['frame login',({frame})=>{frame.contentDocument.body.innerHTML='<form><input type="password"></form>';}],
  ])await check('iframe '+name+' change rejects the old result',async()=>{
    const r=await execute({iframe:true,mutate});assert.equal(r.result.ok,false,'changed iframe must not import old courses');
    assert.equal(r.result.data,undefined);
  });
  for(const key of ['xhid','xqdm'])await check('top '+key+' change rejects the old result',async()=>{
    const r=await execute({legacyContext:true,mutate:({current})=>{current.fields[key]='changed';}});
    assert.equal(r.result.ok,false,'changed student or campus must not import old courses');assert.equal(r.result.data,undefined);
  });
  for(const [name,frameContext] of [['semester',{semester:'2027-2028-1'}],['student',{student:'other'}],['campus',{campus:'other'}]])
    await check('conflicting top and iframe '+name+' cannot guess',async()=>{
      const r=await execute({iframe:true,legacyContext:true,frameContext});assert.equal(r.result.ok,false,'conflicting documents must fail');
      assert.equal(r.calls.length,0,'conflicting live context fails before requests');assert.equal(r.result.data,undefined);
    });
  await check('new same-origin frame invalidates the captured context',async()=>{
    const r=await execute({legacyContext:true,mutate:({current,frame})=>{current.frames.push(frame);}});
    assert.equal(r.result.ok,false,'new frame must be sampled on a fresh import');assert.equal(r.result.data,undefined);
  });
  failures.forEach(message=>console.error('FAIL '+message));assert.deepEqual(failures,[]);
  console.log('Chaoxing collector: '+passed+' offline production scenarios passed.');
})().catch(e=>{console.error(e);process.exitCode=1;});
