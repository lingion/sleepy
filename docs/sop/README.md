# SOPs

本目录存放项目工作规范（SOP）。

## 列表

| 文件 | 适用场景 | 入库 |
|---|---|---|
| `jw-cross-verify-sop.md` | 教务系统跨仓验证 / 新学校适配 / 旧学校 bug / 协议升级 | 是 |
| `release-sop.md` | 每次写 Release / 发版 / 出 APK 之前的执行规范 | **否（用户私有，本地保留）** |

`release-sop.md` 是用户的内部 SOP，仅本地使用：

- 文件留在仓库目录方便本地查阅；
- `.gitignore` 已把它永久排除 git 跟踪；
- 任何 `git add` / `git commit` / `git push` 都不会把它带到仓库历史或远端；
- 任何 release 相关动作（写 Release Notes、构建 APK、推 tag、发 GitHub Release）必须先读它。

> 触发规则：用户提出"打包 / 发版 / Release / 出新版本"等任一关键词，**第一步**先调 `release-sop` skill，按 SOP 执行。
