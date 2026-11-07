// Runs the shipped WebView script against offline responses. No network requests.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const file = 'app/src/main/java/com/lingion/sleepy/data/jw/JwChaoxingShareCapture.kt';
assert(fs.existsSync(file), 'share capture must have a dedicated implementation');
const source = fs.readFileSync(file, 'utf8');
const script = source.match(/const val FETCH_JS = """([\s\S]*?)"""/)[1];
const kotlinPattern = source.match(/const val URL_PATTERN = """([^\n]+?)"""/)[1];
const jsPattern = script.match(/var shareUrlPattern = \/(.+)\/;/)[1].replaceAll('\\/', '/');
assert.equal(jsPattern, kotlinPattern, 'URL guard must match in Kotlin and WebView');
const screen = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt', 'utf8');
assert(screen.includes('JwChaoxingShareCapture.isShareUrl(wv.url.orEmpty())'), 'current share URL must dispatch independently of original school');
assert(screen.includes('evaluateFetchWithTimeout(wv, JwChaoxingShareCapture.FETCH_JS)'), 'capture button must execute collector');
assert(screen.includes('onHtmlCaptured(data, capturedSchool, periods, effectiveStartDate)'), 'bridge must forward share protocol to parser');

async function execute({href='https://kb.chaoxing.com/curriculum/share.html?id=sample%2Bvalue&term=2#week', weeks=3, failWeek=0, shape='array', hanging=false}={}) {
  const calls = [], callbacks = [], timers = new Map();
  let timerId = 0;
  const lessons = [
    {name:'实验',dayOfWeek:2,beginNumber:3,length:3,weeks:'1,3',teacherName:'张',location:'A',onlineLocation:'线上123'},
    {name:'周二课程',dayOfWeek:2,beginNumber:7,length:1,weeks:'2',teacherName:null,location:null,onlineLocation:null},
  ];
  const fetch = async (url, options) => {
    calls.push({url, options});
    if (hanging) return new Promise(()=>{});
    const week = Number(new URL(url).searchParams.get('week'));
    const response = shape === 'missing' ? {} : {data:{lessonArray:shape === 'null' ? null : shape === 'empty' ? [] : [lessons[week === 2 ? 1 : 0]]}};
    return {ok:week !== failWeek,status:week === failWeek ? 403 : 200,json:async()=>{
      if(shape === 'html') throw new SyntaxError('Unexpected HTML');
      return response;
    }};
  };
  const context = {
    location:new URL(href), URL, fetch, AbortController,
    document:{querySelector:key=>key === '.selectList' && weeks !== null ? {querySelectorAll:()=>Array.from({length:weeks},()=>({}))} : null},
    setTimeout:fn=>{const id=++timerId;timers.set(id,fn);return id},
    clearTimeout:id=>timers.delete(id),
    window:{__sleepyBridge:{onWiseduResult:value=>callbacks.push(JSON.parse(value))}},
  };
  vm.runInNewContext(script, context, {timeout:1000});
  for(let i=0;i<30 && !callbacks.length;i++) await new Promise(r=>setImmediate(r));
  if(hanging) for(const fn of [...timers.values()]) fn();
  for(let i=0;i<5;i++) await new Promise(r=>setImmediate(r));
  assert.equal(callbacks.length,1,'exactly one final callback');
  assert.equal(timers.size,0,'completed capture releases timer');
  return {result:callbacks[0],calls,lessons};
}

(async()=>{
  let r=await execute();
  assert.equal(r.result.ok,true);
  assert.equal(r.result.protocol,'chaoxing_share');
  assert.deepEqual(JSON.parse(r.result.data),[r.lessons[0],r.lessons[1],r.lessons[0]]);
  assert.deepEqual(r.calls.map(x=>new URL(x.url).searchParams.get('week')),['1','2','3']);
  for(const {url,options} of r.calls){
    const u=new URL(url);
    assert.equal(u.origin,'https://kb.chaoxing.com');
    assert.equal(u.pathname,'/curriculum/getOtherLessons');
    assert.equal(u.searchParams.get('id'),'sample+value');
    assert.equal(u.searchParams.get('term'),'2');
    assert.equal(u.hash,'');
    assert.equal(options.method,'GET');
    assert.equal(options.headers['X-Requested-With'],'XMLHttpRequest');
  }
  for(const shape of ['missing','null','html']){
    r=await execute({shape});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
  }
  r=await execute({failWeek:2});assert.equal(r.result.ok,false);assert.equal(r.calls.length,2);assert.equal(r.result.data,undefined);
  r=await execute({shape:'empty'});assert.equal(r.result.ok,true);assert.deepEqual(JSON.parse(r.result.data),[]);
  for(const weeks of [null,0]){r=await execute({weeks});assert.equal(r.result.ok,false);assert.equal(r.calls.length,0)}
  for(const href of ['https://kb.chaoxing.com.evil.test/curriculum/share.html?id=x','https://elsewhere.test/?next=https://kb.chaoxing.com/curriculum/share.html?id=x','https://kb.chaoxing.com/curriculum/share.html']){
    r=await execute({href});assert.equal(r.result.ok,false);assert.equal(r.calls.length,0);
  }
  r=await execute({hanging:true});assert.equal(r.result.ok,false);assert.equal(r.result.data,undefined);
  console.log('Chaoxing share fetch: 12 offline scenarios passed; URL, query, request order, fields, errors and timeout checked');
})().catch(error=>{console.error(error);process.exitCode=1});
