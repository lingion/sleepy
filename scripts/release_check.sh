#!/usr/bin/env bash
#
# release_check.sh — v1.0.35 发布前置闸门 (承接 v1.0.34 TS-6)
#
# 用法: bash scripts/release_check.sh
# 铁律: 开头 testDebugUnitTest 失败即中止(exit 非零), 不进入后续门禁。
#
set -euo pipefail

cd "$(dirname "$0")/.."

echo "== [1/7] 单元测试 (testDebugUnitTest) =="
if ! ./gradlew :app:testDebugUnitTest; then
  echo "FATAL: testDebugUnitTest 失败, 中止发布检查" >&2
  exit 1
fi

echo
echo "== 机械门禁 7 项 (裁决四) =="

# (a) App 侧消费点: TodayScreen/CourseTableView 对 isWidgetColorless 零命中, isCourseColorless>=3
echo "--- (a) 消费点 gate ---"
grep -rn 'isWidgetColorless' \
  app/src/main/java/com/lingion/sleepy/ui/screen/today/TodayScreen.kt \
  app/src/main/java/com/lingion/sleepy/ui/component/CourseTableView.kt \
  && { echo "FAIL: isWidgetColorless 仍命中 App 侧消费点" >&2; exit 1; } || true
c3=$(grep -c 'isCourseColorless' \
  app/src/main/java/com/lingion/sleepy/ui/screen/today/TodayScreen.kt \
  app/src/main/java/com/lingion/sleepy/ui/component/CourseTableView.kt | awk -F: '{sum+=$2} END {print sum}')
echo "isCourseColorless 命中数 = $c3"
[ "$c3" -ge 3 ] || { echo "FAIL: isCourseColorless < 3" >&2; exit 1; }

# (b) B 卡无 refreshWidgets, A 卡保留
echo "--- (b) 开关卡 gate ---"
grep -n 'refreshWidgets' app/src/main/java/com/lingion/sleepy/ui/screen/mine/AppearanceScreen.kt

# (c) AppPrefs 两 key 无互读无播种
echo "--- (c) AppPrefs 隔离 gate ---"
grep -n 'KEY_WIDGET_COLORLESS\|KEY_COURSE_COLORLESS' \
  app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt

# (d) 两份 xml 规则含 sleepy_prefs.xml
echo "--- (d) 备份规则 gate ---"
grep -n 'sleepy_prefs.xml' \
  app/src/main/res/xml/backup_rules.xml \
  app/src/main/res/xml/data_extraction_rules.xml

# (e) 6 strings 均有 settings_course_colorless(_sub)
echo "--- (e) 6 strings gate ---"
for d in values values-en values-es values-ja values-zh-rCN values-zh-rTW; do
  c=$(grep -c 'settings_course_colorless' "app/src/main/res/$d/strings.xml")
  echo "$d: settings_course_colorless 命中数 = $c"
  [ "$c" -ge 2 ] || { echo "FAIL: $d settings_course_colorless < 2" >&2; exit 1; }
done

# (f) versionName=1.0.35 且 versionCode=36
echo "--- (f) 版本 gate ---"
grep -n 'versionName = "1.0.35"' app/build.gradle.kts
grep -n 'versionCode = 36' app/build.gradle.kts

# (g) changelog 含 inWeek 注记
echo "--- (g) changelog gate ---"
grep -n 'inWeek' CHANGELOG.md

echo
echo "release_check.sh 全部门禁通过 ✅"
