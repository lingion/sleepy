// Offline execution of the production classic EAMS collector. No school login is used.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt', 'utf8');
const script = source.match(/private const val CLASSIC_EAMS_FETCH_JS = """([\s\S]*?)"""/)[1];
const entry = '<form name="courseTableForm" action="courseTableForStd.action"><input name="ids" value="2024000001"><input value="280" name="semester.id"><input name="project.id" value="9"></form>';

async function execute(html, {url='https://jwgl.webvpn.nbcc.cn/eams/courseTableForStd.action', frames=[], response='new TaskActivity(', responseUrl=url}={}) {
  const calls=[], results=[];
  const document={documentElement:{outerHTML:html}, querySelectorAll:()=>frames};
  document.implementation={createHTMLDocument:()=>({documentElement:{outerHTML:'', set innerHTML(value){this.outerHTML=value;}}, querySelectorAll:()=>[]})};
  const location=new URL(url);
  const fetch=async (target, options={})=>{
    calls.push({url:target, options});
    return {ok:true, status:200, url:responseUrl, text:async()=>response};
  };
  const window={__sleepyBridge:{onWiseduResult:value=>results.push(JSON.parse(value))}};
  vm.runInNewContext(script, {window, document, location, fetch, URL});
  for(let i=0;i<30;i++) await Promise.resolve();
  assert.equal(results.length,1,'collector must complete with exactly one callback');
  return {calls,result:results[0]};
}
(async()=>{
  let passed=0;const failures=[];
  async function check(name, run){try{await run();passed++;}catch(error){failures.push(name+': '+error.message);}}
  await check('hidden input attributes',async()=>{
    const {calls,result}=await execute(entry);assert.equal(result.ok,true);
    assert.equal(calls.length,1);const p=new URLSearchParams(calls[0].options.body);
    assert.equal(p.get('ids'),'2024000001');assert.equal(p.get('semester.id'),'280');assert.equal(p.get('project.id'),'9');
    assert.equal(calls[0].url,'https://jwgl.webvpn.nbcc.cn/eams/courseTableForStd!courseTable.action');
  });
  await check('multiline hidden input',async()=>{
    const {calls,result}=await execute(entry.replace('name="ids" value=', 'name="ids"\nvalue='));
    assert.equal(result.ok,true);assert.equal(new URLSearchParams(calls[0].options.body).get('ids'),'2024000001');
  });
  await check('same origin frame retains its URL',async()=>{
    const childUrl='https://webvpn.nbcc.edu.cn/https/777264/eams/courseTableForStd.action';
    const frames=[{contentDocument:{documentElement:{outerHTML:entry},defaultView:{location:{href:childUrl}}}}];
    const {calls,result}=await execute('<html>portal</html>',{frames});assert.equal(result.ok,true);
    assert.equal(calls[0].url,'https://webvpn.nbcc.edu.cn/https/777264/eams/courseTableForStd!courseTable.action');
  });
  await check('missing ids cannot borrow another field',async()=>{
    const {calls,result}=await execute(entry.replace('name="ids" value="2024000001"','name="ids"')+'<input name="other" value="99">');
    assert.equal(result.ok,false);assert.equal(calls.length,0);
  });
  await check('missing semester cannot borrow another field',async()=>{
    const {calls,result}=await execute(entry.replace('value="280" name="semester.id"','name="semester.id"')+'<input name="other" value="99">');
    assert.equal(result.ok,false);assert.equal(calls.length,0);
  });
  await check('calendar remains the first candidate',async()=>{
    const {calls,result}=await execute(entry+'<script>jQuery("#x").semesterCalendar({value:"279"});</script>');
    assert.equal(result.ok,true);assert.equal(new URLSearchParams(calls[0].options.body).get('semester.id'),'279');
  });
  await check('calendar quoted keys remain the selected semester',async()=>{
    const {calls,result}=await execute(entry+'<script>jQuery("#x").semesterCalendar({"value":"279"});</script>');
    assert.equal(result.ok,true);assert.equal(new URLSearchParams(calls[0].options.body).get('semester.id'),'279');
  });
  await check('login redirect rejects data',async()=>{
    const {result}=await execute(entry,{responseUrl:'https://jwgl.webvpn.nbcc.cn/login'});
    assert.equal(result.ok,false);assert.equal(result.data,undefined);
  });
  await check('Nankai entry navigation accepts hidden fields',async()=>{
    const {calls,result}=await execute('<html>portal</html>',{url:'https://eamis.nankai.edu.cn/eams/index.action',response:entry});
    // The entry response is accepted, then its TaskActivity-free response must exhaust candidates.
    assert.equal(result.ok,false);assert.equal(calls.length,3);
    assert.equal(calls[0].url,'https://eamis.nankai.edu.cn/eams/courseTableForStd.action');
    assert.equal(new URLSearchParams(calls[1].options.body).get('semester.id'),'280');
  });
  for(const failure of failures) console.error(failure);
  assert.deepEqual(failures,[],'classic EAMS collector scenarios failed');
  console.log(`CLASSIC_EAMS: ${passed} offline collector scenarios passed.`);
})().catch(error=>{console.error(error);process.exitCode=1;});
