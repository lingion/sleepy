const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/lingion/sleepy/data/jw/JwSuesCaptureScript.kt'), 'utf8');
const script = source.split('"""')[1].replaceAll("${'$'}", '$');
async function scenario(entries, body, options = {}) {
  const calls = [], results = [];
  const origin = 'https://jw.example.edu';
  const target = origin + '/student/for-std/course-table';
  const context = {
    URL, location: {origin, href: target},
    performance: {getEntriesByType: type => {assert.equal(type, 'resource'); return entries;}},
    window: {__sleepyBridge: {onWiseduResult: text => results.push(JSON.parse(text))}},
    fetch: async (url, init) => {calls.push({url, init}); return {
      ok: options.status ? options.status === 200 : true, status: options.status || 200,
      url: options.finalUrl || url, text: async () => typeof body === 'string' ? body : JSON.stringify(body)
    };}
  };
  await vm.runInNewContext(script, context);
  assert.equal(results.length, 1);
  return {calls, result: results[0]};
}
const entry = (semester, time) => ({name:'https://jw.example.edu/student/for-std/course-table/semester/' + semester + '/print-data?semesterId=' + semester, startTime:time});
(async () => {
  let count = 0;
  for (const payload of [{studentTableVm:{activities:[]}}, {studentTableVms:[{activities:[{courseName:'数学',weekIndexes:[1,9],weekday:2,startUnit:3,endUnit:4}]}]}]) {
    const {calls, result} = await scenario([entry(101,1)], payload);
    assert.equal(result.ok, true); assert.deepEqual(JSON.parse(result.data), payload);
    assert.deepEqual(result.periods, []); assert.equal(calls.length,1);
    assert.equal(calls[0].init.credentials,'include'); assert.equal(calls[0].init.method,'GET'); count++;
  }
  const latest = await scenario([entry(102,9),entry(101,1)], {studentTableVm:{activities:[]}});
  assert.equal(latest.calls[0].url, entry(102,9).name); count++;
  for (const entries of [[], [{name:'https://evil.example/print-data',startTime:1}], [{name:'https://jw.example.edu/student/print-data-other',startTime:1}]]) {
    const {calls,result} = await scenario(entries,{});
    assert.equal(calls.length,0); assert.equal(result.ok,false); count++;
  }
  for (const payload of ['<html>登录</html>',{}, {studentTableVm:{lessons:[]}}, {studentTableVms:[{activities:[]},{}]}]) {
    const {result} = await scenario([entry(101,1)],payload);
    assert.equal(result.ok,false); assert.equal(result.data,undefined); count++;
  }
  for (const options of [{status:401}, {finalUrl:'https://jw.example.edu/cas/login'}, {finalUrl:'https://id.example.edu/login'}]) {
    const {result} = await scenario([entry(101,1)],{studentTableVm:{activities:[]}},options);
    assert.equal(result.ok,false); count++;
  }
  console.log('Passed ' + count + ' SUES offline capture scenarios.');
})().catch(error => {console.error(error); process.exitCode=1;});
