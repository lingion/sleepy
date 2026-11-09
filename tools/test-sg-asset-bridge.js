// Execute the production bridge against the upstream save/complete contract.
// The inputs are synthetic; no school account or device WebView is used.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwSgAssetBridge.kt', 'utf8');
const script = source.match(/private const val SG_ASSET_SHIM_JS = """([\s\S]*?)"""/)[1];
const courses = JSON.stringify([{name:'离散数学', teacher:'王老师', position:'教学楼301', day:3, startSection:5, endSection:6, weeks:[1,3,7]}]);
const slots = [{number:1,startTime:'08:00',endTime:'08:45'}, {number:2,startTime:'08:55',endTime:'09:40'}];

function environment() {
  const results = [];
  const window = {__sleepyBridge:{onWiseduResult:value=>results.push(JSON.parse(value))}};
  const context = vm.createContext({window});
  vm.runInContext(script, context);
  window.SleepySgShim.install('capture-test');
  return {window, results, api:window.shiguangBridgePromise};
}

(async()=>{
  let passed = 0;
  const failures = [];
  async function check(name, run) {
    try { await run(); passed++; }
    catch (error) { failures.push(name + ': ' + error.message); }
  }
  await check('SUDA and HAUE order keeps metadata saved after courses', async()=>{
    const {window,results,api} = environment();
    await api.saveImportedCourses(courses);
    assert.equal(results.length,0,'course submission is not the end of the adapter');
    await api.savePresetTimeSlots(JSON.stringify(slots));
    await api.saveCourseConfig(JSON.stringify({semesterStartDate:'2026-09-07',semesterTotalWeeks:24}));
    window.shiguangBridge.notifyTaskCompletion();
    assert.equal(results.length,1);
    assert.equal(results[0].data,courses);
    assert.deepEqual(results[0].periods,slots.map(s=>({node:s.number,start:s.startTime,end:s.endTime})));
    assert.equal(results[0].startDate,'2026-09-07');
    assert.deepEqual(results[0].sgConfig,{semesterStartDate:'2026-09-07',semesterTotalWeeks:24});
  });
  await check('metadata before courses remains compatible', async()=>{
    const {window,results,api} = environment();
    await api.savePresetTimeSlots(JSON.stringify(slots));
    await api.saveCourseConfig(JSON.stringify({semesterStartDate:'2026-08-31'}));
    await api.saveImportedCourses(courses);
    window.shiguangBridge.notifyTaskCompletion();
    assert.equal(results.length,1);
    assert.equal(results[0].data,courses);
    assert.equal(results[0].periods.length,2);
    assert.equal(results[0].startDate,'2026-08-31');
  });
  await check('duplicate completion sends exactly one result', async()=>{
    const {window,results,api} = environment();
    await api.saveImportedCourses(courses);
    window.shiguangBridge.notifyTaskCompletion();
    window.shiguangBridge.notifyTaskCompletion();
    assert.equal(results.length,1);
  });
  await check('no submitted courses is one explicit failure', async()=>{
    const {window,results} = environment();
    window.shiguangBridge.notifyTaskCompletion();
    window.shiguangBridge.notifyTaskCompletion();
    assert.equal(results.length,1);
    assert.equal(results[0].ok,false);
    assert.equal(results[0].data,undefined);
  });
  await check('another document starts a capture without stale metadata', async()=>{
    const {window,results,api} = environment();
    await api.savePresetTimeSlots(JSON.stringify(slots));
    await api.saveCourseConfig(JSON.stringify({semesterStartDate:'2026-09-07'}));
    await api.saveImportedCourses(courses);
    window.shiguangBridge.notifyTaskCompletion();
    assert.throws(()=>window.SleepySgShim.install('capture-next'),/重新加载/);
    const next = environment();
    const nextCourses = courses.replace('离散数学','线性代数');
    await next.api.saveImportedCourses(nextCourses);
    next.window.shiguangBridge.notifyTaskCompletion();
    assert.equal(results.length,1);
    assert.equal(next.results[0].data,nextCourses);
    assert.deepEqual(next.results[0].periods,[]);
    assert.equal(next.results[0].startDate,'');
    assert.deepEqual(next.results[0].sgConfig,{});
  });
  await check('install after a successful capture still reports missing courses', async()=>{
    const {window,results,api} = environment();
    await api.saveImportedCourses(courses);
    window.shiguangBridge.notifyTaskCompletion();
    const next = environment();
    next.window.shiguangBridge.notifyTaskCompletion();
    assert.equal(results.length,1);
    assert.equal(next.results.length,1);
    assert.equal(next.results[0].ok,false);
  });
  await check('partial configuration updates preserve explicit semester fields', async()=>{
    const {window,results,api} = environment();
    await api.saveCourseConfig(JSON.stringify({semesterStartDate:'2026-09-07'}));
    await api.saveImportedCourses(courses);
    await api.saveCourseConfig(JSON.stringify({semesterTotalWeeks:24}));
    window.shiguangBridge.notifyTaskCompletion();
    assert.deepEqual(results[0].sgConfig,{semesterStartDate:'2026-09-07',semesterTotalWeeks:24});
    assert.equal(results[0].startDate,'2026-09-07');
  });
  await check('an explicit null start date does not reuse a previous value', async()=>{
    const {window,results,api} = environment();
    await api.saveCourseConfig(JSON.stringify({semesterStartDate:'2026-09-07'}));
    await api.saveCourseConfig(JSON.stringify({semesterStartDate:null}));
    await api.saveImportedCourses(courses);
    window.shiguangBridge.notifyTaskCompletion();
    assert.equal(results[0].sgConfig.semesterStartDate,null);
    assert.equal(results[0].startDate,'');
  });
  await check('invalid preset clocks reject and cannot be swallowed into a successful import', async()=>{
    for (const invalid of ['broken','{}','[]',JSON.stringify([{...slots[0],number:2}]),
      JSON.stringify([{...slots[0],startTime:'25:00'}]),JSON.stringify([{...slots[0],endTime:'07:00'}]),
      JSON.stringify([slots[0],{...slots[1],startTime:'08:40'}])]) {
      const {window,results,api} = environment();
      await api.saveImportedCourses(courses);
      await assert.rejects(api.savePresetTimeSlots(invalid));
      window.shiguangBridge.notifyTaskCompletion();
      assert.equal(results.length,1); assert.equal(results[0].ok,false); assert.equal(results[0].data,undefined);
    }
  });
  await check('invalid configuration does not silently use defaults', async()=>{
    for (const invalid of ['broken','[]','null','{"semesterTotalWeeks":0}',
      '{"semesterTotalWeeks":20.5}','{"semesterStartDate":"2026-02-30"}',
      '{"defaultClassDuration":0}','{"defaultBreakDuration":-1}','{"firstDayOfWeek":8}']) {
      const {window,results,api} = environment();
      await api.saveImportedCourses(courses);
      await assert.rejects(api.saveCourseConfig(invalid));
      window.shiguangBridge.notifyTaskCompletion();
      assert.equal(results.length,1);assert.equal(results[0].ok,false);assert.equal(results[0].data,undefined);
    }
  });
  await check('completion cannot bypass an outstanding user question', async()=>{
    const {window,results,api} = environment();
    window.__sleepyBridge.onSgInteraction = ()=>{};
    const pending = api.showPrompt('学期','选择后继续','');
    await api.saveImportedCourses(courses);
    window.shiguangBridge.notifyTaskCompletion();
    assert.equal(await pending,null);assert.equal(results.length,1);assert.equal(results[0].ok,false);
    assert.equal(results[0].data,undefined);
  });
  await check('date-dependent schedules never report success before the persistence path exists', async()=>{
    const {window,results,api} = environment();
    await api.savePresetTimeSlots(JSON.stringify(slots));
    await api.saveImportedCourses(courses);
    await assert.rejects(api.saveComboSchedule(JSON.stringify({name:'季节作息',publicSchedules:[]})));
    window.shiguangBridge.notifyTaskCompletion();
    assert.equal(results.length,1);assert.equal(results[0].ok,false);assert.equal(results[0].data,undefined);
  });
  for (const failure of failures) console.error(failure);
  assert.deepEqual(failures,[],'SG asset bridge contract failed');
  console.log(`SG_ASSET: ${passed} offline bridge scenarios passed.`);
})().catch(error=>{console.error(error);process.exitCode=1;});
