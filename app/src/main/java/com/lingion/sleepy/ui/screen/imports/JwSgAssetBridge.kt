package com.lingion.sleepy.ui.screen.imports

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * sg_asset 通路的运行时装配 — 上游协议形态: shiguang_warehouse (MIT, 星河欲转)。
 *
 * 职责:
 *  1. 按校查 vendor manifest (assets/third_party/shiguang/manifest.json,
 *     由 scripts/vendor_shiguang_assets.py 从拾光仓生成, 不手改);
 *  2. 产出「桥接 shim + 拾光适配 JS 原文」的一次性 evaluate JS。
 *
 * shim 合同 = vendor 仓 scripts/types/shiguang-bridge.d.ts 全部 9 个方法;
 * SgAssetVendorInvariantTest 逐名锁死。课程解码唯一落点在 JwSgAssetParser；
 * 公告、输入和单选通过原生弹窗等待用户，输入校验仍在来源页面执行。
 *  - 课程、作息和配置先暂存，notifyTaskCompletion 到达时统一回传，
 *    保留先保存课程再保存元数据的上游顺序。
 *  - notifyTaskCompletion 在提交课程前到达 → 报「脚本未产出课表」而非静默超时
 */
object JwSgAssetBridge {

    const val MANIFEST_ASSET = "third_party/shiguang/manifest.json"

    data class SchoolEntry(
        val sgName: String, val jsAssetPath: String, val importUrl: String,
        val adapterId: String, val adapterName: String, val category: String,
        val schoolId: String = "",
    )

    private val manifestCache = ConcurrentHashMap<String, Map<String, List<SchoolEntry>>>()

    /** An obsolete saved ID must not silently select a replacement, even if only one remains. */
    internal fun restoreChoice(entries: List<SchoolEntry>, savedId: String): SchoolEntry? =
        if (savedId.isEmpty()) entries.singleOrNull() else entries.singleOrNull { it.adapterId == savedId }

    private fun nfk(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFKC)

    /** Preserve every declaration, including multiple URLs using the same script. */
    internal fun parseManifest(text: String): Map<String, List<SchoolEntry>> {
        val schools = JSONObject(text).getJSONArray("schools")
        val result = linkedMapOf<String, MutableList<SchoolEntry>>()
        for (i in 0 until schools.length()) {
            val school = schools.getJSONObject(i)
            val name = school.getString("sgName")
            val adapters = school.optJSONArray("adapters") ?: continue
            for (j in 0 until adapters.length()) {
                val adapter = adapters.getJSONObject(j)
                val jsPath = adapter.optString("jsPath")
                if (jsPath.isBlank()) continue
                result.getOrPut(nfk(name)) { mutableListOf() }.add(SchoolEntry(
                    name, jsPath, adapter.optString("importUrl"), adapter.getString("adapterId"),
                    adapter.optString("adapterName"), adapter.optString("category"),
                    school.optString("sgId"),
                ))
            }
        }
        return result.mapValues { it.value.toList() }
    }

    fun manifest(context: Context): Map<String, List<SchoolEntry>> {
        val cacheKey = context.applicationInfo.dataDir ?: "app"
        manifestCache[cacheKey]?.let { return it }
        val parsed = runCatching {
            val text = context.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
            parseManifest(text)
        }.getOrDefault(emptyMap())
        manifestCache[cacheKey] = parsed
        return parsed
    }

    fun entries(context: Context, schoolName: String): List<SchoolEntry> =
        manifest(context)[nfk(schoolName)].orEmpty()

    fun bridgeJs(context: Context, schoolName: String, sessionId: String, selected: SchoolEntry? = null): String? {
        val candidates = entries(context, schoolName)
        val entry = if (selected == null) candidates.singleOrNull() else candidates.singleOrNull { it == selected }
        if (entry == null) return null
        val vendorJs = runCatching {
            context.assets.open(entry.jsAssetPath).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return SG_ASSET_SHIM_JS + "\n;SleepySgShim.install(${JSONObject.quote(sessionId)});\n" +
            vendorJs + "\n;SleepySgShim.finish();\n"
    }

    /** true = 该校有 vendor 适配脚本可用 (dispatch 前置判定)。 */
    fun hasAsset(context: Context, schoolName: String): Boolean =
        entries(context, schoolName).isNotEmpty()

    /**
     * 桥接 shim。SG_ASSET_SHIM_JS 与 d.ts 的方法名一致性由
     * SgAssetVendorInvariantTest 断言 (读 vendor d.ts + 本字符串逐名核对)。
     */
    private const val SG_ASSET_SHIM_JS = """
(function(){
  if (window.SleepySgShim) return;
  var state;
  function out(payload){ try{ window.__sleepyBridge.onWiseduResult(JSON.stringify(payload)); }catch(e){} }
  function promiseResult(v){ return new Promise(function(res){ res(v); }); }
  function active(){ return state && !state.cancelled && !state.completed; }
  function metadataFailure(message){
    state.failure = message;
    return Promise.reject(new Error(message));
  }
  function clock(value){
    return typeof value === 'string' && /^\d{2}:\d{2}$/.test(value) &&
      Number(value.slice(0,2)) < 24 && Number(value.slice(3)) < 60;
  }
  function validDate(value){
    if (typeof value !== 'string' || !/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value) || value.slice(0,4) === '0000') return false;
    var date = new Date(value + 'T00:00:00Z');
    return !isNaN(date.getTime()) && date.toISOString().slice(0,10) === value;
  }
  function cancelled(kind){ return kind === 'alert' ? false : null; }
  function request(kind, fields, validator){
    if (!active()) return Promise.resolve(cancelled(kind));
    return new Promise(function(resolve, reject){
      var id = ++state.nextRequest;
      var message = {sessionId:state.sessionId, requestId:id, kind:kind};
      Object.keys(fields).forEach(function(key){ message[key] = fields[key]; });
      state.pending[id] = {kind:kind, fields:fields, validator:validator, resolve:resolve, lastAttempt:0};
      try { window.__sleepyBridge.onSgInteraction(JSON.stringify(message)); }
      catch(e) { delete state.pending[id]; reject(new Error('无法打开导入交互窗口')); }
    });
  }
  function ack(id, attempt, accepted, error){
    window.__sleepyBridge.onSgInteractionAcknowledged(JSON.stringify({
      sessionId:state.sessionId, requestId:id, attemptId:attempt, accepted:accepted, error:error || ''
    }));
  }
  var promiseApi = {
    showAlert: function(title, content, confirmText){
      return request('alert', {title:String(title || ''),content:String(content || ''),confirmText:String(confirmText || '')});
    },
    showPrompt: function(title, tip, defaultText, validatorJsFunction){
      return request('prompt', {title:String(title || ''),tip:String(tip || ''),defaultText:String(defaultText || '')}, validatorJsFunction);
    },
    showSingleSelection: function(title, itemsJsonString, defaultSelectedIndex){
      try {
        var items = JSON.parse(itemsJsonString);
        if (!Array.isArray(items) || !items.length || !items.every(function(item){ return typeof item === 'string'; })) {
          throw new Error('选项列表无效');
        }
        var idx = Number.isInteger(defaultSelectedIndex) && defaultSelectedIndex >= 0 && defaultSelectedIndex < items.length
          ? defaultSelectedIndex : -1;
        return request('selection', {title:String(title || ''),items:items,defaultSelectedIndex:idx});
      } catch(e) { return Promise.reject(e); }
    },
    saveImportedCourses: function(coursesJsonString){
      if (!active()) return Promise.reject(new Error('采集 session 已结束或取消'));
      state.submitted = true;
      // SUDA/HAUE 等适配器会在课程之后提交作息和配置，完成通知前只暂存。
      state.data = String(coursesJsonString);
      return promiseResult(true);
    },
    savePresetTimeSlots: function(timeSlotsJsonString){
      if (!active()) return Promise.reject(new Error('采集 session 已结束或取消'));
      try {
        var slots = JSON.parse(timeSlotsJsonString);
        if (!Array.isArray(slots) || !slots.length || slots.length > 366) throw new Error('基础作息必须是完整节次数组');
        slots.forEach(function(slot,index){
          if (!slot || slot.number !== index + 1 || !clock(slot.startTime) || !clock(slot.endTime) ||
              slot.startTime >= slot.endTime || index > 0 && slots[index - 1].endTime > slot.startTime) {
            throw new Error('基础作息编号或钟点无效');
          }
        });
        state.periods = slots.map(function(s){ return {node:s.number, start:s.startTime, end:s.endTime}; });
      } catch(e){ return metadataFailure('来源基础作息无效，请检查后重新导入'); }
      return promiseResult(true);
    },
    saveCourseConfig: function(configJsonString){
      if (!active()) return Promise.reject(new Error('采集 session 已结束或取消'));
      try {
        var cfg = JSON.parse(configJsonString);
        if (!cfg || typeof cfg !== 'object' || Array.isArray(cfg)) throw new Error('配置必须是对象');
        Object.keys(cfg).forEach(function(key){
          var value = cfg[key];
          if (key === 'semesterStartDate' && value !== null && !validDate(value)) throw new Error('学期日期无效');
          if (key === 'semesterTotalWeeks' && (!Number.isInteger(value) || value < 1 || value > 366)) throw new Error('学期周数无效');
          if (key === 'firstDayOfWeek' && (!Number.isInteger(value) || value < 1 || value > 7)) throw new Error('每周首日无效');
          if (key === 'defaultClassDuration' || key === 'defaultBreakDuration') {
            if (!Number.isSafeInteger(value) || value < (key === 'defaultClassDuration' ? 1 : 0)) throw new Error('课程或课间时长无效');
          }
        });
        Object.keys(cfg).forEach(function(key){ state.config[key] = cfg[key]; });
        if (Object.prototype.hasOwnProperty.call(cfg, 'semesterStartDate')) {
          state.startDate = cfg.semesterStartDate === null ? '' : cfg.semesterStartDate;
        }
      } catch(e){ return metadataFailure('来源课表配置无效，请检查后重新导入'); }
      return promiseResult(true);
    },
    saveComboSchedule: function(comboScheduleJsonString){
      if (!active()) return Promise.reject(new Error('采集 session 已结束或取消'));
      try {
        var combo = JSON.parse(comboScheduleJsonString);
        if (!combo || typeof combo !== 'object' || Array.isArray(combo) ||
            !Array.isArray(combo.publicSchedules) || !combo.publicSchedules.length ||
            combo.publicSchedules.length > 366) throw new Error('组合方案结构无效');
        combo.publicSchedules.forEach(function(schedule){
          if (!schedule || typeof schedule !== 'object' || !validDate(schedule.startDate) ||
              !validDate(schedule.endDate) || schedule.startDate > schedule.endDate ||
              !Array.isArray(schedule.timeSlots)) throw new Error('组合方案日期或作息无效');
          schedule.timeSlots.forEach(function(slot,index){
            if (!slot || !Number.isInteger(slot.number) || slot.number < 1 ||
                !clock(slot.startTime) || !clock(slot.endTime) || slot.startTime >= slot.endTime ||
                index > 0 && (schedule.timeSlots[index - 1].number >= slot.number ||
                    schedule.timeSlots[index - 1].endTime > slot.startTime)) {
              throw new Error('组合方案节次无效');
            }
          });
        });
        state.comboSchedule = combo;
      } catch(e) { return metadataFailure('来源组合作息无效，请检查后重新导入'); }
      return promiseResult(true);
    }
  };
  var syncApi = {
    showToast: function(message){},
    notifyTaskCompletion: function(){
      if (!active()) return;
      state.completed = true;
      var unanswered = Object.keys(state.pending).length > 0;
      Object.keys(state.pending).forEach(function(id){
        var pending = state.pending[id]; pending.resolve(cancelled(pending.kind));
      });
      state.pending = {};
      if (state.failure || unanswered) {
        out({ok:false,captureId:state.sessionId,err:state.failure || '来源尚有未完成的导入选择，请重新导入'});
      } else if (state.submitted) {
        out({ok:true, captureId:state.sessionId, data:state.data, periods:state.periods, startDate:state.startDate, sgConfig:state.config, comboSchedule:state.comboSchedule || null, sgAsset:'saveImportedCourses'});
      } else {
        out({ok:false, captureId:state.sessionId, err:'拾光适配脚本已结束但未提交课表 (可能需要手动导航到课表页后重试)'});
      }
    }
  };
  window.SleepySgShim = {
    install: function(sessionId){
      // 来源异步代码可能在稍后读取全局 API。换 session 必须换文档，不能原页重装。
      if (state) throw new Error('再次导入前请重新加载页面');
      state = {sessionId:sessionId, submitted:false, completed:false, cancelled:false,
        nextRequest:0,pending:{},data:'', periods:[], startDate:'', config:{}, comboSchedule:null, failure:''};
      window.shiguangBridgePromise = promiseApi;
      window.shiguangBridge = syncApi;
      window.AndroidBridgePromise = promiseApi;
      window.AndroidBridge = syncApi;
      window.__sgState = state;
    },
    respond: function(sessionId, requestId, attemptId, value){
      if (!active() || state.sessionId !== sessionId) return;
      var pending = state.pending[requestId];
      if (!pending || !Number.isInteger(attemptId) || attemptId <= pending.lastAttempt) return;
      pending.lastAttempt = attemptId;
      var error = '';
      if (pending.kind === 'alert') {
        if (typeof value !== 'boolean') error = '请确认或取消';
      } else if (value !== null && pending.kind === 'prompt') {
        if (typeof value !== 'string') error = '输入内容无效';
        else if (pending.validator) {
          try {
            var validator = window[pending.validator];
            if (typeof validator !== 'function') error = '输入校验函数不存在';
            else {
              var validation = validator(value);
              if (validation !== false) error = typeof validation === 'string' && validation ? validation : '输入校验未通过';
            }
          } catch(e) { error = '输入校验失败，请重试'; }
        }
      } else if (value !== null && pending.kind === 'selection') {
        if (!Number.isInteger(value) || value < 0 || value >= pending.fields.items.length) error = '请选择有效选项';
      }
      ack(requestId, attemptId, !error, error);
      if (error) return;
      delete state.pending[requestId];
      pending.resolve(value);
    },
    cancelSession: function(sessionId){
      if (!state || state.sessionId !== sessionId) return;
      state.cancelled = true;
      Object.keys(state.pending).forEach(function(id){
        var pending = state.pending[id]; pending.resolve(cancelled(pending.kind));
      });
      state.pending = {};
    },
    finish: function(){
      // 同步段跑完仍未提交: 不立刻报错 — 脚本多为 async; 超时闸兜底。
      // 但同步即抛错的场景由外层 try/catch 处理。
    },
    _out: out
  };
})();
"""
}
