// Offline execution of both production weekly collectors. No school login is used.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt', 'utf8');
const scripts = Object.fromEntries(['QZ_APP','BOYA_PP'].map(k => [k, source.match(new RegExp('(?:private )?const val '+k+'_FETCH_JS = """([\\s\\S]*?)"""'))[1]]));

async function execute(kind, mutate = () => {}, {userUnavailable=false, duration=false}={}) {
  const results=[], calls=[];
  const term={termName:'2026-1',currentTerm:'是',weekEnd:duration?'':'3',termBeginTime:'2026-09-07',termEndTime:'2026-09-27'};
  const fetch=async(target)=>{
    const url=new URL(target,'https://yjsxt.ysu.edu.cn/pp/'); calls.push(url);
    let status=200, body;
    if(url.pathname.endsWith('serverconfig.json')) body={ApiUrl:'https://api.example.edu'};
    else if(url.pathname==='/teachingWeek') body={code:'1',data:[{week:'1'},{week:2},{week:'3'}]};
    else if(url.pathname==='/student/curriculum') {
      const w=Number(url.searchParams.get('week'));
      body={code:'1',data:[{date:[],courses:[{courseName:'周'+w,teacherName:'甲',classroomNub:'A101',classTime:'10102',classWeek:String(w)}]}]};
    } else if(url.pathname==='/api/login/currentUser') {
      status=userUnavailable?404:200; body={code:200,data:{termName:term.termName}};
    } else if(url.pathname==='/api/microForm/term') body={code:200,data:[term]};
    else if(url.pathname==='/api/schedule/class/setting/current') body={code:200,data:{lessonConfig:[{lessonNumber:1,lessonTime:['2026-09-07T08:00','2026-09-07T08:45']}]}};
    else if(url.pathname==='/api/schedule/table/byStudent') {
      const w=Number(url.searchParams.get('whichWeek'));
      body={code:200,data:[{courseName:'周'+w,week:1,lessonNumber:1,whichWeek:w,classroomName:'A101',courseTeacher:[{name:'甲'}]}]};
    } else throw new Error('Unexpected endpoint '+url);
    const response={status,body}; mutate(url,response);
    if(response.error) throw new Error(response.error);
    return {ok:response.status>=200&&response.status<300,status:response.status,url:String(url),
      text:async()=> typeof response.body==='string'?response.body:JSON.stringify(response.body),
      json:async()=> typeof response.body==='string'?JSON.parse(response.body):response.body};
  };
  const window={__sleepyBridge:{onWiseduResult:s=>results.push(JSON.parse(s))}};
  vm.runInNewContext(scripts[kind],{window,fetch,URL,location:new URL('https://yjsxt.ysu.edu.cn/pp/'),document:{cookie:''},sessionStorage:{getItem:key=>key==='Token'?'offline-token':null}});
  for(let i=0;i<250;i++) await Promise.resolve();
  assert.equal(results.length,1,'collector must emit exactly one terminal result');
  return {calls,result:results[0]};
}

(async()=>{
  let passed=0;const failures=[];const cases=[];
  async function check(name,run){try{await run();passed++;cases.push({name,passed:true});}catch(e){failures.push(name+': '+e.message);cases.push({name,passed:false,error:e.name,message:e.message,stack:e.stack});}}
  for(const kind of ['QZ_APP','BOYA_PP']) {
    const weekly=kind==='QZ_APP'?'/student/curriculum':'/api/schedule/table/byStudent';
    const weekKey=kind==='QZ_APP'?'week':'whichWeek';
    await check(kind+' preserves the complete semester',async()=>{
      const {calls,result}=await execute(kind);assert.equal(result.ok,true);
      assert.deepEqual(calls.filter(u=>u.pathname===weekly).map(u=>u.searchParams.get(weekKey)),['1','2','3']);
      const data=JSON.parse(result.data);
      assert.equal(kind==='QZ_APP'?data.weeks.length:data.rows.length,3);
      if(kind==='BOYA_PP') {assert.equal(result.periods[0].start,'08:00');assert.equal(result.startDate,'2026-09-07');}
    });
    for(const failure of ['network','http500','http401','badjson','badcode','badshape']) {
      await check(kind+' rejects partial semester after '+failure,async()=>{
        const {result}=await execute(kind,(u,r)=>{
          if(u.pathname!==weekly || u.searchParams.get(weekKey)!=='2') return;
          if(failure==='network') r.error='offline failed';
          else if(failure==='http500') r.status=500;
          else if(failure==='http401') r.status=401;
          else if(failure==='badjson') r.body='{broken';
          else if(failure==='badcode') r.body={code:500,data:[]};
          else r.body={code:kind==='QZ_APP'?'1':200,data:{unexpected:true}};
        });
        assert.equal(result.ok,false);assert.equal(result.data,undefined);
      });
    }
  }
  for(const failure of ['network','http401','badjson','badcode','empty','badweek']) {
    await check('QZ_APP refuses guessed week list after '+failure,async()=>{
      const {calls,result}=await execute('QZ_APP',(u,r)=>{
        if(u.pathname!=='/teachingWeek') return;
        if(failure==='network') r.error='week failed';
        else if(failure==='http401') r.status=401;
        else if(failure==='badjson') r.body='{broken';
        else if(failure==='badcode') r.body={code:'401',data:[{week:1}]};
        else r.body={code:'1',data:failure==='empty'?[]:[{week:'2broken'}]};
      });
      assert.equal(result.ok,false);assert.equal(result.data,undefined);
      assert.equal(calls.filter(u=>u.pathname==='/student/curriculum').length,0);
    });
  }
  await check('QZ_APP deduplicates week requests without losing empty weeks',async()=>{
    const {calls,result}=await execute('QZ_APP',(u,r)=>{
      if(u.pathname==='/teachingWeek') r.body={code:'1',data:[{week:1},{week:'1'},{week:2}]};
      if(u.pathname==='/student/curriculum'&&u.searchParams.get('week')==='1') r.body={code:'1',data:[{date:[],courses:[]}]};
    });
    assert.equal(result.ok,true);assert.equal(calls.filter(u=>u.pathname==='/student/curriculum').length,2);
    assert.equal(JSON.parse(result.data).weeks.length,2);
  });
  await check('BOYA_PP preserves explicit current term fallback',async()=>{
    const {result}=await execute('BOYA_PP',()=>{},{userUnavailable:true});assert.equal(result.ok,true);
  });
  await check('BOYA_PP refuses conflicting current terms',async()=>{
    const {result}=await execute('BOYA_PP',(u,r)=>{
      if(u.pathname==='/api/microForm/term') r.body.data.push({...r.body.data[0],termName:'2026-2'});
    },{userUnavailable:true});assert.equal(result.ok,false);assert.equal(result.data,undefined);
  });
  await check('BOYA_PP derives complete weeks from an explicit date interval',async()=>{
    const {result,calls}=await execute('BOYA_PP',()=>{},{duration:true});assert.equal(result.ok,true);
    assert.equal(calls.filter(u=>u.pathname==='/api/schedule/table/byStudent').length,3);
  });
  await check('BOYA_PP refuses unknown semester length',async()=>{
    const {result,calls}=await execute('BOYA_PP',(u,r)=>{
      if(u.pathname==='/api/microForm/term') {r.body.data[0].weekEnd='';r.body.data[0].termEndTime='';}
    });assert.equal(result.ok,false);assert.equal(result.data,undefined);
    assert.equal(calls.filter(u=>u.pathname==='/api/schedule/table/byStudent').length,0);
  });
  await check('BOYA_PP includes a last week beginning on the semester end date',async()=>{
    const {result,calls}=await execute('BOYA_PP',(u,r)=>{
      if(u.pathname==='/api/microForm/term') r.body.data[0].termEndTime='2026-09-21';
    },{duration:true});assert.equal(result.ok,true);
    assert.equal(calls.filter(u=>u.pathname==='/api/schedule/table/byStudent').length,3);
  });
  await check('BOYA_PP rejects a malformed explicit week count',async()=>{
    const {result}=await execute('BOYA_PP',(u,r)=>{
      if(u.pathname==='/api/microForm/term') r.body.data[0].weekEnd='2broken';
    });assert.equal(result.ok,false);assert.equal(result.data,undefined);
  });
  for(const endpoint of ['/api/login/currentUser','/api/schedule/class/setting/current']) {
    await check('BOYA_PP preserves login failure at '+endpoint,async()=>{
      const {result}=await execute('BOYA_PP',(u,r)=>{if(u.pathname===endpoint) r.status=401;});
      assert.equal(result.ok,false);assert.equal(result.data,undefined);
    });
  }
  if(process.env.SLEEPY_JW_TEST_REPORT) fs.writeFileSync(process.env.SLEEPY_JW_TEST_REPORT,JSON.stringify(cases,null,2)+'\n');
  cases.filter(c=>!c.passed).forEach(c=>console.error(c.name+'\n'+c.stack));
  assert.deepEqual(failures,[],'weekly collector contracts failed');
  console.log('WEEKLY_FETCH: '+passed+' offline scenarios passed.');
})().catch(e=>{console.error(e);process.exitCode=1;});
