package com.lingion.sleepy.data.jw

/** WakeUp 6.6.2 share collector: selectList week count and getOtherLessons per week. */
object JwChaoxingShareCapture {
    const val URL_PATTERN = """^https?://kb\.chaoxing\.com/[^?#]*\.html\?[^#]+(?:#.*)?$"""
    fun isShareUrl(url: String): Boolean = Regex(URL_PATTERN).matches(url)

    const val FETCH_JS = """
(function(){
  var finished = false;
  var controller = new AbortController();
  var timer;
  function finish(payload) {
    if (finished) return;
    finished = true;
    clearTimeout(timer);
    window.__sleepyBridge.onWiseduResult(JSON.stringify(payload));
  }
  timer = setTimeout(function(){
    finish({ok:false, err:'读取分享课表超时，请重试'});
    controller.abort();
  }, 20000);
  try {
    var shareUrlPattern = /^https?:\/\/kb\.chaoxing\.com\/[^?#]*\.html\?[^#]+(?:#.*)?$/;
    if (!shareUrlPattern.test(location.href)) throw new Error('请先打开超星分享课表链接');
    var list = document.querySelector('.selectList');
    var maxWeek = list ? list.querySelectorAll('li').length : 0;
    if (!maxWeek) throw new Error('页面中未找到学期周次，请等待分享课表加载完成');
    var endpoint = new URL('https://kb.chaoxing.com/curriculum/getOtherLessons');
    endpoint.search = new URL(location.href).search;
    var lessons = [];
    function readWeek(week) {
      if (finished) return;
      if (week > maxWeek) {
        finish({ok:true, protocol:'chaoxing_share', data:JSON.stringify(lessons), periods:[]});
        return;
      }
      endpoint.searchParams.set('week', String(week));
      return fetch(endpoint.href, {method:'GET', credentials:'same-origin',
        headers:{'X-Requested-With':'XMLHttpRequest'}, signal:controller.signal})
        .then(function(response){
          if (!response.ok) throw new Error('读取第 ' + week + ' 周失败，HTTP ' + response.status);
          return response.json();
        }).then(function(response){
          if (finished) return;
          var rows = response && response.data && response.data.lessonArray;
          if (!Array.isArray(rows)) throw new Error('第 ' + week + ' 周响应缺少课程数组');
          lessons = lessons.concat(rows);
          return readWeek(week + 1);
        });
    }
    Promise.resolve(readWeek(1)).catch(function(error){finish({ok:false, err:String(error)});});
  } catch(error) {
    finish({ok:false, err:String(error)});
  }
})();
"""
}
