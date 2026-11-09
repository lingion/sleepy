const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const script = fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwBuaaCapture.kt'),'utf8').match(/const val FETCH_JS = """([\s\S]*?)"""/)[1];
const defaultUrl = 'https://byxt.buaa.edu.cn/jwapp/sys/homeapp/*default/index.do';
function node(value, text='') { return {value, textContent:text, getAttribute(){ return null; }}; }
function frame(url=defaultUrl, nodes=[node('2026-2027-1')], children=[]) {
  return {location:new URL(url), frames:children, document:{querySelectorAll(selector){assert.equal(selector,'#kbappTimeXQText,[name="termCode"],#termCode');return nodes;}}};
}
async function run(options={}) {
  const root = options.root || frame(options.url || defaultUrl, options.nodes===undefined ? [node('2026-2027-1')] : options.nodes);
  const calls=[], results=[], timers=new Map(); let timer=0, signal;
  const payload=options.payload===undefined ? {datas:{arrangedList:[{courseName:'数学',cellDetail:[]}],term:'2026-2027-1'},extra:{untouched:true}} : options.payload;
  const response = {ok:options.ok!==false,status:options.status||200,url:options.responseUrl||'',async text(){if(options.beforeBody)options.beforeBody(root);if(options.bodyError)throw Error('read failed');return options.text===undefined?JSON.stringify(payload):options.text;}};
  function fetch(url,config) {calls.push({url,config});signal=config.signal;if(options.networkError)return Promise.reject(Error('network failed'));if(options.pending)return new Promise(resolve=>{options.resolve=()=>resolve(response);});if(options.beforeResponse)options.beforeResponse(root);return Promise.resolve(response);}
  function connect(w) {w.fetch=fetch;for(const f of w.frames||[]){try{connect(f);}catch{}}} connect(root);
  root.__sleepyBridge={onWiseduResult(s){results.push(JSON.parse(s));}};
  const context={window:root,URL,AbortController,setTimeout(fn){timers.set(++timer,fn);return timer;},clearTimeout(id){timers.delete(id);}};
  vm.runInNewContext(script,context,{timeout:1000});
  await new Promise(resolve=>setImmediate(resolve));
  if(options.timeout){for(const fn of [...timers.values()])fn();await new Promise(resolve=>setImmediate(resolve));}
  return {calls,results,timers,signal,payload,async late(){options.resolve();await new Promise(resolve=>setImmediate(resolve));}};
}
const tests=[];
function test(name,fn){tests.push([name,fn]);}
function rejected(r){assert.equal(r.results.length,1);assert.equal(r.results[0].ok,false);assert.equal(r.results[0].format,'buaa');}
function noFetch(r){rejected(r);assert.equal(r.calls.length,0);}
test('selected code yields complete JSON and exact request',async()=>{
  const r=await run();assert.equal(r.results.length,1);assert.equal(r.results[0].ok,true);assert.deepEqual(JSON.parse(r.results[0].data),r.payload);assert.equal(r.results[0].xnxq,'2026-2027-1');assert.equal(r.calls[0].url,'https://byxt.buaa.edu.cn/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do');
  const o=r.calls[0].config;assert.equal(o.method,'POST');assert.equal(o.credentials,'include');assert.equal(o.mode,'same-origin');assert.equal(o.redirect,'error');assert.equal(o.body,'termCode=2026-2027-1&campusCode=&type=term');assert.equal(o.headers['Content-Type'],'application/x-www-form-urlencoded;charset=UTF-8');assert.equal(r.timers.size,0);
});
test('selected Chinese label is converted',async()=>{const r=await run({nodes:[node('', '2026-2027学年 第 二 学期')]});assert.equal(r.results[0].xnxq,'2026-2027-2');});
test('selected numeric label is converted',async()=>{const r=await run({nodes:[node('', '2026-2027学年第3学期')]});assert.equal(r.results[0].xnxq,'2026-2027-3');});
test('select value takes precedence over all option text',async()=>{const r=await run({nodes:[node('2026-2027-2','2024-2025-1 2026-2027-2')]});assert.equal(r.results[0].xnxq,'2026-2027-2');});
test('equivalent repeated current values are accepted',async()=>{const r=await run({nodes:[node('2026-2027-1'),node('','2026-2027学年第一学期')]});assert.equal(r.results[0].ok,true);});
test('explicit URL term is accepted',async()=>{const r=await run({url:defaultUrl+'?termCode=2026-2027-2',nodes:[]});assert.equal(r.results[0].xnxq,'2026-2027-2');});
test('URL and selected field conflict is rejected',async()=>noFetch(await run({url:defaultUrl+'?termCode=2026-2027-2'})));
test('conflicting fields are rejected',async()=>noFetch(await run({nodes:[node('2026-2027-1'),node('2026-2027-2')]})));
test('missing semester never uses stale default',async()=>noFetch(await run({nodes:[]})));
test('invalid semester is rejected',async()=>{for(const s of ['bad2026-2027-1','2026-2028-1','2026-2027-0','2026-2027-1&campusCode=1'])noFetch(await run({nodes:[node(s)]}));});
test('unrelated host is rejected',async()=>noFetch(await run({url:'https://notbuaa.edu.cn/jwapp/sys/homeapp/index.do'})));
test('login page without homeapp context is rejected',async()=>noFetch(await run({url:'https://byxt.buaa.edu.cn/login'})));
test('same-origin nested frame uses its selected semester',async()=>{const child=frame(defaultUrl,[node('2026-2027-2')]);const r=await run({root:frame('https://byxt.buaa.edu.cn/portal',[],[child])});assert.equal(r.results[0].xnxq,'2026-2027-2');});
test('different-origin frame is rejected',async()=>noFetch(await run({root:frame('https://byxt.buaa.edu.cn/portal',[],[frame('https://jwxt.buaa.edu.cn/jwapp/sys/homeapp/index.do')])})));
test('inaccessible frame is ignored',async()=>{const blocked={get location(){throw Error('Permission denied');},get frames(){throw Error('Permission denied');}};noFetch(await run({root:frame('https://byxt.buaa.edu.cn/portal',[],[blocked])}));});
test('multiple productive frames are rejected',async()=>noFetch(await run({root:frame('https://byxt.buaa.edu.cn/portal',[],[frame(),frame()])})));
test('deployment prefix is retained',async()=>{const r=await run({url:'https://byxt.buaa.edu.cn/gateway/jwapp/sys/homeapp/modules/kbapp/index.do'});assert.equal(r.calls[0].url,'https://byxt.buaa.edu.cn/gateway/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do');});
test('empty arrangedList is valid and complete',async()=>{const r=await run({payload:{datas:{arrangedList:[]}}});assert.equal(r.results[0].ok,true);assert.deepEqual(JSON.parse(r.results[0].data),{datas:{arrangedList:[]}});});
test('missing or malformed arrangedList is rejected',async()=>{for(const payload of [null,[],{}, {datas:{}},{datas:{arrangedList:{}}},{success:false,datas:{arrangedList:[]}}])rejected(await run({payload}));});
test('login HTML is rejected',async()=>rejected(await run({text:'<html>登入页面</html>'})));
test('HTTP failure is rejected',async()=>rejected(await run({ok:false,status:403})));
test('cross-origin response is rejected',async()=>rejected(await run({responseUrl:'https://sso.buaa.edu.cn/login'})));
test('network and body read errors are rejected',async()=>{rejected(await run({networkError:true}));rejected(await run({bodyError:true}));});
test('timeout aborts and late response cannot finish twice',async()=>{const r=await run({pending:true,timeout:true});rejected(r);assert.equal(r.signal.aborted,true);await r.late();assert.equal(r.results.length,1);});
test('semester changes before response are rejected',async()=>{rejected(await run({beforeResponse(w){w.document.querySelectorAll=()=>[node('2026-2027-2')];}}));});
test('semester changes while body is read are rejected',async()=>{rejected(await run({beforeBody(w){w.document.querySelectorAll=()=>[node('2026-2027-2')];}}));});
test('page changes before response are rejected',async()=>{rejected(await run({beforeResponse(w){w.location=new URL(defaultUrl+'?page=other');}}));});
test('page changes while body is read are rejected',async()=>{rejected(await run({beforeBody(w){w.location=new URL(defaultUrl+'?page=other');}}));});
test('current frame disappears during fetch is rejected',async()=>{const child=frame();rejected(await run({root:frame('https://byxt.buaa.edu.cn/portal',[],[child]),beforeResponse(w){w.frames=[];}}));});
test('new productive frame before response is rejected',async()=>{rejected(await run({beforeResponse(w){w.frames.push(frame());}}));});
test('new productive frame while reading body is rejected',async()=>{rejected(await run({beforeBody(w){w.frames.push(frame());}}));});
test('term field disappears while reading body is rejected',async()=>{rejected(await run({beforeBody(w){w.document.querySelectorAll=()=>[];}}));});
test('term fields conflict while reading body is rejected',async()=>{rejected(await run({beforeBody(w){w.document.querySelectorAll=()=>[node('2026-2027-1'),node('2026-2027-2')];}}));});
test('cross-origin navigation during fetch is rejected',async()=>{rejected(await run({beforeResponse(w){w.location=new URL('https://sso.buaa.edu.cn/login');}}));});
(async()=>{for(const [name,fn] of tests){await fn();console.log('PASS '+name);}console.log(tests.length+' tests passed');})().catch(error=>{console.error(error);process.exitCode=1;});
