package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.data.jw.JwProtocol

/** Fetches every response page in the active school frame, preserving original course fields. */
object JwPagedSchoolCapture {
    fun scriptFor(type: String?): String? = when (type) {
        JwProtocol.TYPE_FSTVC, JwProtocol.TYPE_GDBH -> PAGED_SCHOOL_FETCH_JS.replace("__SCHOOL_TYPE__", type)
        else -> null
    }
}

private const val PAGED_SCHOOL_FETCH_JS = """(function(){
  var type='__SCHOOL_TYPE__', finished=false;
  var controller=typeof AbortController !== 'undefined' ? new AbortController() : null;
  var deadline=setTimeout(function(){ fail('课表读取超时，请重试'); },60000);
  function finish(result){
    if(finished)return;
    if(result.ok){try{samePage();}catch(e){result={ok:false,format:type,err:e.message};}}
    finished=true;clearTimeout(deadline);
    if(controller)controller.abort();
    window.__sleepyBridge.onWiseduResult(JSON.stringify(result));
  }
  function fail(message){ finish({ok:false,format:type,err:message}); }
  function object(value){ return value!==null && typeof value==='object' && !Array.isArray(value); }
  function scalar(value){ return typeof value==='string' || typeof value==='number' ? String(value).trim() : ''; }
  function candidateFrames(w,depth,candidates){
    if(depth>8)throw new Error('课表页面嵌套过深，请直接打开需要导入的课表');
    var children=[];
    try {
      var path=w.location.pathname;
      if(/^https?:$/.test(w.location.protocol) && (type==='fstvc' ? /\/studentportal\.php(?:\/|$)/.test(path) :
          /\/(?:Login\/MainDesktop|student(?:\/|$))/.test(path)))candidates.push(w);
      for(var i=0;i<w.frames.length;i++)children.push(w.frames[i]);
    }catch(e){}
    for(var j=0;j<children.length;j++)candidateFrames(children[j],depth+1,candidates);
  }
  function controlState(doc){
    var nodes=doc.querySelectorAll('input,select,textarea');
    if(nodes.length>10000)throw new Error('页面控件过多，请直接打开课表');
    return JSON.stringify(Array.prototype.map.call(nodes,function(n){
      return [n.tagName,n.name || '',n.id || '',n.type || '',scalar(n.value),!!n.checked,
        n.options ? Array.prototype.map.call(n.options,function(o){return [scalar(o.value),!!o.selected];}) : null];
    }));
  }
  var W,initialDocument,initialHref,initialControls;
  try{
    var candidates=[];candidateFrames(window,0,candidates);
    if(candidates.length!==1)throw new Error('请仅打开一个学生课表页面');
    W=candidates[0];initialDocument=W.document;initialHref=W.location.href;initialControls=controlState(initialDocument);
  }catch(e){fail(e.message);return;}
  function samePage(){
    if(finished)throw new Error('采集已结束');
    var candidates=[];candidateFrames(window,0,candidates);
    if(candidates.length!==1 || candidates[0]!==W || W.document!==initialDocument || W.location.href!==initialHref)
      throw new Error('采集期间课表页面发生变化，请重试');
    if(controlState(initialDocument)!==initialControls)throw new Error('采集期间所选学期或页面控件发生变化，请重试');
  }
  async function request(path,fields,html){
    samePage();
    var url=new URL(path,initialHref);
    if(url.origin!==W.location.origin || url.username || url.password)throw new Error('课表接口与当前页面不一致');
    var options={method:fields?'POST':'GET',credentials:'include',mode:'same-origin',redirect:'error'};
    if(controller)options.signal=controller.signal;
    if(fields)options.body=new URLSearchParams(fields);
    var response=await W.fetch(url.href,options);
    samePage();
    if(!response.ok)throw new Error('课表请求失败 HTTP '+response.status);
    var finalUrl=new URL(response.url || url.href,url.href);
    if(finalUrl.origin!==url.origin || finalUrl.pathname!==url.pathname || finalUrl.search!==url.search)
      throw new Error('登录状态已变化，请重新登录');
    var text=await response.text();
    samePage();
    if(html)return text;
    var data=JSON.parse(text);
    if(!object(data) || data.success===false || data.success==='false')throw new Error('课表接口未返回成功结果');
    return data;
  }
  function total(value){
    var text=scalar(value);
    if(!/^[0-9]+$/.test(text) || Number(text)>100000)throw new Error('课程总数无效');
    return Number(text);
  }
  async function fetchPages(fetchPage,key,size,requireTotal){
    var rows=[],knownTotal=null,seen=new Set(),first;
    for(var page=1;page<=4000;page++){
      var result=await fetchPage(page);
      if(!first)first=result;
      if(!Array.isArray(result[key]))throw new Error('课表接口未返回课程数组');
      var items=result[key];
      if(items.length>size)throw new Error('课表分页格式不正确');
      var count=result.total===undefined || result.total===null ? null : total(result.total);
      if(requireTotal && count===null)throw new Error('课表缺少课程总数');
      if(count!==null){
        if(knownTotal!==null && knownTotal!==count)throw new Error('采集期间课程总数发生变化，请重试');
        knownTotal=count;
      }
      if(knownTotal!==null && items.length!==Math.min(size,Math.max(0,knownTotal-rows.length)))throw new Error('课表分页不完整，请重试');
      if(items.length){
        var fingerprint=JSON.stringify(items);
        if(seen.has(fingerprint))throw new Error('课表接口返回重复页面，请重试');
        seen.add(fingerprint);
      }
      rows=rows.concat(items);
      if(rows.length>100000)throw new Error('课表数据过多');
      if(knownTotal!==null ? rows.length===knownTotal : items.length<size){first[key]=rows;return first;}
    }
    throw new Error('课表分页未结束');
  }
  function fstvcEndpoint(html,prefix){
    var table=new DOMParser().parseFromString(html,'text/html').querySelector('table#mainlist');
    var match=table && /\burl\s*:\s*(['"])(.*?)\1/.exec(table.getAttribute('data-options') || '');
    if(!match || !match[2].trim())throw new Error('未找到课表接口，请重新打开学习进度页面');
    var path=match[2].trim();
    if(/^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(path)){
      var absolute=new URL(path,W.location.href);
      if(absolute.origin!==W.location.origin || absolute.username || absolute.password)throw new Error('课表接口与当前页面不一致');
      path=absolute.href;
    }else{path=prefix+'/'+path.replace(/^\/+/, '');}
    var resolved=new URL(path,initialHref);
    if(resolved.origin!==W.location.origin || resolved.username || resolved.password)throw new Error('课表接口与当前页面不一致');
    return resolved.href;
  }
  async function fstvc(){
    var prefix=W.location.pathname.slice(0,W.location.pathname.indexOf('/studentportal.php'));
    var sourcePath=prefix+'/studentportal.php/Jxxx/xxjdxx';
    var path=fstvcEndpoint(await request(sourcePath,null,true),prefix);
    var result=await fetchPages(function(page){return request(path,{page:String(page),rows:'30',zc:'0',sort:'skrq',order:'asc'});},'rows',30,true);
    if(fstvcEndpoint(await request(sourcePath,null,true),prefix)!==path)throw new Error('采集期间所选课表接口发生变化，请重试');
    finish({ok:true,format:type,data:JSON.stringify(result.rows)});
  }
  async function gdbh(){
    var marker=/\/(?:Login\/MainDesktop|student(?:\/|$))/.exec(W.location.pathname);
    var prefix=W.location.pathname.slice(0,marker.index);
    var info=await request(prefix+'/student/StuInfo');
    var term=scalar(info.term);
    if(!term)throw new Error('未取得当前学期，请重新登录教务系统');
    var result=await fetchPages(function(page){
      return request(prefix+'/student/getstutable?_dc='+Date.now()+'&term='+encodeURIComponent(term)+'&page='+page+'&start='+((page-1)*50)+'&limit=50');
    },'data',50,false);
    var check=await request(prefix+'/student/StuInfo');
    if(scalar(check.term)!==term)throw new Error('采集期间所选学期发生变化，请重试');
    finish({ok:true,format:type,data:JSON.stringify(result)});
  }
  (type==='fstvc'?fstvc():gdbh()).catch(function(e){fail(e && e.message ? e.message : '课表读取失败');});
})();"""
