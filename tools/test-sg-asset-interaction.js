// Runs the production shim in an isolated page with synthetic user replies.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwSgAssetBridge.kt', 'utf8');
const script = source.match(/private const val SG_ASSET_SHIM_JS = """([\s\S]*?)"""/)[1];
const tick = () => new Promise(resolve => setImmediate(resolve));
function page() {
  const requests = [], acknowledgements = [], results = [];
  const window = {__sleepyBridge:{
    onSgInteraction: json => requests.push(JSON.parse(json)),
    onSgInteractionAcknowledged: json => acknowledgements.push(JSON.parse(json)),
    onWiseduResult: json => results.push(JSON.parse(json)),
  }};
  vm.runInContext(script, vm.createContext({window}));
  window.SleepySgShim.install('session-a');
  return {window, api:window.shiguangBridgePromise, requests, acknowledgements, results};
}
async function run() {
  const failures = [];
  let passed = 0;
  async function check(name, test) {
    try { await test(); passed++; } catch (error) { failures.push(`${name}: ${error.message}`); }
  }
  await check('prompt waits for explicit input and keeps the request after validator error', async () => {
    const p = page(); let result = 'pending';
    p.window.validateYear = value => /^20\d\d$/.test(value) ? false : '请输入四位学年';
    const pending = p.api.showPrompt('学年','输入年份','2026','validateYear').then(value => { result = value; });
    await tick(); assert.equal(result,'pending'); assert.equal(p.requests.length,1);
    const request = p.requests[0];
    assert.equal(request.sessionId,'session-a'); assert.equal(request.kind,'prompt');
    p.window.SleepySgShim.respond('session-a',request.requestId,1,'bad');
    await tick(); assert.equal(result,'pending'); assert.equal(p.acknowledgements.at(-1).accepted,false);
    assert.equal(p.acknowledgements.at(-1).error,'请输入四位学年');
    p.window.SleepySgShim.respond('session-a',request.requestId,2,'2027');
    await pending; assert.equal(result,'2027'); assert.equal(p.acknowledgements.at(-1).accepted,true);
  });
  await check('only strict false passes a named validator', async () => {
    for (const validator of [()=>true,()=>undefined,()=>{throw Error('bad');},null]) {
      const p = page(); let settled = false;
      p.window.validate = validator;
      p.api.showPrompt('','','','validate').then(()=>{settled=true;}); await tick();
      assert.equal(p.requests.length,1);
      p.window.SleepySgShim.respond('session-a',p.requests[0].requestId,1,'text');
      await tick(); assert.equal(settled,false); assert.equal(p.acknowledgements.at(-1).accepted,false);
    }
  });
  await check('selection with no default waits and never selects item zero', async () => {
    const p = page(); let answer = 'pending';
    const pending = p.api.showSingleSelection('学期','["春","秋"]',-1).then(v=>{answer=v;});
    await tick(); assert.equal(answer,'pending'); assert.equal(p.requests[0].defaultSelectedIndex,-1);
    p.window.SleepySgShim.respond('session-a',p.requests[0].requestId,1,2);
    await tick(); assert.equal(answer,'pending'); assert.equal(p.acknowledgements.at(-1).accepted,false);
    p.window.SleepySgShim.respond('session-a',p.requests[0].requestId,2,1);
    await pending; assert.equal(answer,1);
  });
  await check('valid selection default also waits for confirmation', async () => {
    const p = page(); let answer = 'pending';
    p.api.showSingleSelection('学期','["春","秋"]',0).then(v=>{answer=v;});
    await tick(); assert.equal(answer,'pending'); assert.equal(p.requests[0].defaultSelectedIndex,0);
  });
  await check('cancellation returns the declared values without running validator', async () => {
    for (const kind of ['alert','prompt','selection']) {
      const p = page(); p.window.validator = () => { throw Error('must not run'); };
      const pending = kind === 'alert' ? p.api.showAlert('公告','内容','继续') :
        kind === 'prompt' ? p.api.showPrompt('','','','validator') : p.api.showSingleSelection('','["一"]',-1);
      await tick(); assert.equal(p.requests.length,1);
      p.window.SleepySgShim.respond('session-a',p.requests[0].requestId,1,kind === 'alert' ? false : null);
      assert.equal(await pending,kind === 'alert' ? false : null);
    }
  });
  await check('old sessions duplicate attempts and program characters remain inert', async () => {
    const p = page(); let answer = 'pending';
    const pending = p.api.showPrompt('','','').then(v=>{answer=v;}); await tick();
    const request = p.requests[0]; assert.ok(request);
    p.window.SleepySgShim.respond('old-session',request.requestId,1,'old');
    await tick(); assert.equal(answer,'pending');
    const text = '\"\n);window.injected=true;//';
    p.window.SleepySgShim.respond('session-a',request.requestId,1,text); await pending;
    assert.equal(answer,text); assert.equal(p.window.injected,undefined);
    p.window.SleepySgShim.respond('session-a',request.requestId,1,'duplicate');
    assert.equal(answer,text); assert.equal(p.acknowledgements.length,1);
  });
  await check('cancelled page cannot reinstall or let an old async task emit courses', async () => {
    const p = page(); const old = p.api;
    const pending = old.showPrompt('','',''); await tick();
    p.window.SleepySgShim.cancelSession('session-a'); assert.equal(await pending,null);
    assert.throws(()=>p.window.SleepySgShim.install('session-b'),/重新加载|reload/);
    await assert.rejects(old.saveImportedCourses('[]'),/结束|取消|session/);
    p.window.shiguangBridge.notifyTaskCompletion(); assert.equal(p.results.length,0);
  });
  for (const failure of failures) console.error(failure);
  assert.deepEqual(failures,[]);
  console.log(`SG_INTERACTION: ${passed} production bridge scenarios passed.`);
}
run().catch(error=>{console.error(error);process.exitCode=1;});
