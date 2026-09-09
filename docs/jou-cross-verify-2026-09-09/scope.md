# 江苏海洋大学 (JOU) 跨仓验证 — scope

- 日期: 2026-09-09
- 学校: 江苏海洋大学 (Jiangsu Ocean University, 简称 JOU/江海大; 2019 年前名淮海工学院/HHIT — 检索须覆盖老校名)
- 类型: 初次适配 (课表导入)
- 用户原话: "新开一个分支适配江苏海洋大学。按照 SOP https://github.com/sunjingquan/jianghaidai-release/releases/tag/v0.6.1 可以参考这个项目，但它是闭源的，仅作为课表导入分析使用。"
- 仓内历史: schools.json 0 处 江苏海洋/江海大/hhit/jou.edu.cn — 首次收录
- 分支: `adapt/jou-jw` (worktree sleepy-worktrees/jou-adapt, 基于 main 376336f)

## 参考项目 (用户指定, 闭源)

sunjingquan/jianghaidai-release v0.6.1 — "酱海带" Flutter 校园 App (Discourse 社区 bbs.jianghaidai.com 的客户端 + 校园功能)。APK 内 libapp.so 字符串分析 (本机 2026-09-09, sha256 0724475b…0400a):

- 教务域: **zf.jou.edu.cn (老版正方 ZF, .aspx WebForms)**; 选课独立域 zfxk.jou.edu.cn (LegacyZfxkRsaCipher RSA, 与课表导入无关)
- 登录链: portal.jou.edu.cn (CAS) → zf.jou.edu.cn/login_cas.aspx → xs_main.aspx?xh=<学号>
- 课表页: **/xskbcx.aspx**; 选课页 /xsxk.aspx、/xf_xsqxxxk.aspx?gnmkdm=N121106、/xsxkqk.aspx
- 验证码: /CheckCode.aspx; 实验课表: /shiyan/student/sykebiao.aspx、xkkebiao.aspx
- 通知源: jwc.jou.edu.cn (教务处官网, RSSHub 同源); 实践/评测域 practice/tice.jou.edu.cn
