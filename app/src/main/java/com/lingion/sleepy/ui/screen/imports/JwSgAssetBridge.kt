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
 * SgAssetVendorInvariantTest 逐名锁死 (跨语言 invariant: shim 只做传输与
 * 无头自动应答, 解码唯一落点在 JwSgAssetParser)。
 *
 * 无头自动应答策略 (全量吸收 = 一律 unverified 的代价, 明码记账):
 *  - showAlert → true (确认继续)
 *  - showPrompt → 原样回填 defaultText
 *  - showSingleSelection → 回填 defaultSelectedIndex (<0 取 0)
 *  - savePresetTimeSlots / saveCourseConfig → 缓存 periods / semesterStartDate,
 *    随 saveImportedCourses 的 payload 一并回传 (既有 onWiseduResult 通道复用)
 *  - notifyTaskCompletion 在提交课程前到达 → 报「脚本未产出课表」而非静默超时
 */
object JwSgAssetBridge {

    const val MANIFEST_ASSET = "third_party/shiguang/manifest.json"

    data class SchoolEntry(val sgName: String, val jsAssetPath: String, val importUrl: String)

    private val manifestCache = ConcurrentHashMap<String, Map<String, SchoolEntry>?>()

    private fun nfk(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFKC)

    /** key = NFKC 归一校名; null 结果也缓存, 避免缺条目反复 IO。 */
    fun manifest(context: Context): Map<String, SchoolEntry>? {
        val cacheKey = context.applicationInfo.dataDir ?: "app"
        manifestCache[cacheKey]?.let { return it }
        val parsed = runCatching {
            val text = context.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val schools = root.getJSONArray("schools")
            buildMap {
                for (i in 0 until schools.length()) {
                    val s = schools.getJSONObject(i)
                    val adapters = s.optJSONArray("adapters") ?: continue
                    for (j in 0 until adapters.length()) {
                        val a = adapters.getJSONObject(j)
                        val jsPath = a.optString("jsPath")
                        if (jsPath.isBlank()) continue
                        put(nfk(s.getString("sgName")), SchoolEntry(s.getString("sgName"), jsPath, a.optString("importUrl")))
                        break
                    }
                }
            }.ifEmpty { null }
        }.getOrNull()
        manifestCache[cacheKey] = parsed
        return parsed
    }

    fun bridgeJs(context: Context, schoolName: String): String? {
        val entry = manifest(context)?.get(nfk(schoolName)) ?: return null
        val vendorJs = runCatching {
            context.assets.open(entry.jsAssetPath).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return SG_ASSET_SHIM_JS + "\n;SleepySgShim.install();\n" + vendorJs + "\n;SleepySgShim.finish();\n"
    }

    /** true = 该校有 vendor 适配脚本可用 (dispatch 前置判定)。 */
    fun hasAsset(context: Context, schoolName: String): Boolean =
        manifest(context)?.containsKey(nfk(schoolName)) == true

    /**
     * 桥接 shim。SG_ASSET_SHIM_JS 与 d.ts 的方法名一致性由
     * SgAssetVendorInvariantTest 断言 (读 vendor d.ts + 本字符串逐名核对)。
     */
    private const val SG_ASSET_SHIM_JS = """
(function(){
  if (window.SleepySgShim) return;
  var state = {submitted:false, completed:false, periods:[], startDate:''};
  function out(payload){ try{ window.__sleepyBridge.onWiseduResult(JSON.stringify(payload)); }catch(e){} }
  function promiseResult(v){ return new Promise(function(res){ res(v); }); }
  var promiseApi = {
    showAlert: function(title, content, confirmText){ return promiseResult(true); },
    showPrompt: function(title, tip, defaultText, validatorJsFunction){ return promiseResult(defaultText); },
    showSingleSelection: function(title, itemsJsonString, defaultSelectedIndex){
      var idx = (typeof defaultSelectedIndex === 'number' && defaultSelectedIndex >= 0) ? defaultSelectedIndex : 0;
      return promiseResult(idx);
    },
    saveImportedCourses: function(coursesJsonString){
      state.submitted = true;
      out({ok:true, data:String(coursesJsonString), periods:state.periods, startDate:state.startDate, sgAsset:'saveImportedCourses'});
      return promiseResult(true);
    },
    savePresetTimeSlots: function(timeSlotsJsonString){
      try {
        var slots = JSON.parse(timeSlotsJsonString);
        state.periods = slots.map(function(s){ return {node:s.number, start:s.startTime, end:s.endTime}; });
      } catch(e){ state.periods = []; }
      return promiseResult(true);
    },
    saveCourseConfig: function(configJsonString){
      try {
        var cfg = JSON.parse(configJsonString);
        if (cfg && cfg.semesterStartDate) state.startDate = String(cfg.semesterStartDate);
      } catch(e){}
      return promiseResult(true);
    },
    saveComboSchedule: function(comboScheduleJsonString){ return promiseResult(true); }
  };
  var syncApi = {
    showToast: function(message){},
    notifyTaskCompletion: function(){
      state.completed = true;
      if (!state.submitted) {
        out({ok:false, err:'拾光适配脚本已结束但未提交课表 (可能需要手动导航到课表页后重试)'});
      }
    }
  };
  window.SleepySgShim = {
    install: function(){
      window.shiguangBridgePromise = promiseApi;
      window.shiguangBridge = syncApi;
      window.AndroidBridgePromise = promiseApi;
      window.AndroidBridge = syncApi;
      window.__sgState = state;
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
