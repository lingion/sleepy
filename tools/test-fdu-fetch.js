// Executes the production Kotlin JavaScript with synthetic DOM/HTTP boundaries.
// It does not use a school account, a live Fudan session, or an Android WebView.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const file = process.env.FDU_CAPTURE_SOURCE ||
  'app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwFduCapture.kt';
assert.ok(fs.existsSync(file), 'the independent FDU production collector must exist');
const source = fs.readFileSync(file, 'utf8');
const match = source.match(/const val FETCH_JS = """([\s\S]*?)"""/);
assert.ok(match, 'run the actual FETCH_JS literal');
const script = match[1];
const ugOrigin = 'https://fdjwgl.fudan.edu.cn';
const ugPath = '/student/for-std/course-table';
const pgPath = '/yjsxkapp/sys/xsxkappfudan/xsxkCourse/loadKbxx.do';
const terms = [
  {id:310, name:'2025-2026学年1学期', startDate:'2025-08-31'},
  {id:505, name:'2025-2026学年2学期', startDate:'2026-03-01'},
  {id:900, name:'2026-2027学年1学期', startDate:'2026-08-30'},
];
const ugPayload = {studentTableVms:[{id:12345, activities:[
  {courseName:'离散周课程', teachers:['甲','乙'], room:'光华楼', weekday:2,
    startUnit:3, endUnit:5, weekIndexes:[1,3,9], lessonId:123, lessonCode:'CODE-1',
    unknown:{credit:'unproven', note:'保留引号"与反斜杠\\及中文'}},
  {courseName:'周日边界', teachers:[], room:'', weekday:7,
    startUnit:100, endUnit:101, weekIndexes:[1,2,20]},
]},{activities:[{courseName:'第二张表不能合并或改选'}]}], untouchedNumber:'RAW_NUMBER'};
const pgPayload = {results:[
  {KCMC:'研究生课程', JSXM:null, JASMC:'实验楼', ZCBH:'10100000000000000001', XQ:7, KSJCDM:31,
    unknown:{endUnit:'unproven', startDate:'2030-01-01'}},
], untouchedNumber:'RAW_NUMBER'};
function rawFor(payload) {
  return '\n ' + JSON.stringify(payload, null, 2).replace('"RAW_NUMBER"', '900719925474099312345') + '\n';
}
const ugRaw = rawFor(ugPayload), pgRaw = rawFor(pgPayload);

function makeDocument({rows=terms, selected='505', value, login=false, scriptRows=rows,
  noSelector=false, duplicates=[], scriptText, doubleQuoted=false}={}) {
  const controls=[];
  function control(chosen, current) {
    const options=rows.map(row=>({value:String(row.id), textContent:row.name,
      hasAttribute:name=>name==='selected' && (Array.isArray(chosen)?chosen:[chosen]).includes(String(row.id)),
    }));
    const resolved=current===undefined ? (Array.isArray(chosen)?chosen[0]:chosen) ||
      (options[0] && options[0].value) || '' : current;
    options.forEach(option=>option.selected=option.value===resolved);
    return {id:'allSemesters', name:'', value:resolved, options,
      get selectedOptions(){return this.options.filter(option=>option.value===this.value);}};
  }
  if(!noSelector)controls.push(control(selected,value));
  for(const other of duplicates)controls.push(control(other.selected,other.value));
  let scripts=[];
  if(scriptRows!==null){
    const json=JSON.stringify(scriptRows);
    const literal=doubleQuoted?JSON.stringify(json):"'"+json.replace(/\\/g,'\\\\').replace(/'/g,"\\'")+"'";
    scripts=[{textContent:scriptText===undefined?'var semesters = JSON.parse('+literal+');':scriptText}];
  }
  const doc={controls,scripts,login,
    querySelectorAll(selector){
      if(selector==='#allSemesters' || selector==='select#allSemesters')return controls;
      if(selector==='select')return controls;
      if(selector==='script')return scripts;
      return [];
    },
    querySelector(selector){return this.login && selector.includes('password')?{}:null;},
  };
  return doc;
}

async function execute(options={}) {
  const route=options.route || 'ug';
  const origin=route==='ug'?ugOrigin:options.pgOrigin || 'http://yjsxk.fudan.edu.cn';
  const location=new URL(options.location || origin+(route==='ug'?ugPath:'/yjsxkapp/sys/xsxkappfudan/*default/index.do'));
  const live=makeDocument({rows:options.rows || terms,
    selected:options.liveSelected===undefined?'505':options.liveSelected,
    value:options.liveValue, noSelector:options.noLive || route==='pg',
    duplicates:options.duplicates || [], login:options.currentLogin});
  const fetched=makeDocument({rows:options.rows || terms,
    selected:options.serverSelected===undefined?'900':options.serverSelected,
    scriptRows:options.scriptRows===undefined?options.rows || terms:options.scriptRows,
    scriptText:options.scriptText, doubleQuoted:options.doubleQuoted,
    login:options.failure==='index-login-document',
    noSelector:options.noServer});
  const indexHtml='<html><head><title>offline FDU course table</title></head><body></body></html>';
  const calls=[], results=[], timers=new Map(), late=[];let timerId=0;
  const win={location,document:live};
  win.__sleepyBridge={onWiseduResult:json=>results.push(JSON.parse(json))};
  const raw=options.raw===undefined?options.empty?rawFor(route==='ug'?{studentTableVms:[{activities:[]}]}:{results:[]}):
    route==='ug'?ugRaw:pgRaw:options.raw;
  win.fetch=async(url,init)=>{
    calls.push({url,init});
    const stage=route==='ug' && calls.length===1?'index':'json';
    const failure=options.failure || '';
    if(failure===stage+'-network')throw Error('synthetic network failure');
    let body=stage==='index'?indexHtml:raw;
    let finalUrl=url, contentType=stage==='index'?'text/html;charset=UTF-8':'application/json';
    if(failure===stage+'-login-url')finalUrl=origin+'/login';
    if(failure===stage+'-foreign')finalUrl='https://id.fudan.edu.cn/ac/auth';
    if(failure===stage+'-hostile')finalUrl='https://fdjwgl.fudan.edu.cn.evil.invalid/login';
    if(failure===stage+'-different-path')finalUrl=origin+'/unproven-endpoint';
    if(failure===stage+'-missing-url')finalUrl='';
    if(failure===stage+'-html'){body='<html><form><input type="password"></form></html>';contentType='text/html';}
    if(failure==='index-not-html' && stage==='index'){body='{}';contentType='application/json';}
    if(failure==='index-content-type' && stage==='index')contentType='application/json';
    if(failure==='json-not-json' && stage==='json')body='not JSON';
    function changePage(){
      if(stage!=='json')return;
      if(failure==='navigation')win.location=new URL(origin+'/home');
      if(failure==='selection-change' && live.controls[0])live.controls[0].value='900';
      if(failure==='selector-added')live.controls.push(makeDocument().controls[0]);
      if(failure==='current-login-during-request')live.login=true;
    }
    const response={ok:failure!==stage+'-http',status:failure===stage+'-http'?503:200,
      url:finalUrl, redirected:failure===stage+'-redirected',
      headers:{get:name=>name.toLowerCase()==='content-type'?contentType:
        name.toLowerCase()==='location' && failure===stage+'-location-header'?origin+'/login':null},
      async text(){
        if(failure===stage+'-body-timeout')return new Promise(resolve=>late.push(()=>{changePage();resolve(body);}));
        changePage();return body;
      }};
    if(failure===stage+'-request-timeout')return new Promise(resolve=>late.push(()=>resolve(response)));
    return response;
  };
  class DOMParser {
    parseFromString(body,mime){assert.equal(body,indexHtml);assert.equal(mime,'text/html');return fetched;}
  }
  class FixedDate extends Date {static now(){return 1794182400123;}}
  vm.runInNewContext(script,{window:win,URL,AbortController,DOMParser,Date:FixedDate,
    setTimeout:(fn,ms)=>{assert.equal(ms,18000,'bounded 18-second JS deadline');timers.set(++timerId,fn);return timerId;},
    clearTimeout:id=>timers.delete(id)},{timeout:1000});
  for(let i=0;i<40&&!results.length;i++)await new Promise(resolve=>setImmediate(resolve));
  if((options.failure || '').endsWith('-timeout')){
    for(const fn of [...timers.values()])fn();
    for(const resolve of late)resolve();
    for(let i=0;i<10;i++)await new Promise(resolve=>setImmediate(resolve));
  }
  assert.equal(results.length,1,'exactly one terminal callback, including late completion');
  assert.equal(timers.size,0,'deadline released');
  for(const call of calls)assert.equal(call.init.signal.aborted,true,'completion aborts outstanding work');
  return {result:results[0],calls,raw};
}

(async()=>{
  let count=0;const failures=[];
  async function check(name,run){count++;try{await run();console.log('PASS '+name);}catch(error){failures.push(name);console.error('FAIL '+name+': '+error.message);}}
  async function success(options={},semester='505',startDate='2026-03-02'){
    const r=await execute(options);assert.equal(r.result.ok,true,r.result.err);
    const pg=options.route==='pg';assert.equal(r.result.format,'fdu');
    assert.equal(r.result.variant,pg?'postgraduate':'undergraduate');
    assert.equal(r.result.data,r.raw,'raw JSON bytes including large numbers and formatting are preserved');
    assert.equal(r.result.semesterId,pg?'':semester);assert.equal(r.result.startDate,pg?'':startDate);
    assert.deepEqual(r.result.periods,[],'no unproven school clock is invented');
    assert.equal(r.calls.length,pg?1:2);
    for(const call of r.calls){assert.equal(call.init.method,'GET');assert.equal(call.init.credentials,'include');
      assert.equal(call.init.mode,'same-origin');assert.equal(call.init.redirect,'error','no hidden authentication hops');}
    const target=new URL(r.calls[r.calls.length-1].url);
    assert.equal(target.origin,pg?options.pgOrigin || 'http://yjsxk.fudan.edu.cn':ugOrigin);
    assert.equal(target.pathname,pg?pgPath:ugPath+'/semester/'+semester+'/print-data');
    assert.deepEqual([...target.searchParams],pg?[['_','1794182400123']]:[],
      'use only the proven route parameters, with no guessed student/semester/week filters');
    if(!pg){assert.equal(new URL(r.calls[0].url).pathname,ugPath);
      assert.equal(r.result.sourceStartDate,startDate?new Date(Date.parse(startDate)-86400000).toISOString().slice(0,10):
        options.expectedSourceStartDate || '');}
  }
  async function failure(options,maxCalls){const r=await execute(options);assert.equal(r.result.ok,false);
    assert.equal(typeof r.result.err,'string');assert.ok(r.result.err.length);assert.equal(r.result.data,undefined);
    if(maxCalls!==undefined)assert.equal(r.calls.length,maxCalls);}
  await check('live selected semester wins over server selection and maximum ID',()=>success());
  await check('live changed first value wins over stale selected attribute',()=>success({liveValue:'310'},'310','2025-09-01'));
  await check('live changed last value wins over stale selected attribute',()=>success({liveValue:'900'},'900','2026-08-31'));
  await check('explicit first option is not replaced by maximum ID',()=>success({liveSelected:'310'},'310','2025-09-01'));
  await check('server explicit selection is used without a live selector',()=>success({noLive:true},'900','2026-08-31'));
  await check('server explicit first option is usable',()=>success({noLive:true,serverSelected:'310'},'310','2025-09-01'));
  await check('one candidate is unambiguous without selected attributes',()=>success({rows:[terms[1]],liveSelected:null,serverSelected:null}));
  await check('nonfirst live value without selected attribute is explicit',()=>success({liveSelected:null,liveValue:'505'}));
  await check('matching duplicated controls do not change the semester',()=>success({duplicates:[{selected:'505'}]}));
  await check('empty undergraduate activities remain a successful raw result',()=>success({empty:true}));
  await check('double quoted JSON.parse literal is decoded without eval',()=>success({doubleQuoted:true}));
  await check('semester names with quotes and escapes do not damage metadata',()=>success({rows:terms.map(row=>({...row,name:row.name+"'\\\""}))}));
  await check('no semester script does not invent an opening date',()=>success({scriptRows:null},'505',''));
  await check('missing start date does not block courses',()=>success({rows:terms.map(row=>({...row,startDate:undefined}))},'505',''));
  for(const date of ['2026-03-02','2026-02-30','not-a-date','2026-03-01T00:00:00+08:00']){
    await check('unproved or invalid date is not silently moved to another week '+date,()=>success({
      rows:terms.map(row=>({...row,startDate:date})),expectedSourceStartDate:date},'505',''));
  }
  for(const [sunday,monday] of [['2026-12-27','2026-12-28'],['2023-12-31','2024-01-01'],['2024-02-25','2024-02-26']]){
    await check('Sunday metadata advances to following Monday across boundary '+sunday,()=>success({
      rows:terms.map(row=>({...row,startDate:sunday}))},'505',monday));
  }
  for(const pgOrigin of ['http://yjsxk.fudan.edu.cn','http://yjsxktest.fudan.sh.cn']){
    await check('current PG origin uses its own proven timestamp GET '+pgOrigin,()=>success({route:'pg',pgOrigin}));
    await check('empty PG results remain successful '+pgOrigin,()=>success({route:'pg',pgOrigin,empty:true}));
  }
  for(const options of [
    {noLive:true,serverSelected:null},{liveSelected:null,serverSelected:null},
    {rows:[],noLive:true,serverSelected:null},{noLive:true,noServer:true},
    {liveValue:'9999'},{liveValue:'0'},{liveValue:'-1'},{liveValue:'505/print-data'},
    {liveSelected:['310','505']},{duplicates:[{selected:'900'}]},
    {noLive:true,serverSelected:['310','505']},
    {scriptRows:[terms[0],terms[2]]},
    {scriptRows:[terms[1],{...terms[1],startDate:'2026-08-30'}]},
    {scriptRows:{}},{scriptText:"var semesters = JSON.parse('invalid');"},
    {scriptText:"var semesters = JSON.parse(window.malicious());"},
  ])await check('ambiguous or malformed semester is never guessed '+JSON.stringify(options),()=>failure(options));
  const badUg=[{},[],null,{success:false,...ugPayload},{code:401,...ugPayload},
    {studentTableVms:[]},{studentTableVms:{}},{studentTableVms:[null]},
    {studentTableVms:[{activities:{}}]}, {studentTableVm:{activities:[]}},
    {studentTableVms:[{activities:[]}],results:[]}];
  for(const raw of badUg)await check('undergraduate envelope rejection '+JSON.stringify(raw),()=>failure({raw:JSON.stringify(raw)},2));
  const badPg=[{},[],null,{results:{}},{results:[null]},{results:[{unrelated:'search results'}]},
    {success:false,...pgPayload},{code:401,...pgPayload},{...pgPayload,studentTableVms:[]}];
  for(const raw of badPg)await check('PG envelope rejection '+JSON.stringify(raw),()=>failure({route:'pg',raw:JSON.stringify(raw)},1));
  for(const route of ['ug','pg'])for(const stage of route==='ug'?['index','json']:['json']){
    for(const suffix of ['network','http','login-url','foreign','hostile','different-path','missing-url',
      'redirected','location-header','html','request-timeout','body-timeout']){
      await check(route+' rejects '+stage+'-'+suffix,()=>failure({route,failure:stage+'-'+suffix},
        route==='ug' && stage==='json'?2:1));
    }
  }
  for(const fail of ['index-not-html','index-content-type','index-login-document','json-not-json','navigation',
    'selection-change','selector-added','current-login-during-request']){
    await check('UG rejects '+fail,()=>failure({failure:fail}));
  }
  for(const fail of ['json-not-json','navigation','selector-added','current-login-during-request']){
    await check('PG rejects '+fail,()=>failure({route:'pg',failure:fail}));
  }
  for(const location of ['https://evil.invalid/', 'https://fdjwgl.fudan.edu.cn.evil.invalid/',
    'http://fdjwgl.fudan.edu.cn/student/for-std/course-table',
    'https://fdjwgl.fudan.edu.cn/login','https://fdjwgl.fudan.edu.cn/cas/login',
    'https://id.fudan.edu.cn/ac/auth','http://yjsxk.fudan.sh.cn/',
    'https://yjsxk.fudan.edu.cn/','http://yjsxk.fudan.edu.cn.evil.invalid/',
    'https://user@fdjwgl.fudan.edu.cn/student/for-std/course-table']){
    await check('manual login rejects unsupported or authentication origin '+location,()=>failure({location},0));
  }
  await check('current password form blocks all requests',()=>failure({currentLogin:true},0));
  assert.deepEqual(failures,[],'FDU collector scenarios failed');
  console.log(count+' offline FDU collector scenarios passed. No authenticated network or Android UI validation.');
})().catch(error=>{console.error(error);process.exitCode=1;});
