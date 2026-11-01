// Live cross-verify harness for the WakeUp share protocol (vendor JS side).
// Loads 拾光 GLOBAL_TOOLS/wake_up.js in a node vm sandbox with REAL fetch/crypto/Date
// and drives the two-stage protocol against api.wakeup.fun.
// Never prints codes, signA/signB bodies, or tokens — only structural facts.
import vm from 'node:vm';
import fs from 'node:fs';

const SRC = process.env.SLEEPY_WAKEUP_VENDOR_JS || "app/src/main/assets/third_party/shiguang/js/GLOBAL_TOOLS/wake_up.js";
let source = fs.readFileSync(SRC, 'utf8');
source = source.replace(/runImportFlow\(\);\s*$/, '');
if (process.env.SLEEPY_WAKEUP_VC) {
  // rebuild frozen constants with a different vc/vcname to test server acceptance
  const vc = Number(process.env.SLEEPY_WAKEUP_VC);
  const vcname = process.env.SLEEPY_WAKEUP_Vcname || (vc + '');
  source = source.replace(/versionCode: \d+,/, 'versionCode: ' + vc + ',')
                 .replace(/versionName: "[^"]*",/, 'versionName: "' + vcname + '",');
}

const sandbox = {
  console: { log() {}, warn() {}, error() {} },
  Math, JSON, TextEncoder, TextDecoder, BigInt, Object, Array, String, Number,
  Uint8Array, Uint32Array, DataView, Date, fetch, crypto, AbortController,
  setTimeout, clearTimeout,
  btoa: (bin) => Buffer.from(bin, 'binary').toString('base64'),
  atob: (b64) => Buffer.from(b64, 'base64').toString('binary'),
};
sandbox.globalThis = sandbox;
sandbox.window = { shiguangBridge: { showToast() {}, notifyTaskCompletion() {} }, shiguangBridgePromise: {} };
vm.createContext(sandbox);
vm.runInContext(source + `
; globalThis.__api = { WAKEUP_V6170, wakeupMd5Hex, wakeupNativeHexEncode, wakeupNativeHexDecode, wakeupDesEncrypt,
  wakeupDesDecrypt, wakeupRc4, wakeupBytesToBase64, wakeupBase64ToBytes, wakeupBytesToUtf8, wakeupAndroidQuote,
  wakeupFormEncode, wakeupCreateDevice, wakeupMakeCommonParams, wakeupMakeSignA, wakeupTokenFromSignB,
  wakeupGetRc4Key, wakeupGetSign, wakeupBuildShareRequest, wakeupExtractSignB, wakeupPostForm,
  wakeupDecodeShareCode, parseRawScheduleData, extractKeyFromText };
`, sandbox);
const A = sandbox.__api;

const mode = process.argv[2] || 'handshake';

if (mode === 'dump-xinput') {
  // Deterministic rand10 + fixed nowMs; drive the REAL two-stage flow with a
  // fabricated code; dump the server artifacts for Kotlin-side cross-decrypt.
  sandbox.crypto = { getRandomValues: (b) => { for (let i = 0; i < b.length; i++) b[i] = (i * 7 + 3) & 0xff; return b; } };
  const A2 = sandbox.__api;
  const NOW = Date.now(); // server validates _t_ freshness (errNo 5112), use live clock
  const device = A2.wakeupCreateDevice();
  const common = A2.wakeupMakeCommonParams(device);
  const { signA, rand10 } = A2.wakeupMakeSignA(device.cuid);
  const body1 = `${A2.wakeupFormEncode([['data', signA], ...common])}&`;
  const text1 = await A2.wakeupPostForm(`${A2.WAKEUP_V6170.host}${A2.WAKEUP_V6170.antispamPath}`, body1, device);
  const signB = A2.wakeupExtractSignB(JSON.parse(text1));
  if (!signB) { console.log('DUMP_ANTISPAM_FAIL'); process.exit(5); }
  const token = A2.wakeupTokenFromSignB(signB, rand10);
  const req = A2.wakeupBuildShareRequest('ZzQwFakeProbeCode1234567890', token, device, common, NOW);
  // wakeupBuildShareRequest has no nowMs param — re-sign deterministically here:
  const rc4Key = A2.wakeupGetRc4Key(token);
  const dataValue = A2.wakeupBytesToBase64(A2.wakeupRc4(`key=${A2.wakeupAndroidQuote('ZzQwFakeProbeCode1234567890')}`, rc4Key));
  const items = [`data=${dataValue}`, ...common.map(([k, v]) => `${k}=${v}`)];
  items.push(`nt=${device.nt}`, `_t_=${Math.floor(NOW / 1000)}`, `kakorrhaphiophobia=${NOW}`);
  const sign = A2.wakeupGetSign(A2.wakeupBytesToBase64(new TextEncoder().encode(items.sort().join(''))), token);
  const body2 = `&${A2.wakeupFormEncode([['data', dataValue], ...common, ['nt', device.nt]])}&sign=${sign}&_t_=${Math.floor(NOW / 1000)}&kakorrhaphiophobia=${NOW}`;
  const text2 = await A2.wakeupPostForm(`${A2.WAKEUP_V6170.host}${A2.WAKEUP_V6170.sharePath}`, body2, device);
  fs.writeFileSync(process.argv[3] || '/tmp/wakeup_live/xinput.json', JSON.stringify({
    rand10, signB, token, rc4Key, code: 'ZzQwFakeProbeCode1234567890', nowMs: NOW,
    antispamBody: body1, shareBody: body2, commonParams: common, device, signA,
    shareJson: text2, jsShareData: (() => { try {
      const sj = JSON.parse(text2);
      const enc = sj && typeof sj.data === 'object' ? sj.data.data : sj && sj.data;
      return typeof enc === 'string' ? JSON.parse(A2.wakeupBytesToUtf8(A2.wakeupRc4(A2.wakeupBase64ToBytes(enc), rc4Key))).shareData : null;
    } catch { return null; } })(),
  }, null, 1));
  console.log('DUMP_OK signBLen=' + signB.length + ' tokenLen=' + token.length);
}


if (mode === 'handshake') {
  // Stage 1 only: antispam signA -> signB -> token. No user data involved.
  const device = A.wakeupCreateDevice();
  const common = A.wakeupMakeCommonParams(device);
  const { signA, rand10 } = A.wakeupMakeSignA(device.cuid);
  const body = `${A.wakeupFormEncode([['data', signA], ...common])}&`;
  const t0 = Date.now();
  const text = await A.wakeupPostForm(`${A.WAKEUP_V6170.host}${A.WAKEUP_V6170.antispamPath}`, body, device);
  const dt = Date.now() - t0;
  let json;
  try { json = JSON.parse(text); } catch { console.log('ANTISPAM_BAD_JSON httpMs=' + dt + ' len=' + text.length); process.exit(2); }
  const signB = A.wakeupExtractSignB(json);
  if (!signB) { console.log('ANTISPAM_NO_SIGNB keys=' + Object.keys(json).join(',')); process.exit(3); }
  const token = A.wakeupTokenFromSignB(signB, rand10); // throws if rand10 self-check fails
  console.log('HANDSHAKE_OK httpMs=' + dt + ' tokenLen=' + token.length +
    ' selfCheckPassed=true signBLen=' + signB.length);
} else if (mode === 'share') {
  // Full two-stage with a real code from argv[3] (never logged).
  const code = process.argv[3];
  if (!code) { console.log('NO_CODE'); process.exit(4); }
  try {
    const shareData = await A.wakeupDecodeShareCode(code);
    const parts = A.parseRawScheduleData(shareData);
    const n = (parts.coursesRaw || []).length, d = (parts.courseDetailRaw || []).length;
    console.log('SHARE_OK segments=' + shareData.trim().split('\n').length +
      ' coursesRaw=' + n + ' details=' + d +
      ' maxWeek=' + (parts.uiConfig.maxWeek ?? 'null') +
      ' startDate=' + (parts.uiConfig.startDate ?? 'null'));
  } catch (e) {
    console.log('SHARE_FAIL ' + String(e && e.message || e));
  }
} else if (mode === 'probe-badsign') {
  // Negative control: same flow, but flip the sign before the share POST.
  const device = A.wakeupCreateDevice();
  const common = A.wakeupMakeCommonParams(device);
  const { signA, rand10 } = A.wakeupMakeSignA(device.cuid);
  const antispamBody = `${A.wakeupFormEncode([['data', signA], ...common])}&`;
  const antispamText = await A.wakeupPostForm(`${A.WAKEUP_V6170.host}${A.WAKEUP_V6170.antispamPath}`, antispamBody, device);
  const signB = A.wakeupExtractSignB(JSON.parse(antispamText));
  const token = A.wakeupTokenFromSignB(signB, rand10);
  const req = A.wakeupBuildShareRequest('ZzQwFakeProbeCode1234567890', token, device, common);
  const m = req.body.match(/&sign=([0-9a-f]+)/);
  const bad = '&sign=' + (m[1][0] === 'f' ? '0' + m[1].slice(1) : 'f' + m[1].slice(1));
  const brokenBody = req.body.replace(/&sign=[0-9a-f]+/, bad);
  const shareText = await A.wakeupPostForm(`${A.WAKEUP_V6170.host}${A.WAKEUP_V6170.sharePath}`, brokenBody, device);
  let sj; try { sj = JSON.parse(shareText); } catch { console.log('BAD_NON_JSON len=' + shareText.length); process.exit(0); }
  console.log('BADSIGN_DONE keys=' + Object.keys(sj).join(',') + ' errNo=' + String(sj.errNo) +
    ' errstr=' + JSON.stringify(String(sj.errstr).slice(0, 120)) +
    ' dataType=' + (sj.data === null ? 'null' : typeof sj.data) +
    ' dataLen=' + (typeof sj.data === 'string' ? sj.data.length : 0));
} else if (mode === 'probe-fake') {
  // Two-stage with a fabricated well-formed code: executes the full signed
  // share request against the live server. Expected outcome: server rejects the
  // CODE (business error / empty payload) but NOT the signature — proves the
  // request envelope (sign, headers, RC4 data) passes their validation.
  const device = A.wakeupCreateDevice();
  const common = A.wakeupMakeCommonParams(device);
  const { signA, rand10 } = A.wakeupMakeSignA(device.cuid);
  const antispamBody = `${A.wakeupFormEncode([['data', signA], ...common])}&`;
  const antispamText = await A.wakeupPostForm(`${A.WAKEUP_V6170.host}${A.WAKEUP_V6170.antispamPath}`, antispamBody, device);
  const signB = A.wakeupExtractSignB(JSON.parse(antispamText));
  const token = A.wakeupTokenFromSignB(signB, rand10);
  const req = A.wakeupBuildShareRequest('ZzQwFakeProbeCode1234567890', token, device, common);
  const shareText = await A.wakeupPostForm(`${A.WAKEUP_V6170.host}${A.WAKEUP_V6170.sharePath}`, req.body, device);
  let sj; try { sj = JSON.parse(shareText); } catch { console.log('PROBE_NON_JSON len=' + shareText.length); process.exit(0); }
  const keys = Object.keys(sj).join(',');
  const codeField = sj.code ?? sj.status ?? sj.errorCode ?? 'absent';
  const msg = String(sj.msg ?? sj.message ?? '').slice(0, 80);
  const inner = (sj.data && typeof sj.data === 'object') ? 'innerKeys=' + Object.keys(sj.data).map(k => k + ':' + String(sj.data[k]).slice(0, 40)).join('|') : '';
  console.log('PROBE_DONE vc=' + A.WAKEUP_V6170.versionCode + ' errNo=' + String(sj.errNo) + ' errstr=' + JSON.stringify(String(sj.errstr).slice(0, 80)) + ' ' + inner);
}
