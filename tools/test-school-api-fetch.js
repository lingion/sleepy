// Offline execution of the production JavaScript. No real WebView or school account is used.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwSchoolApiCapture.kt', 'utf8');
const template = source.match(/const val SCHOOL_API_FETCH_JS = """([\s\S]*?)"""/)[1];

async function execute(type, options = {}) {
  const calls = [], callbacks = [], timers = new Map();
  let timerId = 0, resolveLate;
  const calendar = {id:'term-a', allWeek:3, startTime:'2026-09-02'};
  const ccibeRow = {kcmc:'逐周课程', xqj:'三', jc:'1,3', jxdd:'A楼', jsxm:'张老师', qsz:1, jsz:20};
  const cppuRows = [
    {KCMC:'课程甲', JS:'李老师', DDMC:'实验楼', XF:2.5, SKRQ:'2026-09-02', JC:'2@上午'},
    {KCMC:'课程乙', JS:'王老师', DDMC:'主楼', XF:1, SKRQ:'2026-09-07', JC:'4@晚上'},
  ];
  const fetch = async (url, init = {}) => {
    calls.push({url, init});
    const u = new URL(url), week = u.searchParams.get('week');
    let payload;
    const failHere = type === 'ccibe' ? week === '3' : u.pathname === '/je/load';
    if (type === 'ccibe') {
      payload = {success:true, data:{calendar:{...calendar}, time:['08:00-08:45'], wdkb:week === '2' ? [] : [{...ccibeRow}]}};
      if (options.badCalendar && week === '') payload.data.calendar.allWeek = 0;
      if (options.failure === 'term' && failHere) payload.data.calendar.id = 'term-b';
    } else if (u.pathname.includes('getNow')) {
      payload = {data:options.termObject ? {values:{DM:'2026-2027-1'}} : '2026-2027-1'};
      if (options.noTerm) payload.data = null;
    } else if (u.pathname.includes('getClassFormDefault')) {
      payload = {jxzqsrq:'2026-09-02'};
      if (options.noDate) payload = {};
    } else {
      payload = {success:true, totalCount:2, rows:cppuRows};
      if (options.failure === 'truncated') payload.totalCount = 3;
    }
    if (failHere && options.failure === 'network') throw Error('offline');
    if (failHere && options.failure === 'business') payload.success = false;
    if (failHere && options.failure === 'shape') payload = {success:true, data:{}, rows:{}};
    const response = {ok:!(failHere && options.failure === 'http'), status:failHere && options.failure === 'http' ? 503 : 200,
      url:options.failure === 'redirect' && failHere ? 'https://login.invalid/' : url,
      text:async()=>failHere && options.failure === 'login' ? '<html>登录</html>' : JSON.stringify(payload)};
    if (options.hanging) return new Promise(resolve=>{resolveLate=()=>resolve(response);});
    return response;
  };
  const host = type === 'ccibe' ? 'my.vpn.ccibe.edu.cn' : 'jw.cppu.edu.cn';
  const selector = type === 'ccibe' ? '#rc_select_0_list [aria-selected="true"]' : '.je-grid-btnbar-queryStrategy';
  const label = options.next ? '下一学期' : '当前学期';
  const selected = type === 'ccibe' ? {textContent:options.selected || 'term-a'} : {parentElement:{
    textContent:label + (options.descendantNext ? '下一页' : ''),
    childNodes:[{nodeType:3,textContent:label}, {nodeType:1,textContent:options.descendantNext ? '下一页' : ''}],
  }};
  function frame(active) {
    return {location:new URL('https://' + (options.wrongHost ? 'unrelated.invalid' : host) + '/index.html'), frames:[], fetch,
      document:{querySelector:s=>active && s === selector ? selected : null, querySelectorAll:s=>s === 'input,select,textarea' ? [] : active && s === selector ? [selected] : []}};
  }
  const root = frame(!options.nested && !options.noControl);
  if (options.nested) root.frames = [{get location(){throw Error('cross origin');}}, frame(true)];
  root.__sleepyBridge = {onWiseduResult:text=>callbacks.push(JSON.parse(text))};
  vm.runInNewContext(template.replaceAll('__SCHOOL_TYPE__', type), {window:root, URL, FormData, AbortController,
    setTimeout:fn=>{const id=++timerId;timers.set(id,fn);return id;}, clearTimeout:id=>timers.delete(id)}, {timeout:1000});
  for (let i=0; i<40 && !callbacks.length; i++) await new Promise(r=>setImmediate(r));
  if (options.hanging) {
    for (const fn of [...timers.values()]) fn();
    if (resolveLate) resolveLate();
    for (let i=0; i<10; i++) await new Promise(r=>setImmediate(r));
  }
  assert.equal(callbacks.length, 1, 'exactly one terminal callback, including late responses');
  assert.equal(timers.size, 0, 'timer released');
  return {result:callbacks[0], calls};
}

(async()=>{
  const failures = [];
  let count = 0;
  const check = async (name, fn) => {count++;try {await fn();console.log('PASS '+name);} catch(e){failures.push(name);console.error('FAIL '+name+': '+e.message);}};
  const fixtures = {};
  for (const nested of [false,true]) await check('CCIBE all weeks and empty week, nested='+nested, async()=>{
    const r=await execute('ccibe',{nested});assert.equal(r.result.ok,true);
    const data=JSON.parse(r.result.data);assert.deepEqual(data.weeks.map(x=>x.week),[1,2,3]);
    assert.deepEqual(data.weeks.map(x=>x.data.wdkb.length),[1,0,1]);
    assert.equal(data.weeks[0].data.wdkb[0].qsz,1);assert.equal(data.weeks[0].data.wdkb[0].jsz,20);
    assert.equal(r.calls.length,5);assert.equal(data.calendar.startTime,'2026-09-02');
    assert.deepEqual(r.calls.map(x=>new URL(x.url).searchParams.get('week')),['','1','2','3','']);
    assert.ok(r.calls.every(x=>x.init.credentials==='include' && x.init.mode==='same-origin'));
    fixtures.ccibe=r.result.data;
  });
  for (const next of [false,true]) for (const termObject of [false,true]) await check('CPPU term='+next+' object='+termObject,async()=>{
    const r=await execute('cppu',{next,termObject,nested:true});assert.equal(r.result.ok,true);
    const data=JSON.parse(r.result.data);assert.equal(data.startDate,'2026-09-02');assert.equal(data.rows.length,2);
    assert.equal(new URL(r.calls[0].url).pathname,next?'/je/system/getNowNextXnxq':'/je/system/getNowXnxq');
    assert.equal(r.calls[1].init.body.get('xnxqCode'),'2026-2027-1');
    const fields=Object.fromEntries(r.calls[2].init.body.entries());
    assert.equal(fields.funcCode,'V_JWBZK_PK_XSKBZHCX');assert.equal(fields.funcId,'n3VPye8LJPUucKtVB1H');
    assert.equal(fields.limit,'100000');assert.equal(fields.start,'0');
    assert.equal(JSON.parse(fields.j_query).custom[0].value[0].value,'2026-2027-1');
    fixtures.cppu=r.result.data;
  });
  for (const type of ['ccibe','cppu']) {
    for (const failure of ['network','business','shape','http','login','redirect',type==='ccibe'?'term':'truncated']) {
      await check(type+' rejects '+failure,async()=>{const r=await execute(type,{failure});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);});
    }
    for (const option of ['wrongHost','noControl','hanging']) await check(type+' '+option,async()=>{
      const r=await execute(type,{[option]:true});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
      if(option!=='hanging') assert.equal(r.calls.length,0);
    });
  }
  await check('CCIBE resolves selected text to calendar id',async()=>{
    const r=await execute('ccibe',{selected:'2026-2027 第一学期'});assert.equal(r.result.ok,true);
    assert.equal(new URL(r.calls[0].url).searchParams.get('calendarId'),'2026-2027 第一学期');
    assert.equal(new URL(r.calls[1].url).searchParams.get('calendarId'),'term-a');
  });
  await check('CPPU excludes nested next-page labels from term selector',async()=>{
    const r=await execute('cppu',{descendantNext:true});assert.equal(r.result.ok,true);
    assert.equal(new URL(r.calls[0].url).pathname,'/je/system/getNowXnxq');
  });
  for (const option of ['badCalendar']) await check('CCIBE '+option,async()=>{
    const r=await execute('ccibe',{[option]:true});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
  });
  for (const option of ['noTerm','noDate']) await check('CPPU '+option,async()=>{
    const r=await execute('cppu',{[option]:true});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
  });
  assert.deepEqual(failures,[],'collector scenarios failed');
  const dir='app/src/test/resources/jw/fixtures/school-api';
  if(process.argv.includes('--write-fixtures')) {
    fs.mkdirSync(dir,{recursive:true});
    for (const [type, data] of Object.entries(fixtures)) {
      const p=dir+'/'+type+'-offline-envelope.json';
      if(fs.existsSync(p)) throw Error('Refusing to overwrite '+p);
      fs.writeFileSync(p,JSON.stringify(JSON.parse(data),null,2)+'\n',{flag:'wx'});
    }
  } else if(process.argv.includes('--check-fixtures')) {
    for (const [type, data] of Object.entries(fixtures)) assert.deepEqual(JSON.parse(fs.readFileSync(dir+'/'+type+'-offline-envelope.json','utf8')),JSON.parse(data));
  }
  console.log(count+' offline collector scenarios passed; no authenticated network validation.');
})().catch(e=>{console.error(e);process.exitCode=1;});
