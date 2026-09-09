# [Bug]: 无法登入教务\n\n- Number: #14\n- State: closed\n- Author: Aster-poros\n- Created: 2026-09-05T09:12:52Z\n- Updated: 2026-09-05T16:57:00Z\n- URL: https://github.com/lingion/sleepy/issues/14\n\n## Body\n\n### Prerequisites

- [x] I am using the latest version of Sleepy
- [x] I have searched existing issues and found no duplicate
- [ ] I will attach logs, screenshots, or a sample schedule file if helpful

### Current behavior

教务直连里有武汉理工大学，但是在进入教务系统时出现错误，没有显示登入界面，而是Welcome come to EMAP.

### Expected behavior

应该是显示登入界面

### Steps to reproduce

在教务直连尝试进入武汉理工大学教务系统

### Schedule source

_No response_

### Additional context

_No response_

### Diagnostic info (auto-filled by app)

```markdown

```
\n\n## Comments\n\n### lingion — 2026-09-05T16:26:07Z\n\n已定位并修复。武汉理工大学教务入口之前会停在 `Welcome come to EMAP`，现在改为通过 `forceCas` 进入统一认证；抓取个人课表前也会切换到正确的学生角色。修复已包含在 Sleepy v1.0.49。感谢提供复现信息。
