// Executes the production collector with offline DOM and HTTP responses.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwCfNewFetchJs.kt', 'utf8');
const script = source.match(/const val CF_NEW_FETCH_JS = """([\s\S]*?)"""/)[1];

async function execute({htmlDepth = -1, full = false, failure = '', hanging = false, weekOptions = Array.from({length:22},(_,i)=>String(i+1))} = {}) {
  const calls = [], callbacks = [], timers = new Map();
  let nextTimer = 0;
  const html = '<html><span lay-tips="上课任务[123]">课程</span></html>';
  const row = zc => ({kcmc:'实验', teaxms:'张', jxcdmc:'A', xq:'3', ps:'01', pe:'02', zc});
  const fetch = async (url, options = {}) => {
    const params = new URLSearchParams(options.body);
    calls.push({url, options, params});
    if (hanging) return new Promise(() => {});
    if (url.includes('getDatesOfWeek')) return {ok:true, status:200, text:async()=>JSON.stringify([{xqmc:'1',rq:'2026-09-07'}])};
    const week = params.get('zc');
    let result = {code:0, data:week === '' ? (full ? [row('1'), row('3')] : [row('1')]) : [row(week)]};
    const failed = week === '7';
    if (failed && failure === 'business') result = {code:-1, data:[]};
    if (failed && failure === 'shape') result = {code:0, data:{rows:[]}};
    if (failed && failure === 'missing') result = {code:0};
    if (failed && failure === 'network') throw new Error('disconnected');
    return {ok:!(failed && failure === 'http'), status:failed && failure === 'http' ? 503 : 200, text:async()=>failed && failure === 'login' ? '<html>登录</html>' : JSON.stringify(result)};
  };
  function frame(path = '/new/student/xsgrkb/week.page', tip = false) {
    return {
      location:{pathname:path, search:'?xnxqdm=202601'}, frames:[], fetch,
      document:{documentElement:{outerHTML:tip ? html : '<html>NTSS</html>', innerHTML:''},
        querySelectorAll:selector=>selector === '[lay-tips]' && tip ? [{getAttribute:()=> '上课任务[123]'}] : [],
        querySelector:selector=>selector === '#zc' && weekOptions !== null ? {options:weekOptions.map(value=>({value}))} : null},
    };
  }
  const root = frame();
  if (htmlDepth >= 0) {
    root.location.pathname = '/portal';
    let current = root;
    for (let i=0;i<htmlDepth;i++) {const child=frame('/nested');current.frames=[child];current=child;}
    Object.assign(current.document, frame('/table', true).document);
    // An inaccessible frame must not prevent scanning the following sibling.
    if (htmlDepth > 0) root.frames.unshift({get location(){throw new Error('cross origin')}});
  }
  root.__sleepyBridge = {onWiseduResult:value=>callbacks.push(JSON.parse(value))};
  vm.runInNewContext(script, {window:root, URL, URLSearchParams, AbortController,
    setTimeout:fn=>{const id=++nextTimer;timers.set(id,fn);return id;},
    clearTimeout:id=>timers.delete(id)}, {timeout:1000});
  for(let i=0;i<40 && !callbacks.length;i++) await new Promise(r=>setImmediate(r));
  if(hanging) for(const fn of [...timers.values()]) fn();
  for(let i=0;i<5;i++) await new Promise(r=>setImmediate(r));
  assert.equal(callbacks.length, 1, 'exactly one terminal result');
  assert.equal(timers.size, 0, 'release timeout after completion');
  return {result:callbacks[0], calls, html};
}

(async()=>{
  const failures = [];
  const check = async (name, fn) => {try {await fn();console.log('PASS '+name);} catch(e){failures.push(name);console.error('FAIL '+name+': '+e.message);}};
  for(const htmlDepth of [0, 2, 8]) await check('HTML depth '+htmlDepth, async()=>{
    const r=await execute({htmlDepth});assert.equal(r.result.ok,true);assert.equal(r.result.data,r.html);assert.equal(r.calls.length,0);
  });
  await check('multiple returned weeks cannot prove a complete semester',async()=>{
    const r=await execute({full:true,weekOptions:['1','3','25']});assert.equal(r.result.ok,true);
    const data=JSON.parse(r.result.data);assert.deepEqual(data.weeks.map(x=>x.week),[1,3,25]);
    assert.equal(r.calls.some(x=>x.params.get('zc') === ''),false);
  });
  await check('explicit sparse weeks include late weeks without filling holes',async()=>{
    const r=await execute({weekOptions:['','0','3','1','25','3','30']});assert.equal(r.result.ok,true);
    assert.deepEqual(JSON.parse(r.result.data).weeks.map(x=>x.week),[1,3,25,30]);
    assert.deepEqual(JSON.parse(r.result.data).requestedWeeks,[1,3,25,30]);
    const last=r.calls.find(x=>x.params.get('zc') === '30');assert.equal(last.params.get('d1'),'2027-03-29 00:00:00');
  });
  for(const weekOptions of [null,[],['','0'],['1','2.5'],['1','-2'],['1','367'],['1','2abc']]) {
    await check('reject unavailable or invalid week list '+JSON.stringify(weekOptions),async()=>{
      const r=await execute({weekOptions});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
      assert.equal(r.calls.filter(x=>x.url.includes('getCalendarWeekDatas')).length,0);
    });
  }
  await check('all requested weeks and request dates',async()=>{
    const r=await execute();assert.equal(r.result.ok,true);
    const weeks=JSON.parse(r.result.data).weeks;assert.deepEqual(weeks.map(x=>x.week),Array.from({length:22},(_,i)=>i+1));
    const seventh=r.calls.find(x=>x.params.get('zc') === '7');assert.equal(seventh.params.get('d1'),'2026-10-19 00:00:00');
    assert.equal(seventh.options.credentials,'include');assert.equal(seventh.options.headers['X-Requested-With'],'XMLHttpRequest');
  });
  for(const failure of ['business','shape','missing','network','http','login']) await check('reject partial '+failure,async()=>{
    const r=await execute({failure});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
  });
  await check('capture deadline',async()=>{const r=await execute({hanging:true});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);});
  assert.deepEqual(failures,[],'collector scenarios failed');
  console.log('CF_NEW: 20 offline collector scenarios passed. No real WebView or school login was used.');
})().catch(e=>{console.error(e);process.exitCode=1});
