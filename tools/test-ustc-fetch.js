// Synthetic DOM and HTTP responses drive the actual production JS. No school account or Android UI is used.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const file = 'app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwUstcCapture.kt';
const source = fs.readFileSync(file, 'utf8');
const script = source.match(/const val FETCH_JS = """([\s\S]*?)"""/)[1];

const semesters = [
  {id:'310', text:'2026年春季学期'},
  {id:'420', text:'2026年夏季学期'},
  {id:'900', text:'2026年秋季学期'},
];
const payload = {
  studentTableVm: {name:'离线样本', code:'synthetic', activities:[
    {courseName:'离散周课程', weekday:2, startUnit:3, endUnit:5, weeksStr:'1,3,7-9周',
      startDate:'10:00', endDate:'12:10', teacherDeepVms:[{person:{nameZh:'教师甲'}}],
      room:'教学楼甲', customPlace:'', lessonCode:'CODE-1', lessonRemark:'完整保留'},
    {courseName:'后半学期', weekday:6, startUnit:11, endUnit:12, weeksStr:'16-20周',
      startDate:'19:00', endDate:'20:50', teachers:['教师乙'], room:'', customPlace:'实验楼'},
  ]},
  retainedMetadata:{endOfSemester:'2027-01-24', slots:[1,2,3,4,5,6,7,8,9,10,11,12,13]},
};

function documentFor({terms=semesters, selected='420', value, login=false, duplicate=false}={}) {
  function control() {
    const options = terms.map(term=>({value:term.id, textContent:term.text,
      hasAttribute:name=>name==='selected' && term.id===selected,
      getAttribute:name=>name==='value'?term.id:name==='selected'&&term.id===selected?'':null,
    }));
    const current = value === undefined ? selected || (terms[0] && terms[0].id) || '' : value;
    options.forEach(option=>option.selected=option.value===current);
    return {options, value:current, selectedIndex:options.findIndex(option=>option.selected)};
  }
  const controls = terms.length ? [control()] : [];
  if (duplicate) controls.push(control());
  return {
    querySelectorAll:selector=>selector==='select'?controls:[],
    querySelector:selector=>login && selector.includes('password')?{}:null,
  };
}

async function execute(options={}) {
  const calls=[], results=[], timers=new Map(); let timerId=0, late;
  const live = documentFor({terms:options.noLiveSelection?[]:options.terms||semesters,
    selected:options.liveSelected===undefined?'420':options.liveSelected,
    value:options.liveValue, login:options.currentLogin, duplicate:options.duplicate});
  const fetched = documentFor({terms:options.terms||semesters,
    selected:options.serverSelected===undefined?'900':options.serverSelected,
    login:options.failure==='index-login-html'});
  const indexHtml='<html><body><select id="semester">'+(options.terms||semesters).map(term=>
    '<option '+(term.id===(options.serverSelected===undefined?'900':options.serverSelected)?'selected ':'')+
    'value="'+term.id+'">'+term.text+'</option>').join('')+'</select></body></html>';
  const location=new URL(options.location||'https://jw.ustc.edu.cn/for-std/course-table/7654321');
  const win={location,document:live,frames:[]};
  win.__sleepyBridge={onWiseduResult:text=>results.push(JSON.parse(text))};
  win.fetch=async (url,init)=>{
    calls.push({url,init});
    const stage=calls.length===1?'index':'json';
    if(options.failure===stage+'-network')throw Error('synthetic network failure');
    let text=stage==='index'?indexHtml:JSON.stringify(options.empty?{studentTableVm:{activities:[]}}:payload);
    let responseUrl=stage==='index'?'https://jw.ustc.edu.cn/for-std/course-table/7654321':url;
    let contentType=stage==='index'?'text/html':'application/json';
    if(options.failure===stage+'-login-url')responseUrl='https://jw.ustc.edu.cn/ucas-sso/login';
    if(options.failure===stage+'-foreign')responseUrl='https://passport.ustc.edu.cn/login';
    if(options.failure===stage+'-html'){text='<html><form><input type="password"></form></html>';contentType='text/html';}
    if(options.failure==='index-not-html' && stage==='index'){text='{}';contentType='application/json';}
    if(options.failure==='json-not-json' && stage==='json')text='not json';
    if(options.failure==='json-shape' && stage==='json')text=JSON.stringify({studentTableVm:{activities:{}}});
    if(options.failure==='json-business' && stage==='json')text=JSON.stringify({...payload,success:false});
    if(options.failure==='student-id' && stage==='index')responseUrl='https://jw.ustc.edu.cn/for-std/course-table/abc123';
    if(options.failure==='student-url' && stage==='index')responseUrl='https://jw.ustc.edu.cn/other/7654321';
    if(options.failure==='missing-redirect' && stage==='index')responseUrl='';
    if(options.failure==='navigation' && stage==='json')win.location=new URL('https://jw.ustc.edu.cn/home');
    if(options.failure==='selection-change' && stage==='json')live.querySelectorAll('select')[0].value='900';
    const response={ok:options.failure!==stage+'-http',status:options.failure===stage+'-http'?503:200,
      url:responseUrl,headers:{get:name=>name.toLowerCase()==='content-type'?contentType:null},text:async()=>text};
    if(options.failure===stage+'-timeout')return new Promise(resolve=>late=()=>resolve(response));
    return response;
  };
  class DOMParser {
    parseFromString(text,mime){assert.equal(text,indexHtml);assert.equal(mime,'text/html');return fetched;}
  }
  vm.runInNewContext(script,{window:win,URL,AbortController,DOMParser,
    setTimeout:fn=>{timers.set(++timerId,fn);return timerId;},clearTimeout:id=>timers.delete(id)},{timeout:1000});
  for(let i=0;i<50&&!results.length;i++)await new Promise(resolve=>setImmediate(resolve));
  if(options.failure && options.failure.endsWith('-timeout')){
    for(const fn of [...timers.values()])fn();
    if(late)late();
    for(let i=0;i<10;i++)await new Promise(resolve=>setImmediate(resolve));
  }
  assert.equal(results.length,1,'one terminal callback');
  assert.equal(timers.size,0,'deadline released');
  return {result:results[0],calls};
}

(async()=>{
  const failures=[];let count=0;
  async function check(name,run){count++;try{await run();console.log('PASS '+name);}catch(error){failures.push(name);console.error('FAIL '+name+': '+error.message);}}
  async function successful(options,semester){
    const r=await execute(options);assert.equal(r.result.ok,true,r.result.err);
    assert.equal(r.result.format,'ustc');assert.equal(r.result.semesterId,semester);
    assert.equal(r.result.studentId,'7654321');assert.equal(r.calls.length,2);
    assert.equal(new URL(r.calls[0].url).pathname,'/for-std/course-table');
    const target=new URL(r.calls[1].url);
    assert.equal(target.pathname,'/for-std/course-table/semester/'+semester+'/print-data/7654321');
    assert.deepEqual([...target.searchParams],[['weekIndex','']],'all weeks, never the current week');
    for(const call of r.calls){assert.equal(new URL(call.url).origin,'https://jw.ustc.edu.cn');
      assert.equal(call.init.method,'GET');assert.equal(call.init.credentials,'include');assert.equal(call.init.mode,'same-origin');}
    assert.equal(r.calls[0].init.redirect,'follow','source redirects to numeric student id');
    assert.equal(r.calls[1].init.redirect,'error','JSON login redirects fail');
    assert.deepEqual(JSON.parse(r.result.data),options && options.empty?{studentTableVm:{activities:[]}}:payload);
  }
  await check('live selected semester overrides server selected and numeric maximum',()=>successful({},'420'));
  await check('changed live value overrides original selected attribute',()=>successful({liveValue:'310'},'310'));
  await check('server selected is used when current page has no semester selector',()=>successful({noLiveSelection:true},'900'));
  await check('explicit first selected semester is valid',()=>successful({liveSelected:'310'},'310'));
  await check('single available semester is unambiguous without selected',()=>successful({terms:[semesters[0]],liveSelected:null,serverSelected:null},'310'));
  await check('changed nonfirst live value is explicit without selected attribute',()=>successful({liveSelected:null,liveValue:'420'},'420'));
  await check('matching duplicate selectors are not conflicting',()=>successful({duplicate:true},'420'));
  await check('empty full semester remains valid JSON',()=>successful({empty:true},'420'));
  for(const options of [
    {noLiveSelection:true,serverSelected:null},
    {liveSelected:null,serverSelected:null},
    {terms:[],noLiveSelection:true},
  ])await check('ambiguous or missing semester never guesses '+JSON.stringify(options),async()=>{
    const r=await execute(options);assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);assert.equal(r.calls.length,1);
  });
  for(const stage of ['index','json'])for(const suffix of ['network','http','login-url','foreign','html','timeout']){
    const failure=stage+'-'+suffix;
    await check('rejects '+failure,async()=>{const r=await execute({failure});assert.equal(r.result.ok,false);
      assert.equal(r.result.data,undefined);assert.equal(r.calls.length,stage==='index'?1:2);});
  }
  for(const failure of ['index-not-html','index-login-html','student-id','student-url','missing-redirect','json-not-json','json-shape','json-business','navigation','selection-change']){
    await check('rejects '+failure,async()=>{const r=await execute({failure});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);});
  }
  for(const options of [
    {location:'https://evil.invalid/for-std/course-table/7654321'},
    {location:'https://jw.ustc.edu.cn.evil.invalid/for-std/course-table/7654321'},
    {location:'https://jw.ustc.edu.cn/ucas-sso/login'},
    {currentLogin:true},
  ])await check('manual login and exact school origin '+JSON.stringify(options),async()=>{
    const r=await execute(options);assert.equal(r.result.ok,false);assert.equal(r.calls.length,0);
  });
  assert.deepEqual(failures,[],'USTC collector scenarios failed');
  console.log(count+' offline USTC collector scenarios passed. No authenticated network or Android UI validation.');
})().catch(error=>{console.error(error);process.exitCode=1;});
