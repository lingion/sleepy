# 哈工程教务系统课表导入方案 (HEU jwgl)

domain: 金智 Wisedu jwapp 微应用平台
verified: 真实凭证真实数据全链路跑通 (2025089104)

## 1. 登录链路 (CAS SSO)
cas_base = https://cas.hrbeu.edu.cn/cas/login
captcha_api = /sso/apis/v2/open/captcha?imageWidth=100&captchaSize=4 → JSON {img:base64jpeg, token}
login_post = POST /cas/login (Content-Type: x-www-form-urlencoded)
  fields: username, password, captcha, token(验证码token), lt(登录页提取), execution=e1s1, _eventId=submit, source=cas
登录成功标志: Set-Cookie CASTGC=TGT-... + 响应加载 success.js (非 login.js)
密码: 明文 (publicKey 字段为空时不 RSA 加密; 若非空则需 RSA)
lt 提取: 登录页 HTML `name="lt" value="LT-..."`
验证码识别: ddddocr (Python) 实测准确; Android 需 onnxruntime 或手输回退

## 2. 教务系统入口
jwgl = https://jwgl.hrbeu.edu.cn/  → CAS service=.../jwapp/sys/emaphome/portal/index.do
持 CASTGC 访问 jwgl 自动签发 ST 完成 SSO; 落地 cookie = _WEU
课表 app = wdkb (我的课表), appId = wdkb-2018

## 3. 课表 API
当前学期: POST /jwapp/sys/wdkb/modules/jshkcb/dqxnxq.do → datas.dqxnxq.rows[0].DM (e.g. 2025-2026-2)
课表查询: POST /jwapp/sys/wdkb/modules/xskcb/xskcb.do  (data: XNXQDM=2025-2026-2)
  headers: Referer .../wdkb/*default/index.do, X-Requested-With: XMLHttpRequest
  返回: {code:"0", datas:{xskcb:{rows:[...]}}}
  注: 一门课多时间段 = 多 row (按 KCM 去重分组)

## 4. 字段映射 (教务 row → Sleepy CourseEntity)
KCM   课程名       → courseName
SKJS  上课教师     → teacher
JASMC 教室名称     → room
SKXQ  星期(1=周一..7=周日) → day
KSJC  开始节次     → startNode
JSJC  结束节次     → step = JSJC - KSJC + 1
SKZC  周次bitmap   → 周次: SKZC[i]=='1' 表示第(i+1)周上课 (0-indexed字符串, 位0=第1周)
ZCMC  周次可读(校验用) e.g. "4-5,7-16周"
KCH   课程号       → 去重/groupId 基
XNXQDM 学年学期     → 课表标识
XH/XM 学号/姓名     → 校验登录身份

## 5. 周次处理要点
SKZC 是 bitmap, 周次常不连续 (e.g. 4-5,7-16 跳第6周)
Sleepy CourseEntity 用 startWeek/endWeek + inWeek(); 不连续需:
  方案A: 按连续段拆成多 CourseEntity (同 groupId)
  方案B: CourseEntity 增加 weekBitmap 字段, inWeek() 查 bitmap
parse_weeks(skzc) = [i+1 for i,c in enumerate(skzc) if c=='1']

## 6. 实测数据 (验证用)
学期 2025-2026-1: 6 门课 / 10 时段 (线代/工数/心理/英语/体育/思修)
学期 2025-2026-2: 13 门课 / 34 时段 (体育二/概率/电路/近代史/军理/...)
多时段例: 线性代数 = 周二6-7节(4-5,7-16周) + 周四8-10节(4,6-15周)
