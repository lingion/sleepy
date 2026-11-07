// Executes the delivered Kotlin template in Node VM. DOM is an explicit offline stub.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwJsonSchoolCapture.kt'),'utf8');
const template = source.match(/private const val SCRIPT = """([\s\S]*?)"""/)[1];
const hosts = {sustech:'https://tis.sustech.edu.cn/authentication/main',shsmu:'https://jwstu.shsmu.edu.cn/Home/Curriculum',ygu:'https://ygu.edu.cn/user/index',zju_post:'https://grs.example.edu/py/kcb?xnxq=2026-2027-1&zc=2'};
const zOptions = [{value:'2',textContent:'第2周'},{value:'8',textContent:'第8周'},{value:'13',textContent:'第13周'}];
const lesson = {KEY:'xq1_jc1',SKSJ:'数学[李老师][班级][1-16周][主楼][第1-2节]'};
const shsmuLesson = {Curriculum:'解剖学',Start:'2026-09-08T08:00:00',End:'2026-09-08T09:30:00',Teacher:'张老师',ClassroomAcademy:'实验室'};
const yguData = [{weekList:[{kcbVoList:[{kcmc:'经济学',xq:2,jcs:'3-4',zcs:'1-16',dsz:'单',jsmc:'文科楼',rkls:'王老师'}]}]}];
function documentStub(fields, options, hasForm=true) {
  return {documentElement:{outerHTML:'<html><form id="kcbForm"></form></html>'},
    querySelectorAll(selector) {
      const match = /^\[name="([^"]+)"\],#/.exec(selector);
      if (!match) throw Error('Unexpected selector '+selector);
      const value = fields[match[1]];
      return value === undefined ? [] : (Array.isArray(value) ? value : [value]).map(value=>({value}));
    },
    querySelector(selector) {
      if(selector==='#zc')return options === null ? null : {options};
      if(selector==='#kcbForm')return hasForm ? {} : null;
      if(selector==='input[type="password"]')return null;
      throw Error('Unexpected selector '+selector);
    }};
}
async function execute(type, options={}) {
  const calls=[], callbacks=[], timers=new Map(); let timerId=0, late;
  const fields={...(type==='sustech' && !options.current ? {xn:'2026-2027',xq:'1'} : {}),...(type==='ygu' && !options.noSelected ? {xqjc:'2026-2027-1'} : {}),...options.fields};
  const weekOptions=options.weekOptions===undefined ? zOptions.map(x=>({...x})) : options.weekOptions;
  const initial=options.url || hosts[type], location=new URL(initial);
  const doc=documentStub(fields,weekOptions,!options.noForm);
  let currentCalls=0;
  const fetch=async(url,init)=>{
    calls.push({url,init}); const u=new URL(url); let payload;
    if(u.pathname==='/component/querydangqianxnxq') {
      currentCalls++; payload=options.currentPayload || {XN:options.year || '2026-2027',XQ:currentCalls>1 && options.currentChanged ? '2' : '1'};
    } else if(u.pathname==='/xszykb/queryxszykbzong') payload=options.rows===undefined ? [lesson,{KEY:'bz'}] : options.rows;
    else if(/\/Home\/GetCurriculumTable$/.test(u.pathname)) payload=options.shsmuPayload || {list:[shsmuLesson]};
    else if(u.pathname==='/api/jw/xlgl/listR') payload=options.terms || {code:0,data:[{xqjc:'2027-2028-1'},{xqjc:'2026-2027-1'},{xqjc:'2025-2026-2'}]};
    else if(u.pathname==='/api/system/user/getInfo')payload=options.user || {code:0,user:{studentsNumber:'20261234',nickName:'测试同学'}};
    else if(u.pathname==='/api/jw/kpkglkcb/student') payload=options.yguPayload || {code:0,data:yguData};
    else if(type==='zju_post') payload='<html data-week="'+u.searchParams.get('zc')+'"><form id="kcbForm"></form></html>';
    else throw Error('Unexpected request '+url);
    if(options.changeFields && calls.length===options.changeFields.at)Object.assign(fields,options.changeFields.values);
    if(options.navigate)location.href=initial+'#changed';
    if(options.failure==='network')throw Error('offline');
    const response={ok:options.failure!=='http',status:options.failure==='http'?503:200,
      url:options.finalUrl || url,text:async()=>options.failure==='login'?'<html>登录</html>':typeof payload==='string'?payload:JSON.stringify(payload)};
    if(options.hanging)return new Promise(resolve=>{late=()=>resolve(response);});
    return response;
  };
  const W={location,document:doc,fetch,frames:[],performance:{getEntriesByType:()=>options.entries || (type==='shsmu' && !options.noRange ? [{name:location.origin+'/Home/GetCurriculumTable?Start=2026-09-07&End=2027-01-31',startTime:1}] : [])},
    DOMParser:class {parseFromString(raw,mime) {assert.equal(mime,'text/html'); const page=documentStub({},options.responseWeekOptions===undefined?weekOptions:options.responseWeekOptions, !options.responseNoForm && raw.includes('id="kcbForm"')); if(options.responsePassword){const query=page.querySelector;page.querySelector=s=>s==='input[type="password"]'?{}:query(s);} return page;}}};
  if(options.wrongHost)W.location=new URL('https://evil.example'+location.pathname);
  const root=options.nested?{location:new URL('https://portal.example.edu/'),document:documentStub({},null,false),frames:[W]}:W;
  root.__sleepyBridge={onWiseduResult:json=>callbacks.push(JSON.parse(json))};
  const context={window:root,URL,URLSearchParams,AbortController,Date,Set,console,
    setTimeout:fn=>{timers.set(++timerId,fn);return timerId;},clearTimeout:id=>timers.delete(id)};
  const running=vm.runInNewContext(template.replaceAll('__SCHOOL_TYPE__',type),context);
  await new Promise(resolve=>setImmediate(resolve));
  if(options.hanging){assert.equal(timers.size,1);timers.values().next().value();late();}
  await running;
  assert.equal(callbacks.length,1,'Exactly one callback');assert.equal(timers.size,0);
  return {result:callbacks[0],calls,fields};
}
(async()=>{
  let count=0;
  async function check(name,body){await body();count++;console.log('PASS '+name);}
  await check('SUSTech selected semester exact form and raw array',async()=>{
    const {result,calls}=await execute('sustech');assert.equal(result.ok,true);assert.equal(result.semester,'2026-2027-1');assert.deepEqual(JSON.parse(result.data),[lesson,{KEY:'bz'}]);
    assert.equal(calls.length,1);assert.equal(calls[0].init.body,'xn=2026-2027&xq=1');
  });
  await check('SUSTech current semester double checked, year normalized',async()=>{
    const {result,calls}=await execute('sustech',{current:true,year:'2026'});assert.equal(result.ok,true);assert.equal(calls.length,3);assert.equal(calls[1].init.body,'xn=2026-2027&xq=1');
  });
  for(const rows of [[],{content:[lesson]},{data:[lesson]}])await check('SUSTech accepted envelope '+JSON.stringify(rows).slice(0,20),async()=>{assert.equal((await execute('sustech',{rows})).result.ok,true);});
  for(const options of [{fields:{xn:'2026-2028'}},{fields:{xq:''}},{fields:{XN:'2027-2028'}},{rows:[{KEY:'xq8_jc1',SKSJ:'x'}]},{rows:{content:{}}},{current:true,currentPayload:{XN:'2026',XQ:null}},{current:true,currentChanged:true},{changeFields:{at:1,values:{xq:'2'}}}])await check('SUSTech rejects invalid or changing context '+JSON.stringify(options),async()=>{assert.equal((await execute('sustech',options)).result.ok,false);});
  await check('SHSMU selected date range does not prove semester origin',async()=>{
    const {result,calls}=await execute('shsmu');assert.equal(result.ok,true);assert.equal(calls.length,1);assert.equal(new URL(calls[0].url).search,'?Start=2026-09-07&End=2027-01-31');assert.equal(result.startDate,undefined);
    const data=JSON.parse(result.data);assert.equal(data.sleepyShsmuCapture,true);assert.equal(data.semesterStartDate,undefined);assert.deepEqual(data.list,[shsmuLesson]);assert.equal(result.calendarBasis,'unknown');assert.equal(result.requiresSemesterStart,true);
  });
  await check('SHSMU selected midweek range stays unbound',async()=>{const {result}=await execute('shsmu',{url:'https://jwstu.shsmu.edu.cn/Home/GetCurriculumTable?Start=2026-10-14&End=2026-10-18'});assert.equal(result.ok,true);assert.equal(result.startDate,undefined);assert.equal(JSON.parse(result.data).rangeStart,'2026-10-14');});
  await check('SHSMU latest same-origin observed request only',async()=>{const {result}=await execute('shsmu',{entries:[{name:'https://jwstu.shsmu.edu.cn/Home/GetCurriculumTable?Start=2025-09-15&End=2026-02-26',startTime:1},{name:'https://jwstu.shsmu.edu.cn/Home/GetCurriculumTable?Start=2026-09-07&End=2027-01-31',startTime:5},{name:'https://evil.example/Home/GetCurriculumTable?Start=2024-01-01&End=2024-12-31',startTime:9}]});assert.equal(result.ok,true);assert.equal(result.startDate,undefined);assert.equal(JSON.parse(result.data).rangeStart,'2026-09-07');});
  await check('SHSMU discards unproven calendar fields in endpoint response',async()=>{const {result}=await execute('shsmu',{shsmuPayload:{list:[shsmuLesson],semesterStartDate:'2026-09-07',semesterStartBasis:'selected-date-range'}});assert.equal(result.ok,true);const data=JSON.parse(result.data);assert.equal(data.semesterStartDate,undefined);assert.equal(data.semesterStartBasis,undefined);});
  await check('SHSMU retains WebVPN path prefix',async()=>{
    const prefix='https://webvpn2.shsmu.edu.cn/https/encoded';const {result,calls}=await execute('shsmu',{url:prefix+'/Home/Curriculum',noRange:true,fields:{Start:'2026-09-07',End:'2027-01-31'}});assert.equal(result.ok,true);assert.ok(calls[0].url.startsWith(prefix+'/Home/GetCurriculumTable?'));
  });
  for(const options of [{noRange:true},{noRange:true,fields:{Start:'2026-02-30',End:'2026-12-01'}},{fields:{Start:'2027-02-01',End:'2027-01-31'}},{shsmuPayload:{rows:[]}},{changeFields:{at:1,values:{Start:'2026-09-14'}}}])await check('SHSMU rejects missing or changing range '+JSON.stringify(options),async()=>{assert.equal((await execute('shsmu',options)).result.ok,false);});
  await check('YGU selects declared old term without current-list ordering guess',async()=>{
    const {result,calls}=await execute('ygu');assert.equal(result.ok,true);assert.equal(calls.length,3);assert.deepEqual(JSON.parse(calls[2].init.body),{xm:'测试同学',xh:'20261234',xqjc:'2026-2027-1'});assert.deepEqual(JSON.parse(result.data).data,yguData);
  });
  await check('YGU URL selected term',async()=>{const {result}=await execute('ygu',{noSelected:true,url:'https://ygu.edu.cn/user/index#/schedule?xqjc=2026-2027-1'});assert.equal(result.ok,true);});
  await check('YGU unique only term is explicit',async()=>{assert.equal((await execute('ygu',{noSelected:true,terms:{code:0,data:[{xqjc:'2026-2027-1'}]}})).result.ok,true);});
  await check('YGU selected empty term stays empty',async()=>{const {result,calls}=await execute('ygu',{yguPayload:{code:0,data:[]}});assert.equal(result.ok,true);assert.equal(calls.length,3);assert.deepEqual(JSON.parse(result.data).data,[]);});
  for(const options of [{noSelected:true},{terms:{code:null,data:[{xqjc:'2026-2027-1'}]}},{terms:{code:0,data:[{xqjc:'2026-2027-1'},{xqjc:'2026-2027-1'}]}},{user:{code:0,user:{studentsNumber:'1'}}},{yguPayload:{code:0,data:[{}]}},{yguPayload:{code:0,data:[],xqjc:'other'}},{changeFields:{at:3,values:{xqjc:'2027-2028-1'}}}])await check('YGU rejects ambiguity or malformed response '+JSON.stringify(options),async()=>{assert.equal((await execute('ygu',options)).result.ok,false);});
  await check('ZJU collects all actual sparse week values',async()=>{const {result,calls}=await execute('zju_post');assert.equal(result.ok,true);assert.deepEqual(calls.map(c=>new URL(c.url).searchParams.get('zc')),['2','8','13']);assert.equal(JSON.parse(result.data).length,3);assert.ok(calls.every(c=>new URL(c.url).searchParams.get('xnxq')==='2026-2027-1'));});
  await check('ZJU all-weeks option avoids partial repeated pages',async()=>{const {result,calls}=await execute('zju_post',{weekOptions:[{value:'-1',textContent:'全部周次'},...zOptions]});assert.equal(result.ok,true);assert.equal(calls.length,1);assert.equal(new URL(calls[0].url).searchParams.get('zc'),'-1');});
  await check('ZJU no week selector captures single full page',async()=>{const {result,calls}=await execute('zju_post',{weekOptions:null});assert.equal(result.ok,true);assert.equal(calls.length,0);assert.equal(JSON.parse(result.data).length,1);});
  await check('ZJU rejects semester switch without navigation',async()=>{assert.equal((await execute('zju_post',{fields:{xnxq:'2026-2027-1'},changeFields:{at:2,values:{xnxq:'2026-2027-2'}}})).result.ok,false);});
  for(const options of [{weekOptions:[]},{weekOptions:[{value:'0',textContent:'请选择'}]},{weekOptions:[{value:'2',textContent:'2'},{value:'2',textContent:'2'}]},{weekOptions:[{value:'9007199254740992',textContent:'bad'}]},{responseNoForm:true},{responsePassword:true},{responseWeekOptions:[{value:'3',textContent:'3'}]}])await check('ZJU rejects incomplete or changed weeks '+JSON.stringify(options),async()=>{assert.equal((await execute('zju_post',options)).result.ok,false);});
  for(const type of Object.keys(hosts)){
    await check(type+' nested logged-in frame',async()=>{assert.equal((await execute(type,{nested:true})).result.ok,true);});
    for(const failure of ['network','http','login'])await check(type+' '+failure,async()=>{const {result}=await execute(type,{failure});assert.equal(result.ok,false);assert.equal(result.data,undefined);});
    for(const options of [{hanging:true},{navigate:true},{finalUrl:'https://login.example.edu/'},{finalUrl:hosts[type]+'?unexpected=1'}])await check(type+' terminates safely '+JSON.stringify(options),async()=>{const {result}=await execute(type,options);assert.equal(result.ok,false);assert.equal(result.data,undefined);});
    if(type!=='zju_post')await check(type+' wrong host rejected',async()=>{const {result,calls}=await execute(type,{wrongHost:true});assert.equal(result.ok,false);assert.equal(calls.length,0);});
    await check(type+' same-origin credentials and no redirects',async()=>{const {result,calls}=await execute(type);assert.equal(result.ok,true);for(const {url,init} of calls){assert.equal(new URL(url).origin,new URL(hosts[type]).origin);assert.equal(init.credentials,'include');assert.equal(init.mode,'same-origin');assert.equal(init.redirect,'error');}});
  }
  console.log('Passed '+count+' four-school offline capture scenarios.');
})().catch(error=>{console.error(error);process.exitCode=1;});
