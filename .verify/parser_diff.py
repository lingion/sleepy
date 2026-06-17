"""
检查真实 CSV 格式与当前 parser 的兼容性
1. 表头是否匹配
2. 周数格式能否解析
3. 节点是分两列（开始/结束）还是合并一列
"""
import sys

with open('/Users/lingion_k/Desktop/sleepy/.verify/real-sample.csv') as f:
    text = f.read()

print("=== Headers ===")
print(text.split(chr(10))[0])

print()
print("=== 当前 parser 的列识别逻辑（findCol）===")
print("  nameIdx: 课程名 / 课程 / 名称 / course / name")
print("  teacherIdx: 教师 / 老师 / teacher  ← 你的表头是'老师' ✓")
print("  roomIdx: 教室 / 位置 / 地点 / room / position  ← 你的表头是'地点' ✓")
print("  dayIdx: 星期 / 周几 / day  ← 你的表头是'星期' ✓")
print("  nodeIdx: 节次 / 节点 / node / 节 / class  ← 你的表头是'开始节数' 或 '结束节数'（都不匹配！）")
print("  weekIdx: 周次 / weeks / week  ← 你的表头是'周数' ✓")
print()
print("=== 问题诊断 ===")
print("  1. 你的表头是'开始节数'和'结束节数'，分两列")
print("     当前 parser 用 '1-2' 字符串格式识别节次")
print("     → 当前 parser 会找不到 nodeIdx 列，报错或退化为 type=0 + 全部 step=1")
print()
print("  2. 你的周数格式: '2-5,7-9,11-14'（多区间用逗号）")
print("     当前 parser 用 parseRange('1-16') → Pair(start, end)")
print("     → 遇到逗号会直接 fail，那行被跳过")
print()
print("结论: 真实课表 import 会失败/部分失败，需要再改 parser")
