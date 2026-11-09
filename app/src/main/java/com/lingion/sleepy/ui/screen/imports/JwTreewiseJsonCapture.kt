package com.lingion.sleepy.ui.screen.imports

/** Uses the selected teaching-page term; transports complete JSON for Kotlin parsing. */
object JwTreewiseJsonCapture {
    fun scriptFor(type: String?): String? = if (type in setOf(
        "shuwei_new", "jmu", "nwpu", "cumtb", "ahnu", "hfu"
    )) FETCH_JS.replace("__TREEWISE_TYPE__", requireNotNull(type)) else null

    const val FETCH_JS = """(async function(){
  var type = '__TREEWISE_TYPE__', finished = false;
  var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
  var deadline = setTimeout(function(){fail('课表读取超时，请重试');},20000);
  function finish(result) {
    if (finished) return;
    finished = true; clearTimeout(deadline); if (controller) controller.abort();
    window.__sleepyBridge.onWiseduResult(JSON.stringify(result));
  }
  function fail(message) {finish({ok:false,err:message,format:'treewise-'+type});}
  function object(v) {return v !== null && typeof v === 'object' && !Array.isArray(v);}
  function scalar(v) {return typeof v === 'string' || typeof v === 'number' ? String(v).trim() : '';}
  function id(v) {if(typeof v==='number'&&!Number.isSafeInteger(v))throw new Error('页面标识不是精确整数');var s=scalar(v); if (!/^[1-9][0-9]*$/.test(s)) throw new Error('页面标识格式无法识别'); return s;}
  function unique(values,label,required) {
    values=values.filter(function(v,i){return values.indexOf(v)===i;});
    if (values.length>1) throw new Error('页面存在冲突的'+label);
    if (!values.length && required) throw new Error('页面没有当前'+label+'，请打开课表并完成选择');
    return values.length ? values[0] : '';
  }
  function semester(W) {
    var values=[], nodes=W.document.querySelectorAll('#allSemesters,[name="semesterId"],#semesterId,option[name="semesterAssoc"]');
    for(var i=0;i<nodes.length;i++) {
      var n=nodes[i]; if(String(n.tagName).toLowerCase()==='option' && !n.selected) continue;
      var v=scalar(n.value); if(v) values.push(id(v));
    }
    var query=new URL(W.location.href).searchParams.getAll('semesterId');
    for(var j=0;j<query.length;j++) if(scalar(query[j])) values.push(id(query[j]));
    var path=/\/semester\/([1-9][0-9]*)\//.exec(W.location.pathname);
    if(path) values.push(path[1]);
    return unique(values,'学期',true);
  }
  function pageId(W,name,array) {
    var values=[];
    function add(v) {
      if(array) {if(!Array.isArray(v)||v.length!==1) throw new Error('页面学生标识不是唯一值');v=v[0];}
      if(v!==undefined && v!==null && v!=='') values.push(id(v));
    }
    if(W[name]!==undefined) add(W[name]);
    var scripts=W.document.querySelectorAll('script'), pattern=new RegExp('(?:var|let|const)\\s+'+name+'\\s*=\\s*([^;]+);','g');
    for(var i=0;i<scripts.length;i++) {
      var text=scripts[i].textContent || '',m;
      while((m=pattern.exec(text))) {
        var raw=m[1].trim();if(!array && /^(?:null|undefined|''|""|['"].*\$.*['"])$/.test(raw))continue;var number=array ? /^\[\s*['"]?([1-9][0-9]*)['"]?\s*\]$/.exec(raw) : /^['"]?([1-9][0-9]*)['"]?$/.exec(raw);
        if(!number) throw new Error('页面'+name+'标识格式无法识别');
        values.push(number[1]);
      }
    }
    return unique(values,name,false);
  }
  function pageBiz(W) {
    var values=[], v=pageId(W,'bizTypeId',false);if(v)values.push(v);
    var scripts=W.document.querySelectorAll('script'),pattern=/bizTypeId\s*:\s*['"]?([1-9][0-9]*)['"]?\s*[,}]/g;
    for(var i=0;i<scripts.length;i++){var m;while((m=pattern.exec(scripts[i].textContent || '')))values.push(m[1]);}
    return unique(values,'业务类型',false) || '2';
  }
  function printUrl(W,base,term) {
    if(!W.performance || !W.performance.getEntriesByType) throw new Error('请先在教务页面打开打印课表后重试');
    var routes=[], entries=W.performance.getEntriesByType('resource');
    for(var i=0;i<entries.length;i++) {
      var u; try {u=new URL(entries[i].name,W.location.href);} catch(error){continue;}
      if(u.origin!==W.location.origin || u.username || u.password || u.hash) continue;
      var prefix=base+'/for-std/course-table/semester/'+term+'/print-data';
      if(u.pathname!==prefix && u.pathname.indexOf(prefix+'/')!==0) continue;
      var terms=u.searchParams.getAll('semesterId');
      if(terms.some(function(v){return v!==term;})) continue;
      routes.push(u.href);
    }
    return unique(routes,'当前学期打印接口',true);
  }
  function snapshot(W) {
    var href=W.location.href, marker='/for-std/course-table', index=W.location.pathname.indexOf(marker);
    if(index<0 || (W.location.pathname[index+marker.length] && W.location.pathname[index+marker.length]!=='/')) return null;
    var base=W.location.pathname.slice(0,index), term=semester(W), dataId='', studentId='', route='',bizId=(type==='jmu'||type==='cumtb')?pageBiz(W):'2';
    if(type==='hfu') {
      var info=/\/for-std\/course-table\/info\/([1-9][0-9]*)\/?$/.exec(W.location.pathname), ids=[];
      if(info) ids.push(info[1]);
      var buttons=W.document.querySelectorAll('body > div > div > div:nth-child(3) > div > div.student-panel-body.info-page > button');
      for(var b=0;b<buttons.length;b++) if(scalar(buttons[b].value)) ids.push(id(buttons[b].value));
      dataId=unique(ids,'学生标识',true);
    } else if(type==='cumtb' || type==='jmu' || type==='nwpu') dataId=pageId(W,'dataId',false);
    if(type==='nwpu' && !dataId) throw new Error('页面没有 dataId，请打开个人课表后重试');
    if(type==='jmu') studentId=pageId(W,'studentId',true);
    if(type==='shuwei_new') route=printUrl(W,base,term);
    return {href:href,base:base,term:term,dataId:dataId,studentId:studentId,route:route,bizId:bizId};
  }
  var candidates=[],seen=[];
  function visit(W,depth) {
    if(depth>8) throw new Error('课表页面嵌套过深，请直接打开需要导入的课表');
    if(!W || seen.indexOf(W)>=0) return; seen.push(W);
    var location,frames;
    try {location=W.location;if(location.origin!==window.location.origin || !/^https?:$/.test(location.protocol)) return;frames=W.frames;} catch(error){return;}
    var selection=snapshot(W);if(selection)candidates.push({W:W,selection:selection});
    for(var i=0;i<frames.length;i++) visit(frames[i],depth+1);
  }
  try {
    visit(window,0);
    if(candidates.length!==1) throw new Error(candidates.length?'发现多个课表页面，请只保留需要导入的课表':'请登录教务并打开个人课表，选择需要导入的学期');
    var chosen=candidates[0],W=chosen.W,selected=chosen.selection,initialDocument=W.document,fingerprint=JSON.stringify(selected);
    function verify() {
      if(finished) throw new Error('采集已结束');
      candidates=[];seen=[];visit(window,0);
      if(candidates.length!==1 || candidates[0].W!==W || W.document!==initialDocument || JSON.stringify(candidates[0].selection)!==fingerprint)
        throw new Error('课表页面或学期已变化，请重新点击导入');
    }
    async function request(path,body) {
      verify();var u=new URL(path,W.location.href);
      if(u.origin!==W.location.origin || u.username || u.password) throw new Error('课表接口与当前页面不同源');
      var options={method:body===undefined?'GET':'POST',credentials:'include',mode:'same-origin',redirect:'error',headers:{'Accept':'application/json','X-Requested-With':'XMLHttpRequest'}};
      if(controller)options.signal=controller.signal;
      if(body!==undefined){options.headers['Content-Type']='application/json';options.body=JSON.stringify(body);}
      var response=await W.fetch(u.href,options);verify();
      if(!response.ok)throw new Error('课表请求失败 HTTP '+response.status);
      var finalUrl=new URL(response.url || u.href,u.href);
      if(finalUrl.origin!==u.origin || finalUrl.pathname!==u.pathname || finalUrl.search!==u.search) throw new Error('登录状态已变化，请重新登录');
      var text=await response.text();verify();var result;
      try {result=JSON.parse(text);}catch(error){throw new Error('课表接口返回不是 JSON，请重新登录');}
      if(!object(result) || result.success===false || result.success==='false')throw new Error('课表接口返回错误');
      return result;
    }
    function studentShape(result) {
      var tables=[];
      if(result.studentTableVm!==undefined)tables.push(result.studentTableVm);
      if(result.studentTableVms!==undefined){if(!Array.isArray(result.studentTableVms))return false;tables=tables.concat(result.studentTableVms);}
      return tables.length>0 && tables.every(function(t){return object(t)&&Array.isArray(t.activities);});
    }
    var result,kind='student';
    if(type==='shuwei_new') result=await request(selected.route);
    else if(type==='nwpu') result=await request(selected.base+'/for-std/course-table/semester/'+selected.term+'/print-data/'+selected.dataId+'?hasExperiment=true');
    else {
      var endpoint=selected.base+'/for-std/course-table/get-data?bizTypeId=', query='&semesterId='+selected.term+'&dataId='+selected.dataId;
      var data=await request(endpoint+selected.bizId+query);
      if(type==='jmu') {
        if(Object.prototype.hasOwnProperty.call(data,'lessonIds'))throw new Error('当前接口不是 studentTable 课表，请重新打开打印课表');
        if(!selected.studentId)throw new Error('页面没有唯一 studentId，请打开个人课表后重试');
        result=await request(selected.base+'/for-std/course-table/semester/'+selected.term+'/print-data/0/'+selected.studentId);
      } else {
        kind='hfu';
        if(!Array.isArray(data.lessonIds))throw new Error('课表响应中没有 lessonIds');
        if(!data.lessonIds.length)data=await request(endpoint+'23'+query);
        if(!Array.isArray(data.lessonIds) || !data.lessonIds.length)throw new Error('当前学期没有课程，请确认学期后重试');
        if(!data.lessonIds.every(function(v){return (typeof v!=='number'||Number.isSafeInteger(v)) && /^[1-9][0-9]*$/.test(scalar(v));}))throw new Error('课表课程标识格式无法识别');
        result=await request(selected.base+'/ws/schedule-table/datum',{lessonIds:data.lessonIds,studentId:type==='hfu'?selected.dataId:'',weekIndex:''});
      }
    }
    if(kind==='student' ? !studentShape(result) : !(object(result.result)&&Array.isArray(result.result.lessonList)&&Array.isArray(result.result.scheduleList)))
      throw new Error('课表响应结构无法识别，请确认学期和登录状态');
    verify();finish({ok:true,data:JSON.stringify(result),xnxq:selected.term,format:'treewise-'+type});
  } catch(error) {fail(String(error && error.message || error));}
})();"""
}
