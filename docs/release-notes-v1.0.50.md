# Sleepy v1.0.50

> Direct import adds Guangdong Medical University with the new `/kbcx/` path; Hefei University of Technology EAMS5 distinguishes login state from timetable data; 18 school entries corrected; about page credits 32 schools by project.
>
> 教务直连新增广东医科大学（支持新版 `/kbcx/` 路径）；合肥工业大学 EAMS5 区分登录态与课表数据；修正 18 所学校入口；关于页按学校聚合 32 所学校的致谢。

## What's New

### Guangdong Medical University: `/kbcx/` direct import

Guangdong Medical University has moved to the new Zhengfang `zftal-ui-v5` timetable path:

```
https://jw.gdmu.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default
```

Sleepy now accepts `/kbcx/`, `/jwglxt/`, and WebVPN `/http/<hex>/` as valid timetable prefixes. The internal bridge test forbids hardcoding `/jwglxt/kbcx`.

Guangdong Medical University has no public student-maintained adapter; the new path was confirmed from a user-submitted capture and is recorded as a capture acknowledgement.

### Hefei University of Technology EAMS5

EAMS5 distinguishes a live timetable from a login page.

- `studentId` matches six real-world forms (`{ studentId: '...' }`, `{studentId="..."}`, `var studentId = '...'`, and variants).
- A login page is detected before parse; expired sessions surface a clear error instead of being parsed as timetable data.

The four student-maintained repositories that informed the Hefei adapter are now all credited:

- HFUT-Schedule (Chiu-xaH, MIT)
- HfutOpenApi (BoynChan, MIT)
- hfut_schedule_hacker (Aoi-cn)
- django-hfut-auth (elonzh, MIT)

### About page: 32 schools grouped by project

The about page is reorganized. 29 flat credits become 9 cross-school projects and 32 school cards. Each school card is collapsed by default; tap to expand the projects used for that school. The long introduction paragraph is also collapsible.

## Fixes

- 18 school entries corrected — protocol, URL, and alias — so direct import opens them where it previously failed.
- Three missing Hefei University of Technology repository credits added.
- The boilerplate "report omissions via GitHub Issue" line is removed from the about page.

## Known Limitations

`https://jw.gdmu.edu.cn/` was probed before release: DNS resolves, HTTPS handshake times out (exit 28). The main university site `https://www.gdmu.edu.cn/` remains reachable.

This is a temporary state on the school's network. The import code path itself is unchanged; if the domain is still timing out when you tap Import, wait for the school to restore the endpoint.

## Verification

- Tests: 1088 cases, 0 failures, 0 errors.
- APK:
  - `app-arm64-v8a-release.apk` — 2,795,511 bytes, SHA-256 `290341f766294a6a33ddc8f9b06f4a4a6f636c0f226c179aea7e754582426e02`
  - `app-armeabi-v7a-release.apk` — 2,792,819 bytes, SHA-256 `d0bdbb9b7220706338772630623cf97224d742e3ad6e523ee2ed6a86822ce65c`
  - `app-x86_64-release.apk` — 2,794,617 bytes, SHA-256 `6d334e4f303915c36d88b0152827e66755240e37431d6e95c67bf2ef65d1912f`
- Build: versionName `1.0.50`, versionCode `51`

---

# Sleepy v1.0.50

> 教务直连新增广东医科大学（支持新版 `/kbcx/` 路径）；合肥工业大学 EAMS5 区分登录态与课表数据；修正 18 所学校入口；关于页按学校聚合 32 所学校的致谢。

## 新增功能

### 广东医科大学：支持裸 `/kbcx/` 路径

广东医科大学已迁移到正方新版 `zftal-ui-v5` 课表路径：

```
https://jw.gdmu.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default
```

Sleepy 现已接受 `/kbcx/`、`/jwglxt/` 和 WebVPN `/http/<hex>/` 作为合法前缀。桥接测试禁止硬编码 `/jwglxt/kbcx`。

广东医科大学暂无公开的学生维护适配器，新路径由用户采集包确认，按"采集包致谢"记录。

### 合肥工业大学 EAMS5

EAMS5 现在能区分真实课表与登录页：

- `studentId` 匹配六种实际写法：`{ studentId: '...' }`、`{studentId="..."}`、`var studentId = '...'` 及其变体。
- 抓取前先识别登录页；登录态过期时给出明确错误，不会被当成课表解析。

参考合工大协议形态的 4 个学生仓库现已全部致谢：

- HFUT-Schedule (Chiu-xaH, MIT)
- HfutOpenApi (BoynChan, MIT)
- hfut_schedule_hacker (Aoi-cn)
- django-hfut-auth (elonzh, MIT)

### 关于页：32 所学校按项目聚合

关于页重新组织：29 条散乱致谢 → 9 张跨校项目卡 + 32 张学校卡。每张学校卡默认收起，点开展开看该校参考的全部学生项目。原先长段的致谢说明也改为可折叠。

## 修复

- 修正 18 所学校的协议 / URL / 别名，使原先无法进入教务直连的学校恢复正常。
- 补齐合肥工业大学 3 条被遗漏的仓库致谢。
- 删除关于页"如遗漏请通过 GitHub Issue 告知"等冗余话术。

## 已知限制

发布前探测 `https://jw.gdmu.edu.cn/`：DNS 解析正常，HTTPS 握手超时（exit 28）。同期主站 `https://www.gdmu.edu.cn/` 可达。

这是学校侧的临时网络状态。导入代码路径本身未改变；若点击"导入此页"时 GDMU 教务域名仍超时，请等学校恢复正常后再导入。

## 验证

- 测试：1088 用例，0 失败 0 错误。
- APK：
  - `app-arm64-v8a-release.apk` — 2,795,511 bytes，SHA-256 `290341f766294a6a33ddc8f9b06f4a4a6f636c0f226c179aea7e754582426e02`
  - `app-armeabi-v7a-release.apk` — 2,792,819 bytes，SHA-256 `d0bdbb9b7220706338772630623cf97224d742e3ad6e523ee2ed6a86822ce65c`
  - `app-x86_64-release.apk` — 2,794,617 bytes，SHA-256 `6d334e4f303915c36d88b0152827e66755240e37431d6e95c67bf2ef65d1912f`
- 构建：versionName `1.0.50`，versionCode `51`
