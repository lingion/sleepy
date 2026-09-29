#!/usr/bin/env bash
# bump-version.sh: 给定下一个版本号, 校验 docs/release-notes-v<ver>.md 存在,
# 打 git tag (annotated, 治理阶段4: 不再手改 build.gradle.kts)。
# release.yml 由 tag push 触发, 不需此脚本触发 CI。
#
# 用法: scripts/bump-version.sh vX.Y.Z  (tag 必须含 v 前缀)
# 前提: 工作区干净, 当前在要打 tag 的 commit 上, 目标 notes 已 staged/committed。
#
# 退出码: 0=成功; 1=版本号格式错; 2=notes 缺失; 3=工作区脏; 4=tag 已存在。

set -euo pipefail

VER="${1:-}"
if [[ -z "$VER" ]]; then
    echo "用法: $0 vX.Y.Z" >&2
    exit 1
fi
if [[ ! "$VER" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$ ]]; then
    echo "版本号格式错 (要求 vX.Y.Z 或 vX.Y.Z-rc.N): $VER" >&2
    exit 1
fi

NOTES="docs/release-notes-${VER}.md"
if [[ ! -f "$NOTES" ]]; then
    echo "缺 $NOTES — 发版前必须先写好双语 notes 并 commit" >&2
    exit 2
fi

if [[ -n "$(git status --porcelain)" ]]; then
    echo "工作区不干净, 先 commit/stash 现有改动再打 tag:" >&2
    git status --short >&2
    exit 3
fi

if git rev-parse "$VER" >/dev/null 2>&1; then
    echo "tag 已存在: $VER" >&2
    exit 4
fi

# annotated tag: 写完整 release 描述, 与 GitHub Release body 同源(单一份双语 notes)
MSG=$(cat "$NOTES")
# 去掉 # Sleepy vX.Y.Z 标题行(annotated tag subject 用版本号)
HEAD=$(head -1 "$NOTES")
git tag -a "$VER" -m "$HEAD

$MSG"
echo "✓ 已打本地 tag $VER"
echo "下一步: git push origin $VER (此动作需要用户明示批准)"
