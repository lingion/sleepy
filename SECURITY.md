# Security Policy / 安全策略

## Supported Versions / 支持的版本

只有最新发布的版本接收安全修复。请先升级到最新版再报告。
Only the latest release receives security fixes. Please upgrade before reporting.

## Reporting a Vulnerability / 报告漏洞

**不要用公开 issue 报告安全漏洞。**

- 邮件: lingion@hrbeu.edu.cn
- 或使用 GitHub 的 [私下报告漏洞](https://github.com/lingion/sleepy/security/advisories/new) 功能

请在报告中包含: 影响的版本、复现步骤、影响评估。收到后会确认，但个人项目，回复时间不承诺固定时限。

Do not open public issues for security vulnerabilities. Email the address above
or use GitHub private vulnerability reporting. Include affected version,
reproduction steps, and impact assessment. Reports are acknowledged when read;
as a personal project, no fixed response time is promised.

## Scope / 范围

Sleepy 是本地课表应用。特别关注:

- 导入解析路径的文件处理(外部打开 JSON/HTML/日历/CSV/XML 等 10 种 MIME、分享接收)
- WebView 教务直连的 JS 注入与凭据处理(Cookie 不出设备)
- 导出文件中的个人信息泄漏
