// Executes the production WebView script offline. No school account or network is used.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');

const root = path.resolve(__dirname, '..');
const sourceArg = process.argv.indexOf('--source');
const sourcePath = sourceArg >= 0 ? path.resolve(process.argv[sourceArg + 1]) : path.join(root,
  'app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwAhuCapture.kt');
const source = fs.readFileSync(sourcePath, 'utf8');
const marker = source.match(/const val (?:EAMS5_AHU_FETCH_JS|FETCH_JS)\s*=\s*"""/);
assert(marker, 'production raw JavaScript constant must exist');
const start = marker.index + marker[0].length;
const end = source.indexOf('"""', start);
assert(end > start, 'production raw JavaScript constant must end');
const script = source.slice(start, end);
assert(script.includes('__EAMS5_PREFIX__'), 'literal prefix replacement contract is retained');

const option = (id, attrs = '') => '<option value="' + id + '" ' + attrs + '>Semester ' + id + '</option>';
const select = (...options) => '<select name="allSemesters" id="allSemesters">' + options.join('') + '</select>';
const list = rows => '<script>var allSemesters = ' + JSON.stringify(rows) + ';</script>';
const tableHtml = select(option('500'), option('112', 'selected="selected"'));
const validData = '{ "studentTableVms" : [{ "activities" : [] }], "timeTableLayout": {"courseUnitList":[]} }\n';
const loginHtml = '<html><form id="casloginform"><input name="lt"><input name="execution"></form></html>';
const tests = [];
const test = (name, body) => tests.push({ name, body });

function liveSelect(value, selectedValues = [value]) {
  const options = selectedValues.map(id => ({value: id, selected: true}));
  return {value, options, selectedOptions: options, selectedIndex: options.length ? 0 : -1};
}

async function execute({html = tableHtml, live = null, prefix = '/student', hostname = 'jw.ahu.edu.cn',
  first = {}, second = {}, data = validData, beforeRead = null} = {}) {
  const calls = [];
  const callbacks = [];
  const origin = 'https://' + hostname;
  const location = {hostname, origin, protocol: 'https:', href: origin + prefix + '/for-std/course-table',
    pathname: prefix + '/for-std/course-table'};
  const controls = live ? [live] : [];
  const document = {body: {innerHTML: ''},querySelector: key => key === '#allSemesters' ? (controls[0] || null) : null,
    querySelectorAll: key => key === '#allSemesters' ? controls : [],
    getElementById: key => key === 'allSemesters' ? (controls[0] || null) : null};
  const fetch = async (url, options = {}) => {
    calls.push({url, options});
    assert(calls.length <= 2, 'only course-table and print-data may be fetched');
    const setup = calls.length === 1 ? first : second;
    if (setup.error) throw new Error(setup.error);
    const status = setup.status === undefined ? 200 : setup.status;
    const body = setup.body === undefined ? (calls.length === 1 ? html : data) : setup.body;
    return {status, ok: status >= 200 && status < 300, redirected: !!setup.redirected,
      url: setup.url === undefined ? new URL(url, origin).href : setup.url,
      headers: {get: name => name.toLowerCase() === 'location' ? (setup.location || '') : ''},
      text: async () => {if (beforeRead) await beforeRead({hop: calls.length,location,document,controls});return body;}};
  };
  const context = {location, document, fetch, URL, window: {location,
    __sleepyBridge: {onWiseduResult: value => callbacks.push(JSON.parse(value))}}};
  vm.runInNewContext(script.replaceAll('__EAMS5_PREFIX__', prefix), context, {timeout: 1000});
  for (let i = 0; i < 25 && callbacks.length === 0; i++) await new Promise(resolve => setImmediate(resolve));
  assert.equal(callbacks.length, 1, 'one terminal bridge callback is required');
  return {bridge: callbacks[0], calls};
}

async function expectSemester(id, setup) {
  const result = await execute(setup);
  assert.equal(result.bridge.ok, true, result.bridge.err);
  assert.equal(result.calls.length, 2, 'two GET hops are required');
  const prefix = setup?.prefix || '/student';
  assert.equal(result.calls[0].url, prefix + '/for-std/course-table');
  assert.equal(result.calls[1].url, prefix + '/for-std/course-table/semester/' + id +
    '/print-data?semesterId=' + id + '&hasExperiment=false');
  for (const {url, options} of result.calls) {
    assert(url.startsWith(prefix + '/'), 'requests remain relative to the current origin');
    assert.equal((options.method || 'GET').toUpperCase(), 'GET');
    assert.equal(options.credentials, 'include');
    assert(!url.includes('get-data') && !url.includes('grade') && !url.includes('dataId='));
  }
  return result;
}

async function expectFailure(setup, hops) {
  const result = await execute(setup);
  assert.equal(result.bridge.ok, false, 'response must fail instead of importing an unintended semester or payload');
  assert.equal(typeof result.bridge.err, 'string');
  assert(result.bridge.err.length > 0, 'failure has an explanation');
  assert.equal(result.calls.length, hops, 'failure stops further requests');
  return result;
}

test('live selection outranks fetched selected option, current object and future maximum', () =>
  expectSemester('111', {live: liveSelect('111'), html: tableHtml + '<script>currentSemester={id:113};</script>'}));
test('live select value works when selectedOptions is unavailable', () =>
  expectSemester('111', {live: {value: '111'}, html: tableHtml}));
test('live selected option works when value is unavailable', () =>
  expectSemester('111', {live: {selectedOptions: [{value: '111'}]}, html: tableHtml}));
test('live options selected property is honored', () =>
  expectSemester('111', {live: {options: [{value: '500', selected: false}, {value: '111', selected: true}]}}));
test('empty live selection permits fetched explicit selection', () =>
  expectSemester('112', {live: liveSelect('', [])}));
test('multiple live selected options fail', () => expectFailure({live: liveSelect('111', ['111', '112'])}, 1));
test('invalid live selected id fails', () => expectFailure({live: liveSelect('112&semesterId=500')}, 1));
test('selected attribute after value outranks maximum and currentSemester', () =>
  expectSemester('112', {html: tableHtml + '<script>currentSemester={id:113};</script>'}));
test('selected attribute before value is recognized', () =>
  expectSemester('112', {html: select(option('500'), '<option selected value="112">Current</option>')}));
test('selected option outside allSemesters does not choose a semester', () =>
  expectFailure({html: select(option('111'), option('112')) + '<select><option selected value="500">Other</option></select>'}, 1));
test('two fetched selected options fail', () =>
  expectFailure({html: select(option('111', 'selected'), option('112', 'selected'))}, 1));
test('invalid fetched selected id fails', () =>
  expectFailure({html: select(option('112x', 'selected'), option('113'))}, 1));

for (const [name, assignment] of [
  ['JSON object', 'currentSemester = {"id":112,"name":"old"};'],
  ['JS object literal', "currentSemester = {id: '112', name: 'old'};"],
  ['semesterId object field', 'currentSemester = {"semesterId":"112"};'],
  ['currentSemesterId number', 'currentSemesterId = 112;'],
  ['currentSemesterId string', 'currentSemesterId: "112",'],
  ['JSON.parse single quoted string', 'currentSemester = JSON.parse(\'{"id":112,"name":"old"}\');'],
  ['JSON.parse escaped double quoted string', 'currentSemester = JSON.parse("{\\"id\\":112,\\"name\\":\\"old\\"}");'],
  ['JSON.parse escaped apostrophe', 'currentSemester = JSON.parse(\'{"id":112,"name":"O\\\'Brien"}\');'],
]) test(name + ' outranks an unselected multi-semester list', () =>
  expectSemester('112', {html: select(option('500'), option('111')) + '<script>' + assignment + '</script>'}));

test('current object outranks a marked list', () => expectSemester('112', {html:
  '<script>currentSemester={id:112};</script>' + list([{id: 500, current: true}, {id: 111}])}));
test('nested object id cannot replace the current semester id', () => expectSemester('112', {html:
  '<script>currentSemester={details:{id:500}, id:112, name:"a,b}c"};</script>'}));
test('selected live semester outranks malformed lower priority metadata', () => expectSemester('112', {live: liveSelect('112'),
  html: '<script>currentSemester=JSON.parse(\'broken\'); allSemesters=[broken];</script>'}));
test('conflicting current object and currentSemesterId fail', () => expectFailure({html:
  '<script>currentSemester={id:112}; currentSemesterId=113;</script>' + list([{id: 500}])}, 1));
for (const marker of [{isCurrentSemester: true}, {isCurrent: true}, {current: true}, {status: 'CURRENT'}]) {
  test('exactly one list marker ' + JSON.stringify(marker), () =>
    expectSemester('112', {html: list([{id: 500}, {id: 112, ...marker}, {id: 111}])}));
}
test('two current list entries fail', () => expectFailure({html: list([{id: 112, current: true}, {id: 113, current: true}])}, 1));
test('duplicate marked list entries still fail', () => expectFailure({html: list([{id: 112, current: true}, {id: 112, current: true}])}, 1));
test('unmarked array does not choose its first or maximum', () => expectFailure({html: list([{id: 500}, {id: 112}])}, 1));
test('unselected HTML list does not choose its first or maximum', () =>
  expectFailure({html: select(option('500'), option('112'))}, 1));
test('singleton HTML semester succeeds', () => expectSemester('112', {html: select(option('112'))}));
test('singleton JSON semester succeeds', () => expectSemester('112', {html: list([{id: 112}])}));
test('singleton semesterId field succeeds', () => expectSemester('112', {html: list([{semesterId: '112'}])}));
test('missing semesters fail', () => expectFailure({html: '<html>course table</html>'}, 1));
test('invalid semester array JSON fails', () => expectFailure({html: '<script>allSemesters=[{id:112}];</script>'}, 1));
for (const id of ['112x', '-1', '0', '1.5', '112/print-data', '']) {
  test('invalid semester identifier ' + JSON.stringify(id), () => expectFailure({html: list([{id}])}, 1));
}
for (const prefix of ['/student', '/eams5-student']) {
  test('deployment prefix ' + prefix, () => expectSemester('112', {prefix}));
}
test('unrelated host fails before fetching', () => expectFailure({hostname: 'example.edu.cn'}, 0));
test('suffix lookalike host fails before fetching', () => expectFailure({hostname: 'jw.ahu.edu.cn.attacker.example'}, 0));

for (const hop of ['first', 'second']) {
  const hops = hop === 'first' ? 1 : 2;
  for (const status of [400, 401, 403, 404, 500]) {
    test(hop + ' hop rejects HTTP ' + status, () => expectFailure({[hop]: {status}}, hops));
  }
  for (const [name, response] of [
    ['redirect to CAS', {status: 302, location: 'https://one.ahu.edu.cn/cas/login'}],
    ['redirect to local login', {status: 302, location: '/student/login'}],
    ['final CAS URL', {redirected: true, url: 'https://one.ahu.edu.cn/cas/login'}],
    ['final local login URL', {url: 'https://jw.ahu.edu.cn/student/login?service=course-table'}],
    ['HTTP 200 login form', {body: loginHtml}],
    ['HTTP 200 password form', {body: '<html><form action="/student/login"><input name="password" type="password"></form></html>'}],
    ['HTTP 200 session message', {body: '<html>会话已过期，请重新登录</html>'}],
    ['foreign final origin', {url: 'https://attacker.example/course-table'}],
    ['network failure', {error: 'offline'}],
  ]) test(hop + ' hop rejects ' + name, () => expectFailure({[hop]: response}, hops));
}

for (const data of ['', '<html>not JSON</html>', '{bad', 'null', '[]', '{}', '{"error":"expired"}',
  '{"studentTableVms":{}}', '{"studentTableVms":[{}]}', '{"studentTableVms":[null]}',
  '{"studentTableVms":[{"activities":null}]}', '{"studentTableVms":[{"activities":{}}]}',
  '{"studentTableVm":[]}', '{"studentTableVm":{"activities":"bad"}}']) {
  test('print-data rejects invalid container ' + data, () => expectFailure({data}, 2));
}
for (const data of ['{"studentTableVms":[]}', '{"studentTableVms":[{"activities":[]}]}',
  '{"studentTableVm":{"activities":[]}}',
  '{"studentTableVms":[{"activities":[]},{"activities":[]}]}']) {
  test('print-data accepts valid empty tables ' + data, () => expectSemester('112', {data}));
}
test('JSON course text containing login HTML is left for Kotlin', async () => {
  const data = JSON.stringify({studentTableVms: [{activities: [{courseName: '统一身份认证',
    lessonRemark: '<html>请重新登录</html>'}]}]});
  const {bridge} = await expectSemester('112', {data});
  assert.equal(bridge.data, data);
});
test('raw JSON and every course field are forwarded unchanged', async () => {
  const data = ' {\n "studentTableVms":[{"activities":[{"courseName":"测试\\u8bfe",' +
    '"weekday":4,"startUnit":101,"endUnit":103,"weekIndexes":[1,3],"unknown":{"id":"<&>"}}]}],' +
    '"timeTableLayout":{"courseUnitList":[{"indexNo":13,"startTime":2100,"endTime":2145}]},' +
    '"opaqueNumber":9007199254740993\n}\n';
  const {bridge} = await expectSemester('112', {data});
  assert.equal(bridge.data, data, 'do not parse and reserialize or decode course fields');
  assert.deepEqual(bridge.periods, [], 'Kotlin resolves source periods from the original JSON');
});

for (const hop of [1,2]) {
  test('selection changed while reading hop ' + hop + ' rejects stale data', async () => {
    const live = liveSelect('111');
    await expectFailure({live,beforeRead: state => {
      if (state.hop === hop) {live.value='112';live.selectedOptions[0].value='112';}
    }},2);
  });
  test('new live control while reading hop ' + hop + ' rejects server selection', () =>
    expectFailure({beforeRead: state => {if(state.hop===hop)state.controls.push(liveSelect('111'));}},2));
  test('navigation while reading hop ' + hop + ' rejects stale data', () =>
    expectFailure({live:liveSelect('111'),beforeRead: state => {
      if(state.hop===hop) {state.location.href+='?semesterId=112';state.location.pathname+='/other';}
    }},2));
}
test('selected options change is rejected even when the value stays unchanged', () => {
  const live=liveSelect('111');
  return expectFailure({live,beforeRead: state => {if(state.hop===2)live.selectedOptions[0].value='112';}},2);
});
test('value change is rejected even when selected options stay unchanged', () => {
  const live=liveSelect('111');
  return expectFailure({live,beforeRead: state => {if(state.hop===2)live.value='112';}},2);
});
test('a live selector removed during data read rejects stale data', () =>
  expectFailure({live:liveSelect('111'),beforeRead: state => {if(state.hop===2)state.controls.length=0;}},2));
test('an additional selector during data read rejects stale data', () =>
  expectFailure({live:liveSelect('111'),beforeRead: state => {if(state.hop===2)state.controls.push(liveSelect('112'));}},2));
test('a replacement selector during data read rejects stale data', () =>
  expectFailure({live:liveSelect('111'),beforeRead: state => {if(state.hop===2)state.controls[0]=liveSelect('111');}},2));
test('page login while reading data rejects stale data', () =>
  expectFailure({live:liveSelect('111'),beforeRead: state => {if(state.hop===2)state.document.body.innerHTML=loginHtml;}},2));
test('stable live state succeeds through both response reads', () =>
  expectSemester('111',{live:liveSelect('111'),beforeRead:()=>{}}));
test('stable absence of live selectors preserves server selection', () =>
  expectSemester('112',{beforeRead:()=>{}}));

(async () => {
  console.log('AHU production source: ' + sourcePath);
  console.log('JavaScript SHA-256: ' + crypto.createHash('sha256').update(script).digest('hex'));
  let failures = 0;
  for (const {name, body} of tests) {
    try { await body(); console.log('PASS ' + name); }
    catch (error) { failures++; console.error('FAIL ' + name + '\n  ' + error.message); }
  }
  console.log('AHU fetch executable contract: ' + tests.length + ' cases, ' + failures + ' failures');
  process.exitCode = failures ? 1 : 0;
})().catch(error => {console.error(error); process.exitCode = 1;});
