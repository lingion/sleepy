const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwZfNewFetchJs.kt', 'utf8');
const script = source.match(/const val ZF_NEW_FETCH_JS = """([\s\S]*?)"""/)[1];
const timetable = '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default';
const endpoint = '/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151';
const sampleResponse = {
  kbList: [
    {
      kcmc: '高等数学',
      xm: '李老师',
      cdmc: 'A101',
      xqj: '1',
      jcs: '1-2',
      jc: '1-2节',
      zcd: '1-4周,7-16周',
    },
  ],
};
const fields = {'#xnm': '2026', '#xqm': '3', '#xsdm': '', '#kclbdm': '', '#kclxdm': ''};
const pageRequest = {
  source: 'xhr',
  frame: 'top',
  method: 'POST',
  url: endpoint,
  status: 200,
  requestBody: 'xnm=2026&xqm=3&kzlx=ck&xsdm=&kclbdm=&kclxdm=&validate=',
  responseBody: JSON.stringify(sampleResponse),
};
const rejectedRequest = {
  ...pageRequest,
  source: 'fetch',
  url: 'https://jwgl.qdc.edu.cn' + endpoint,
  responseBody: "{'state':'fail','message':'please retry again later'}",
};

async function capture(records, {
  host = 'jwgl.qdc.edu.cn',
  path = timetable,
  form = fields,
  fetchBody = JSON.stringify(sampleResponse),
} = {}) {
  const requests = [];
  const result = await new Promise(resolve => {
    vm.runInNewContext(script, {
      location: new URL(path, 'https://' + host),
      document: {querySelector: key => Object.hasOwn(form, key) ? {value: form[key]} : null},
      URL,
      URLSearchParams,
      fetch: async (url, options) => {
        requests.push({url, options});
        return {text: async () => fetchBody};
      },
      window: {
        __sleepyDiagNetworkRecords: records,
        __sleepyBridge: {onWiseduResult: value => resolve(JSON.parse(value))},
      },
    });
  });
  return {result, requests};
}

test('QDC imports the verified XHR response without sending another fetch', async () => {
  const records = [pageRequest, rejectedRequest];
  for (const host of ['jwgl.qdc.edu.cn', 'JWGL.QDC.EDU.CN']) {
    for (const path of [timetable, '/jwglxt/xtgl/index_initMenu.html']) {
      const {result, requests} = await capture(records, {host, path});
      assert.deepEqual(requests, []);
      assert.equal(result.ok, true);
      assert.deepEqual(JSON.parse(result.data), sampleResponse);
    }
  }
});

test('passive capture enforces current semester, present filters, origin, and latest status', async () => {
  for (const record of [
    {...pageRequest, requestBody: pageRequest.requestBody.replace('xqm=3', 'xqm=12')},
    {...pageRequest, requestBody: pageRequest.requestBody.replace('kclbdm=', 'kclbdm=A')},
    {...pageRequest, requestBody: pageRequest.requestBody.replace('kzlx=ck', 'kzlx=xs')},
    {...pageRequest, url: 'https://example.edu.cn' + endpoint},
    {...pageRequest, status: 403},
    rejectedRequest,
  ]) {
    const {result, requests} = await capture([record]);
    assert.deepEqual(requests, []);
    assert.equal(result.ok, false);
    assert.equal(result.kind, 'NOT_ON_TIMETABLE');
  }
});

test('passive capture uses latest matching request and does not fall back to stale cache on error', async () => {
  const empty = await capture([pageRequest, {...pageRequest, responseBody: '{"kbList":[]}'}]);
  assert.equal(empty.result.ok, true);
  assert.equal(empty.result.emptySemester, true);

  const invalid = await capture([pageRequest, {...pageRequest, responseBody: '{"message":"error"}'}]);
  assert.equal(invalid.result.ok, false);

  const failedLatest = await capture([pageRequest, {...pageRequest, status: 500}]);
  assert.equal(failedLatest.result.ok, false);
});

test('passive capture tolerates missing optional filter inputs, WebVPN/bare paths, iframe fallback, and nested kbList', async () => {
  const minimalForm = {'#xnm': '2026', '#xqm': '3'};
  const webvpnRecord = {
    ...pageRequest,
    url: '/http/7f3a9c/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151',
    requestBody: 'xnm=2026&xqm=3',
    responseBody: JSON.stringify({wrapper: {kbList: sampleResponse.kbList}}),
  };
  const webvpnRes = await capture([webvpnRecord], {form: minimalForm});
  assert.equal(webvpnRes.result.ok, true);
  assert.deepEqual(JSON.parse(webvpnRes.result.data), sampleResponse);

  const bareRecord = {
    ...pageRequest,
    frame: 'frame',
    url: '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151',
  };
  const frameRes = await capture([bareRecord]);
  assert.equal(frameRes.result.ok, true);
  assert.deepEqual(JSON.parse(frameRes.result.data), sampleResponse);
});

test('other new ZF hosts still fetch actively and fall back to recorded XHR only when fetch is rejected', async () => {
  const normal = await capture([], {host: 'other.example.edu.cn'});
  assert.equal(normal.requests.length, 1);
  assert.equal(normal.requests[0].url, endpoint);
  assert.equal(normal.requests[0].options.method, 'POST');
  assert.equal(normal.requests[0].options.credentials, 'include');
  assert.deepEqual(JSON.parse(normal.result.data), sampleResponse);

  const blockedNonJson = await capture([pageRequest], {
    host: 'other.example.edu.cn',
    fetchBody: "{'state':'fail','message':'please retry again later'}",
  });
  assert.equal(blockedNonJson.requests.length, 1);
  assert.equal(blockedNonJson.result.ok, true);
  assert.deepEqual(JSON.parse(blockedNonJson.result.data), sampleResponse);

  const blockedJson = await capture([pageRequest], {
    host: 'other.example.edu.cn',
    fetchBody: JSON.stringify({state: 'fail', message: 'validate required'}),
  });
  assert.equal(blockedJson.requests.length, 1);
  assert.equal(blockedJson.result.ok, true);
  assert.deepEqual(JSON.parse(blockedJson.result.data), sampleResponse);

  const expiredLogin = await capture([pageRequest], {
    host: 'other.example.edu.cn',
    fetchBody: '<html><form action="login_slogin"><input name="csrftoken"/><input name="mm"/></form></html>',
  });
  assert.equal(expiredLogin.result.ok, false);
  assert.equal(expiredLogin.result.kind, 'SESSION_EXPIRED');

  const noCacheNonJson = await capture([], {
    host: 'other.example.edu.cn',
    fetchBody: 'not-json',
  });
  assert.equal(noCacheNonJson.result.ok, false);
  assert.equal(noCacheNonJson.result.kind, 'SESSION_EXPIRED');
});
