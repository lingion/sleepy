// Offline executable contract for the WebView fetch script. No school network calls.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt', 'utf8');
const start = source.indexOf('private const val WISEDU_FETCH_JS = """');
const end = source.indexOf('"""', start + 'private const val WISEDU_FETCH_JS = """'.length);
assert(start >= 0 && end > start, 'WISEDU_FETCH_JS raw string exists');
const base = source.slice(start + 'private const val WISEDU_FETCH_JS = """'.length, end);
assert(source.includes('school.type == JwProtocol.TYPE_SEU'));
assert(source.includes('evaluateFetchWithTimeout(wv, WISEDU_FETCH_JS.replace('));
const route = source.slice(source.indexOf('if (school.type == JwProtocol.TYPE_SEU)'), source.indexOf('if (school.type == JwProtocol.TYPE_NUIT)'));
const quoted = route.split(String.fromCharCode(10)).map(line => line.trim()).filter(line => line.startsWith(String.fromCharCode(34))).map(line => line.slice(1, line.lastIndexOf(String.fromCharCode(34))));
assert.equal(quoted.length, 4, "SEU route has exact replacement source strings");
assert(base.includes(quoted[0]), 'SEU host guard source must exist in shared script');
assert(base.includes(quoted[2]), 'SEU term selection source must exist in shared script');
let seu = base.replace(
  quoted[0], quoted[1],
);
assert.notEqual(seu, base, 'SEU host guard must be applied');
const rawBlock = route.match(/"""([\s\S]*?)"""/);
assert(rawBlock, 'SEU term override raw string exists');
seu = seu.replace(
  quoted[2], rawBlock[1],
);
assert(seu.includes(rawBlock[1]), 'SEU term selection must be applied');
assert.notEqual(seu, base.replace(quoted[0], quoted[1]), 'SEU term selector must modify script');

async function execute(script, {pathname='/', hostname='ehall.seu.edu.cn', schedule={datas:{xskcb:{rows:[]}}}, status=200, selected=null, frameSelected=null, termRows=[{DM:'2026-2027-1'}]}={}) {
  const calls = [];
  let bridge;
  const nodes = { '#dqxnxq2': selected };
  const document = {
    querySelector: key => nodes[key] || null,
    querySelectorAll: key => key === 'iframe' && frameSelected ? [{contentDocument:{querySelector: k => k === '#dqxnxq2' ? frameSelected : null}}] : [],
    body:{innerText:''},
  };
  const fetch = async (url, options={}) => {
    calls.push({url, options});
    if (url.includes('xskcb/xskcb.do')) return {ok:status >= 200 && status < 300, status, text: async()=>typeof schedule === 'string' ? schedule : JSON.stringify(schedule)};
    if (url.includes('dqxnxq.do')) return {ok:true, status:200, json:async()=>({datas:{dqxnxq:{rows:termRows}}})};
    return {ok:true,status:200};
  };
  const context = {location:{pathname,hostname}, document, fetch, window:{__sleepyBridge:{onWiseduResult: value => {bridge=JSON.parse(value)}}}};
  vm.runInNewContext(script, context, {timeout:1000});
  for (let i=0; i<15 && !bridge; i++) await new Promise(resolve=>setImmediate(resolve));
  assert(bridge, 'bridge callback required');
  return {bridge,calls};
}

(async()=>{
  let r=await execute(seu,{schedule:{datas:{}}});
  assert.equal(r.bridge.ok,false,'HTTP 200 missing rows must fail');
  for (const rows of [null, {}, 'login']) {
    r=await execute(seu,{schedule:{datas:{xskcb:{rows}}}});
    assert.equal(r.bridge.ok,false,'non-array rows must fail');
  }
  r=await execute(seu);
  assert.equal(r.bridge.ok,true,'valid empty rows is a valid empty semester');
  assert.deepEqual(JSON.parse(r.bridge.data).datas.xskcb.rows,[]);
  r=await execute(base,{pathname:'/jwapp/sys/wdkb/*default/index.do',hostname:'example.edu.cn',schedule:{datas:{xskcb:{rows:[{KCM:'test'}]}}}});
  assert.equal(r.bridge.ok,true,'shared WISEDU keeps valid nonempty JSON response');
  assert.equal(JSON.parse(r.bridge.data).datas.xskcb.rows.length,1);
  r=await execute(base,{pathname:'/jwapp/sys/wdkb/*default/index.do',hostname:'example.edu.cn',schedule:{datas:{xskcb:{rows:null}}}});
  assert.equal(r.bridge.ok,false,'shared WISEDU rejects invalid rows');
  r=await execute(seu,{schedule:'<html>login</html>'});
  assert.equal(r.bridge.ok,false,'login HTML must fail');
  r=await execute(seu,{status:403});
  assert.equal(r.bridge.ok,false,'HTTP 403 must fail');
  r=await execute(seu,{selected:{value:'2027-2028-1'},termRows:[{DM:'2026-2027-1'},{DM:'2027-2028-1'}]});
  assert.equal(r.bridge.ok,true,'selected school term succeeds');
  assert.equal(r.calls.at(-1).options.body,'XNXQDM=2027-2028-1');
  r=await execute(seu,{frameSelected:{value:'2027-2028-1'},termRows:[{DM:'2026-2027-1'},{DM:'2027-2028-1'}]});
  assert.equal(r.bridge.ok,true,'same-origin iframe term succeeds');
  assert.equal(r.calls.at(-1).options.body,'XNXQDM=2027-2028-1');
  r=await execute(base,{pathname:'/',hostname:'other.edu.cn'});
  assert.equal(r.bridge.ok,false,'shared WISEDU rejects unrelated root portal');
  console.log('SEU and WISEDU fetch executable contract: 12 cases passed');
})().catch(error=>{console.error(error);process.exitCode=1});
