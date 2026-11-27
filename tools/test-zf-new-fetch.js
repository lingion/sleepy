const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwZfNewFetchJs.kt', 'utf8');
const script = source.match(/ZF_NEW_FETCH_JS = """([\s\S]*?)"""/)[1];
const timetable = '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default';
const endpoint = '/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151';
const response = JSON.parse(fs.readFileSync('app/src/test/resources/zf-new/kblist_qdc.json', 'utf8'));
const fields = {'#xnm': '2026', '#xqm': '3', '#xsdm': '', '#kclbdm': '', '#kclxdm': ''};
const pageRequest = {
  source: 'xhr',
  frame: 'top',
  method: 'POST',
  url: endpoint,
  status: 200,
  requestBody: 'xnm=2026&xqm=3&kzlx=ck&xsdm=&kclbdm=&kclxdm=&validate=',
  responseBody: JSON.stringify(response),
};
const rejectedRequest = {
  ...pageRequest,
  source: 'fetch',
  url: 'https://jwgl.qdc.edu.cn' + endpoint,
  responseBody: "{'state':'fail','message':'please retry again later'}",
};

async function capture(records, {host = 'jwgl.qdc.edu.cn', path = timetable, form = fields} = {}) {
  const requests = [];
  const result = await new Promise(resolve => {
    vm.runInNewContext(script, {
      location: new URL(path, 'https://' + host),
      document: {querySelector: key => Object.hasOwn(form, key) ? {value: form[key]} : null},
      URL,
      URLSearchParams,
      fetch: async (url, options) => {
        requests.push({url, options});
        return {text: async () => JSON.stringify(response)};
      },
      window: {
        __sleepyDiagNetworkRecords: records,
        __sleepyBridge: {onWiseduResult: value => resolve(JSON.parse(value))},
      },
    });
  });
  return {result, requests};
}

test('QDC imports the response from the verified page request without sending another request', async () => {
  const records = [pageRequest, rejectedRequest];
  for (const path of [timetable, '/jwglxt/xtgl/index_initMenu.html']) {
    const {result, requests} = await capture(records, {path});
    assert.deepEqual(requests, []);
    assert.equal(result.ok, true);
    assert.deepEqual(JSON.parse(result.data), response);
  }
});

test('QDC requires a response for the current semester and filters from its own page', async () => {
  for (const record of [
    {...pageRequest, requestBody: pageRequest.requestBody.replace('xqm=3', 'xqm=12')},
    {...pageRequest, requestBody: pageRequest.requestBody.replace('kclbdm=', 'kclbdm=A')},
    {...pageRequest, url: 'https://example.edu.cn' + endpoint},
    {...pageRequest, frame: 'frame'},
    {...pageRequest, status: 403},
    rejectedRequest,
  ]) {
    const {result, requests} = await capture([record]);
    assert.deepEqual(requests, []);
    assert.equal(result.ok, false);
  }
});

test('QDC uses the latest matching page request and distinguishes empty from invalid responses', async () => {
  const empty = await capture([pageRequest, {...pageRequest, responseBody: '{"kbList":[]}'}]);
  assert.equal(empty.result.emptySemester, true);
  const invalid = await capture([pageRequest, {...pageRequest, responseBody: '{"message":"error"}'}]);
  assert.equal(invalid.result.ok, false);
});

test('other new ZF installations still fetch their timetable', async () => {
  const {result, requests} = await capture([], {host: 'other.example.edu.cn'});
  assert.equal(requests.length, 1);
  assert.equal(requests[0].url, endpoint);
  assert.equal(requests[0].options.method, 'POST');
  assert.equal(requests[0].options.credentials, 'include');
  assert.deepEqual(JSON.parse(result.data), response);
});
