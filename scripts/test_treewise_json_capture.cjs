const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const source=fs.readFileSync(path.resolve(__dirname,'../app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwTreewiseJsonCapture.kt'),'utf8');
const template=source.split('"""')[1].replaceAll("${'$'}",'$');
const termSelector='#allSemesters,[name="semesterId"],#semesterId,option[name="semesterAssoc"]';
const buttonSelector='body > div > div > div:nth-child(3) > div > div.student-panel-body.info-page > button';
const student={studentTableVm:{activities:[{courseName:'数学',weekday:2,startUnit:3,endUnit:4,weekIndexes:[1,3],credits:2,teachers:['李']}],extra:'keep'},metadata:'full'};
const hfu={result:{lessonList:[{id:7,courseName:'数学'}],scheduleList:[{lessonId:7,weekday:1,startTime:830,endTime:1010,weekIndex:1,room:{nameZh:'A1'}}]},metadata:'full'};
const node=(value,extra={})=>({value:String(value),tagName:'SELECT',...extra});
const route=(term='101',suffix='')=>'https://jw.example.edu/student/for-std/course-table/semester/'+term+'/print-data'+suffix;
function page(options={}) {
 const state={terms:options.terms || [node(101)],scripts:options.scripts || [],buttons:options.buttons || [],entries:options.entries || []};
 const W={location:new URL(options.url || 'https://jw.example.edu/student/for-std/course-table'),frames:[],performance:{getEntriesByType:t=>{assert.equal(t,'resource');return state.entries;}},document:{querySelectorAll:s=>{
  if(s===termSelector)return state.terms;if(s==='script')return state.scripts.map(textContent=>({textContent}));if(s===buttonSelector)return state.buttons;assert.fail('selector '+s);
 }}};
 Object.assign(W,options.globals || {});return {W,state};
}
async function run(type,answers,options={}) {
 const p=options.page || page(options),W=p.W,calls=[],results=[],timers=[];
 W.__sleepyBridge={onWiseduResult:t=>results.push(JSON.parse(t))};
 W.fetch=async(url,init)=>{
  const index=calls.length;calls.push({url,init});if(options.beforeResponse)await options.beforeResponse(p,index,timers);
  const a=answers[index];assert.notEqual(a,undefined,'unexpected request '+url);
  if(a.throw)throw new Error(a.throw);
  return {ok:a.status===undefined || a.status===200,status:a.status || 200,url:a.finalUrl || url,text:async()=>{
   if(options.beforeText)await options.beforeText(p,index,timers);return typeof a.body==='string'?a.body:JSON.stringify(a.body);
  }};
 };
 await vm.runInNewContext(template.replace('__TREEWISE_TYPE__',type),{window:W,URL,AbortController,setTimeout:f=>{timers.push(f);return timers.length;},clearTimeout:()=>{}});
 assert.equal(results.length,1);return {result:results[0],calls,p};
}
let count=0;
async function check(name,fn){await fn();count++;console.log('PASS '+name);}
function valid(r,expected){assert.equal(r.result.ok,true,r.result.err);assert.deepEqual(JSON.parse(r.result.data),expected);assert.equal(r.result.xnxq,'101');for(const c of r.calls){assert.equal(c.init.credentials,'include');assert.equal(c.init.redirect,'error');assert.equal(c.init.mode,'same-origin');assert.ok(c.init.signal);}}
(async()=>{
 await check('new student refetch exact current print route',async()=>{const r=await run('shuwei_new',[{body:student}],{entries:[{name:route()}]});valid(r,student);assert.equal(r.calls[0].url,route());});
 await check('new student ignores older term resource',async()=>{const r=await run('shuwei_new',[{body:student}],{entries:[{name:route('100')},{name:route()}]});valid(r,student);assert.equal(r.calls[0].url,route());});
 await check('duplicate same route is harmless',async()=>valid(await run('shuwei_new',[{body:student}],{entries:[{name:route()},{name:route()}]}),student));
 await check('NWPU uses source print dataId and experiment flag',async()=>{const r=await run('nwpu',[{body:student}],{scripts:['var dataId = 204; var personId = 99;']});valid(r,student);assert.equal(r.calls[0].url,route('101','/204?hasExperiment=true'));});
 await check('NWPU live dataId is accepted',async()=>valid(await run('nwpu',[{body:student}],{globals:{dataId:204}}),student));
 await check('JMU absent lessonIds takes source student-array print route',async()=>{const r=await run('jmu',[{body:{other:'probe'}},{body:student}],{scripts:['var dataId = 44; var studentId = [205];']});valid(r,student);assert.ok(r.calls[0].url.endsWith('?bizTypeId=2&semesterId=101&dataId=44'));assert.equal(r.calls[1].url,route('101','/0/205'));});
 await check('JMU live one-student array accepted',async()=>valid(await run('jmu',[{body:{}},{body:student}],{globals:{studentId:[205]}}),student));
 await check('AHNU uses empty native needDataId=false',async()=>{const r=await run('ahnu',[{body:{lessonIds:[7]}},{body:hfu}],{globals:{dataId:999}});valid(r,hfu);assert.ok(r.calls[0].url.endsWith('dataId='));assert.deepEqual(JSON.parse(r.calls[1].init.body),{lessonIds:[7],studentId:'',weekIndex:''});});
 await check('HFU default needDataId=true uses info path',async()=>{const r=await run('hfu',[{body:{lessonIds:[7]}},{body:hfu}],{url:'http://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table/info/206'});valid(r,hfu);assert.ok(r.calls[0].url.startsWith('http://jxglstu.hfut.edu.cn/eams5-student/'));assert.ok(r.calls[0].url.endsWith('dataId=206'));assert.equal(JSON.parse(r.calls[1].init.body).studentId,'206');});
 await check('HFU source button supplies dataId',async()=>valid(await run('hfu',[{body:{lessonIds:[7]}},{body:hfu}],{buttons:[node(206)]}),hfu));
 await check('CUMTB source dataId and page business type retained',async()=>{const r=await run('cumtb',[{body:{lessonIds:[7]}},{body:hfu}],{scripts:['var dataId = 207; var config = {bizTypeId: 8,};']});valid(r,hfu);assert.ok(r.calls[0].url.endsWith('bizTypeId=8&semesterId=101&dataId=207'));assert.equal(JSON.parse(r.calls[1].init.body).studentId,'');});
 await check('empty lessonIds retries biz23 in identical term',async()=>{const r=await run('ahnu',[{body:{lessonIds:[]}},{body:{lessonIds:[7]}},{body:hfu}]);valid(r,hfu);assert.ok(r.calls[1].url.includes('bizTypeId=23&semesterId=101'));});
 await check('empty biz23 never switches to first term',async()=>{const r=await run('ahnu',[{body:{lessonIds:[]}},{body:{lessonIds:[]}}]);assert.equal(r.result.ok,false);assert.equal(r.calls.length,2);});
 await check('select uses current option rather than first source option',async()=>{const r=await run('nwpu',[{body:student}],{terms:[node(101),node(999,{tagName:'OPTION',selected:false})],globals:{dataId:204}});valid(r,student);});
 await check('selected semesterAssoc option accepted',async()=>valid(await run('nwpu',[{body:student}],{terms:[node(101,{tagName:'OPTION',selected:true})],globals:{dataId:204}}),student));
 await check('same origin frame selected',async()=>{const p=page({url:'https://jw.example.edu/portal'}),child=page({globals:{dataId:204}});p.W.frames=[child.W];child.W.fetch=async()=>({ok:true,status:200,url:route('101','/204?hasExperiment=true'),text:async()=>JSON.stringify(student)});const r=await run('nwpu',[],{page:p});valid(r,student);});
 for(const [name,opts] of [
  ['missing term',{terms:[]}],['invalid term',{terms:[node(0)]}],['conflicting term',{terms:[node(101),node(102)]}],
  ['URL semester conflict',{url:'https://jw.example.edu/student/for-std/course-table?semesterId=102'}],
  ['missing dataId',{}],['malformed dataId',{scripts:['var dataId = someObject;']}],['live/static ID conflict',{globals:{dataId:205},scripts:['var dataId = 204;']}],
 ]) await check('NWPU rejects '+name,async()=>{const r=await run('nwpu',[],opts);assert.equal(r.result.ok,false);assert.equal(r.calls.length,0);});
 for(const [name,entries] of [['no resource',[]],['older term',[{name:route('100')}]],['cross origin',[{name:route().replace('jw.example.edu','evil.example')}]],['semester query conflict',[{name:route()+'?semesterId=102'}]],['ambiguous same term',[{name:route('101','/1')},{name:route('101','/2')}]]]) await check('new student rejects '+name,async()=>{const r=await run('shuwei_new',[],{entries});assert.equal(r.result.ok,false);assert.equal(r.calls.length,0);});
 await check('HFU no student ID does not use blank',async()=>{const r=await run('hfu',[]);assert.equal(r.result.ok,false);});
 await check('HFU conflicting path and button rejected',async()=>{const r=await run('hfu',[],{url:'https://jw.example.edu/student/for-std/course-table/info/2',buttons:[node(3)]});assert.equal(r.result.ok,false);});
 await check('JMU multiple student IDs rejected',async()=>{const r=await run('jmu',[],{globals:{studentId:[2,3]}});assert.equal(r.result.ok,false);});
 await check('JMU unexpected lessonIds shape does not feed wrong parser',async()=>{const r=await run('jmu',[{body:{lessonIds:[7]}}],{globals:{studentId:[205]}});assert.equal(r.result.ok,false);assert.equal(r.calls.length,1);});
 for(const [name,answer] of [['http',{status:401,body:{}}],['login URL',{finalUrl:'https://jw.example.edu/login',body:student}],['cross origin URL',{finalUrl:'https://id.example.edu/login',body:student}],['html',{body:'<html>login</html>'}],['empty object',{body:{}}],['success false',{body:{success:false,...student}}],['invalid VM',{body:{studentTableVms:[{activities:[]},{}]}}],['network',{throw:'network failed'}]]) await check('reject '+name,async()=>{const r=await run('nwpu',[answer],{globals:{dataId:204}});assert.equal(r.result.ok,false);});
 for(const bad of [{},{lessonIds:'7'},{lessonIds:[{}]}]) await check('reject invalid lessonIds '+JSON.stringify(bad),async()=>{const r=await run('ahnu',[{body:bad}]);assert.equal(r.result.ok,false);assert.equal(r.calls.length,1);});
 await check('reject malformed HFU datum',async()=>{const r=await run('ahnu',[{body:{lessonIds:[7]}},{body:{result:{lessonList:[]}}}]);assert.equal(r.result.ok,false);});
 for(const phase of ['beforeResponse','beforeText']) for(const mutation of ['term','href','missing term','dataId','new frame','disappeared frame']) await check('reject '+mutation+' during '+phase,async()=>{
  const p=page({globals:{dataId:204}}),opts={page:p};opts[phase]=async(q)=>{
   if(mutation==='term')q.state.terms=[node(102)];if(mutation==='href')q.W.location=new URL(q.W.location.href+'#changed');if(mutation==='missing term')q.state.terms=[];if(mutation==='dataId')q.W.dataId=205;if(mutation==='new frame')q.W.frames=[page({globals:{dataId:204}}).W];if(mutation==='disappeared frame')q.W.location=new URL('https://jw.example.edu/portal');
  };
  const r=await run('nwpu',[{body:student}],opts);assert.equal(r.result.ok,false);
 });
 await check('reject same URL document replacement during response',async()=>{const r=await run('nwpu',[{body:student}],{globals:{dataId:204},beforeResponse:async(p)=>{p.W.document={...p.W.document};}});assert.equal(r.result.ok,false);});
 await check('reject response changed query while retaining path',async()=>{const r=await run('nwpu',[{body:student,finalUrl:route('101','/204?hasExperiment=false')}],{globals:{dataId:204}});assert.equal(r.result.ok,false);});
 await check('timeout remains single callback after late response',async()=>{const r=await run('nwpu',[{body:student}],{globals:{dataId:204},beforeResponse:async(p,i,timers)=>timers[0]()});assert.equal(r.result.ok,false);assert.match(r.result.err,/超时/);});
 for(const raw of ['null',"''",'"$dataId"']) await check('JMU optional dataId '+raw,async()=>valid(await run('jmu',[{body:{}},{body:student}],{scripts:['var dataId = '+raw+'; var studentId = [205];']}),student));
 await check('unsafe numeric dataId rejected',async()=>assert.equal((await run('nwpu',[],{globals:{dataId:9007199254740992}})).result.ok,false));
 await check('unsafe numeric lessonId rejected',async()=>assert.equal((await run('ahnu',[{body:{lessonIds:[9007199254740992]}}])).result.ok,false));
 await check('business selection change in-flight rejected',async()=>assert.equal((await run('cumtb',[{body:{lessonIds:[7]}}],{globals:{bizTypeId:2},beforeText:async(p)=>{p.W.bizTypeId=8;}})).result.ok,false));
 for(const index of [1,2]) await check('HFU term switch at request '+index+' rejected',async()=>{const r=await run('ahnu',[{body:{lessonIds:[]}},{body:{lessonIds:[7]}},{body:hfu}],{beforeResponse:async(p,i)=>{if(i===index)p.state.terms=[node(102)];}});assert.equal(r.result.ok,false);assert.equal(r.calls.length,index+1);});
 for(const base of ['/student','/eams5','/eams5-student']) {
  const url='https://jw.example.edu'+base+'/for-std/course-table';
  await check('NWPU preserves '+base+' deployment prefix',async()=>{
   const r=await run('nwpu',[{body:student}],{url,globals:{dataId:204}});valid(r,student);
   assert.equal(r.calls[0].url,url+'/semester/101/print-data/204?hasExperiment=true');
  });
  await check('new student preserves '+base+' print route',async()=>{
   const resource=url+'/semester/101/print-data/204?layout=print';
   const r=await run('shuwei_new',[{body:student}],{url,entries:[{name:resource}]});valid(r,student);
   assert.equal(r.calls[0].url,resource);
  });
 }
 for(const type of ['nwpu','shuwei_new'])for(const url of ['https://jw.example.edu/eams5','https://uis.nwpu.edu.cn/cas/login'])
  await check(type+' refuses unselected entry '+url,async()=>{
   const r=await run(type,[],{url,globals:{dataId:204}});assert.equal(r.result.ok,false);assert.equal(r.calls.length,0);
  });
 await check('new student does not infer print route from NWPU dataId',async()=>{
  const r=await run('shuwei_new',[],{globals:{dataId:204}});assert.equal(r.result.ok,false);assert.equal(r.calls.length,0);
 });
 await check('six exact dispatch aliases',async()=>{for(const type of ['shuwei_new','jmu','nwpu','cumtb','ahnu','hfu'])assert.ok(source.includes('"'+type+'"'));
  const screen=fs.readFileSync(path.resolve(__dirname,'../app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt'),'utf8');
  assert.ok(screen.includes('JwTreewiseJsonCapture.scriptFor(school.type)?.let { script ->'));
  assert.ok(screen.indexOf('JwTreewiseJsonCapture.scriptFor(school.type)')<screen.indexOf('JwPagedSchoolCapture.scriptFor(school.type)'));
 });
 console.log('Passed '+count+' treewise JSON capture scenarios.');
})().catch(e=>{console.error(e);process.exitCode=1;});
