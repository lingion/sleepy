# 拾光 shiguang_warehouse 264 校差距清单(可派单)

> 战役立项文档 · 2026-10-27 · 拾光仓库 main 浅克隆 + Sleepy schools.json(github/main @ 87a4e305)三源合并

## 1. 背景与结论口径

竞品「拾光课程表」的适配脚本仓库 `ShiGuangSchedule/shiguang_warehouse`(MIT License, Copyright 2025 星河欲转)以「仓库即数据库」模式维护 264 所学校的教务适配脚本。经与 Sleepy `app/src/main/assets/schools.json` 全量集合比对:

- 264/264 校在 Sleepy 目录**有条目**(校名归一 + 教务主机名双路 join,全命中)。
- 264/264 校在 Sleepy 目录 **enableFetch 开闸数为 0**——拾光已适配的全部学校,Sleepy 均未开在线抓取闸。
- 其中 165 校目录已注协议 type,99 校仅 pending 登记(无 type、无 URL)。
- Sleepy 不照搬拾光的远程 JS 执行架构;本清单把拾光适配当作**协议形态参考与开闸对照候选源**,每校落地仍走 jw-cross-verify SOP 全流程。

## 2. 动作分级(派单语义)

| 级别 | 含义 | 数量 |
|---|---|---|
| A 直接开闸对照 | 目录已注 type 且拾光入口与目录 URL 同主机,协议形态大概率一致;开闸前在线验证登录链 | 95 |
| B 入口比对后开闸 | 目录已注 type,但拾光入口主机不同(webvpn 包装/新域名);先比对入口再决定 | 70 |
| C 目录补 type 后开闸 | 目录仅 pending 登记;按拾光脚本形态补 type 后走开闸 | 99 |

## 3. 按 Sleepy parser 族分组

### zf_new(81 校)

正方 zf_new(jwapp/sys/homeapp 家族)。Sleepy 已有超集 parser,主要工作量=开闸对照。

动作分布:A 29 · B 30 · C 22

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 上海中侨职业技术大学 | SHZQ | cas.shzq.edu.cn | zf_new |
| B | 临沂大学 | LYU | sdp.lyu.edu.cn | zf_new |
| B | 南京信息职业技术学院 | NJCIT | jw.njcit.cn | zf_new |
| B | 喀什大学 | KSU | cas.ksu.edu.cn | zf_new |
| B | 塔里木大学 | TARU | auth.taru.edu.cn | zf_new |
| B | 大连大学 | DLU | webvpn.dlu.edu.cn | zf_new |
| B | 宣化科技职业学院 | XHKJZYXY | xhkjzyxy.edu.cn:18088 | zf_new |
| B | 山东水利职业学院 | SDWCVC | sso.sdwcvc.cn | zf_new |
| B | 广东技术师范大学 | GPNU | jwglxt.gpnu.edu.cn | zf_new |
| B | 广东科学技术职业学院 | GDIT | ids.gdit.edu.cn | zf_new |
| B | 广东科技学院 | GDUST | webvpn.gdust.edu.cn | zf_new |
| B | 徐州医科大学 | XZHMU | authserver.xzhmu.edu.cn | zf_new |
| B | 成都银杏酒店管理学院 | YXHMC | zfjw.gingkoc.edu.cn | zf_new |
| B | 江苏大学 | UJS | jwxt.ujs.edu.cn | zf_new |
| B | 江苏科技大学 | JUST | my.just.edu.cn | zf_new |
| B | 河南工程学院 | HAUE | 106.system.haue.edu.cn | zf_new |
| B | 济南大学 | UJN | sso.ujn.edu.cn | zf_new |
| B | 浙江工商职业技术学院 | ZJBTI | webvpn.zjbti.net.cn | zf_new |
| B | 浙江树人学院 | ZJSRU | xk.jwc.zjsru.edu.cn | zf_new |
| B | 浙江科技大学 | ZUST | https-authserver-zust-edu-cn-443.webvpn.zust.edu.cn | zf_new |
| B | 淮南师范学院 | HNNU | xxmh.hnnu.edu.cn | zf_new |
| B | 甘肃医学院 | GSMC | jw.gsmc.edu.cn | zf_new |
| B | 福建师范大学 | FJNU | gedu.fjnu.edu.cn | zf_new |
| B | 福建船政交通职业学院 | FJCPC | webvpn.fjcpc.edu.cn | zf_new |
| B | 茂名职业技术学院 | MMPT | jwc.mmpt.edu.cn | zf_new |
| B | 西南大学 | SWU | i.swu.edu.cn | zf_new |
| B | 西安理工大学 | XAUT | jwgl.xaut.edu.cn | zf_new |
| B | 西安科技大学 | XUST | jwsso.xust.edu.cn | zf_new |
| B | 重庆理工大学 | CQUT | timetable-cfc.cqut.edu.cn | zf_new |
| B | 齐齐哈尔工程学院 | QQHRIT | tysf.qqhrit.com:20261 | zf_new |
| C | 三明学院 | SMXY | one.fjsmu.edu.cn | —(pending) |
| C | 上海财经大学浙江学院 | SHUFEZJ | jwxt.shufe-zj.edu.cn | —(pending) |
| C | 中国地质大学(武汉) | CUG | sfrz.cug.edu.cn | —(pending) |
| C | 中国民航大学 | CAUC | http-jwgl-cauc-edu-cn-80.webvpn.cauc.edu.cn | —(pending) |
| C | 南京师范大学 | NJNU | authserver.nnu.edu.cn | —(pending) |
| C | 四川机电职业技术学院 | SCEMI | 192.168.73.155 | —(pending) |
| C | 安徽电气工程职业技术学院 | AEPU | zt.aepu.com.cn | —(pending) |
| C | 山东师范大学 | SDNU | jwxt.sdnu.edu.cn | —(pending) |
| C | 常州大学 | CCZU | zmvpn.cczu.edu.cn | —(pending) |
| C | 常州机电职业技术学院 | CZIMT | webapp.czimt.edu.cn | —(pending) |
| C | 广东药科大学 | GDPU | webvpn.gdpu.edu.cn | —(pending) |
| C | 广州理工学院 | GZIST | ids.gzist.edu.cn | —(pending) |
| C | 广州航海学院 | GZMTU | jwx.gzmtu.edu.cn | —(pending) |
| C | 德州学院 | DZU | 211.64.47.165 | —(pending) |
| C | 成都航空职业技术大学 | CAPU | jwxt.cap.edu.cn | —(pending) |
| C | 江西航空职业技术学院 | JHZYEDU | jw.jhzyedu.cn | —(pending) |
| C | 河南工业大学 | HAUT | jwglxt.haut.edu.cn | —(pending) |
| C | 甘肃财贸职业学院 | GSCMXY | jwxt.gscmxy.edu.cn | —(pending) |
| C | 肇庆学院 | ZQU | jwgl.zqu.edu.cn | —(pending) |
| C | 西南林业大学 | SWFU | jw.swfu.edu.cn | —(pending) |
| C | 许昌学院 | XCU | jwglxt.xcu.edu.cn | —(pending) |
| C | 贵州大学 | GZU | zhjw.gzu.edu.cn | —(pending) |
| A | 中国计量大学 | CJLU | jwxt.cjlu.edu.cn | zf_new |
| A | 华南师范大学 | SCNU | jwxt.scnu.edu.cn | zf_new |
| A | 华南理工大学 | SCUT | xsjw2018-jw.webvpn.scut.edu.cn | zf_new |
| A | 南京工业大学 | NJTECH | jwgl.njtech.edu.cn | zf_new |
| A | 南通大学 | NTU | tdjw.ntu.edu.cn | zf_new |
| A | 宁波工程学院 | NBUT | jwxt.nbut.edu.cn | zf_new |
| A | 山东华宇工学院 | HUAYU | jwgl.huayu.edu.cn | zf_new |
| A | 广东海洋大学 | GDOU | jw.gdou.edu.cn | zf_new |
| A | 广州新华学院 | XHSYSU | xhcas.xhsysu.edu.cn | zf_new |
| A | 广西机电职业技术学院 | GXCME | sso.gxcme.edu.cn | zf_new |
| A | 文华学院 | WENHUA | jw1.hustwenhua.net | zf_new |
| A | 无锡学院 | CWXU | jwgl.cwxu.edu.cn | zf_new |
| A | 枣庄学院 | UZZ | iam.uzz.edu.cn | zf_new |
| A | 江苏电子信息职业学院 | JSEI | webvpn.jsei.edu.cn | zf_new |
| A | 河北工程大学 | HEBEU | jwglxxfwpt.hebeu.edu.cn | zf_new |
| A | 河北经贸大学 | HUEB | 202-206-194-100.vpn.hueb.edu.cn:8118 | zf_new |
| A | 河南财经政法大学 | HUEL | xk.huel.edu.cn | zf_new |
| A | 济源职业技术学院 | JYVTC | jwgl.jyvtc.edu.cn | zf_new |
| A | 浙江万里学院 | ZWU | ehall.zwu.edu.cn | zf_new |
| A | 浙江中医药大学 | ZCMU | jwmk.zcmu.edu.cn | zf_new |
| A | 浙江工业大学 | ZJUT | www.gdjw.zjut.edu.cn | zf_new |
| A | 福州理工学院 | FIT | oaa.fitedu.net | zf_new |
| A | 聊城大学东昌学院 | LCUDCC | jw.lcudcc.edu.cn | zf_new |
| A | 西安文理学院 | XAWL | jwgl.xawl.edu.cn | zf_new |
| A | 重庆化工职业学院 | CQCIVC | dc.cqcivc.cn | zf_new |
| A | 重庆航天职业技术学院 | CQEPC | jw.cqepc.cn | zf_new |
| A | 重庆财经学院 | CFEC | jwmis.cfec.edu.cn | zf_new |
| A | 青岛理工大学 | QUT | jxgl.qut.edu.cn | zf_new |
| A | 青岛黄海学院 | QDHHC | jwxt.qdhhc.edu.cn | zf_new |

### qz(40 校)

强智 qz(for-std/course-table 家族)。Sleepy 已有 parser,主要工作量=开闸对照。

动作分布:A 20 · B 16 · C 4

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 上海立达学院 | LIDAPOLY | jw.lidapoly.edu.cn | qz |
| B | 东北林业大学 | NEFU | jwxt.nefu.edu.cn | qz |
| B | 中南大学 | CSU | ca.csu.edu.cn | qz |
| B | 中南林业科技大学 | CSUFT | webvpn.csuft.edu.cn | qz |
| B | 北京工商大学 | BTBU | jwgl.btbu.edu.cn | qz |
| B | 南华大学 | USC | jwzx.usc.edu.cn:8924 | qz |
| B | 宜宾学院 | YIBINU | authserver.yibinu.edu.cn | qz |
| B | 广州医科大学 | GZHMU | webvpn.gzhmu.edu.cn | qz |
| B | 广州华立学院 | HUALIXY | jw.hualixy.edu.cn | qz |
| B | 广西电力职业技术学院 | GXDLXY | jw.gxdlxy.com | qz |
| B | 暨南大学 | JNU | jw.jnu.edu.cn | qz |
| B | 湖南大学 | HNU | hdjw.hnu.edu.cn | qz |
| B | 绵阳师范学院 | MYSY | jw.mtc.edu.cn | qz |
| B | 重庆城市科技学院 | CQCST | jw.cqcst.edu.cn | qz |
| B | 重庆工商职业学院 | CQTBI | szxy.cqtbi.edu.cn | qz |
| B | 青海师范大学 | QHNU | one.qhnu.edu.cn | qz |
| C | 南方科技大学 | SUSTECH | cas.sustech.edu.cn | —(pending) |
| C | 武汉商学院 | WBU | jwxt.wbu.edu.cn | —(pending) |
| C | 湖北文理学院 | HBUAS | hbuas.jw.chaoxing.com | —(pending) |
| C | 西安石油大学 | XSYU | jiaowu.xsyu.edu.cn | —(pending) |
| A | 中南民族大学 | SCUEC | webvpn.scuec.edu.cn | qz |
| A | 云南财经大学 | YNUFE | xjwis.ynufe.edu.cn | qz |
| A | 仲恺农业工程学院 | ZHKU | edu-admin.zhku.edu.cn | qz |
| A | 南京林业大学 | NJFU | jwxt.njfu.edu.cn | qz |
| A | 吉林大学 | JLU | iedu.jlu.edu.cn | qz |
| A | 山东财经大学 | SDUFE | jw.sdufe.edu.cn | qz |
| A | 山东财经大学燕山学院 | YSSDUFE | ysjw.sdufe.edu.cn:8081 | qz |
| A | 广州松田职业学院 | GZST | jw.educationgroup.cn | qz |
| A | 怀化学院 | HHTC | jwmis.hhtc.edu.cn | qz |
| A | 新疆工程学院 | XJIE | authserver.xjie.edu.cn | qz |
| A | 武汉科技大学 | WUST | bkjx.wust.edu.cn | qz |
| A | 河北东方学院 | HBDFXY | 219.148.95.56:10000 | qz |
| A | 河北地质大学华信学院 | HBGUHX | 61.182.88.214:8090 | qz |
| A | 湖南三一工业职业技术学院 | SANYEDU | ehall.sanyedu.com | qz |
| A | 湖南工程学院 | HNIE | jwcmis.hnie.edu.cn | qz |
| A | 菏泽学院 | HZU | 222.206.176.102 | qz |
| A | 西安交通大学 | XJTU | ehall.xjtu.edu.cn | qz |
| A | 重庆交通大学 | CQJTU | jwgln.cqjtu.edu.cn | qz |
| A | 闽南科技学院 | MKU | jwgl.mku.edu.cn | qz |
| A | 青岛大学 | QDU | xjw.qdu.edu.cn | qz |

### urp_new(28 校)

URP 新版。Sleepy 有 urp_new parser,多为本专科教务,需逐校验证登录链。

动作分布:A 3 · B 1 · C 24

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 扬州大学 | YZU | webvpn.yzu.edu.cn | urp_new |
| C | 东北农业大学 | NEAU | webvpn.neau.edu.cn | —(pending) |
| C | 中国海洋大学 | OUC | id.ouc.edu.cn | —(pending) |
| C | 中国石油大学(华东) | UPC | webvpn.upc.edu.cn | —(pending) |
| C | 中山大学 | SYSU | jwxt.sysu.edu.cn | —(pending) |
| C | 兰州大学 | LZU | jwk.lzu.edu.cn | —(pending) |
| C | 南昌航空大学科技学院 | STCNCHU | qzjwxt.stcnchu.edu.cn:800 | —(pending) |
| C | 四川工商职业技术学院 | SCTBC | jw.sctbc.edu.cn | —(pending) |
| C | 山西工程职业学院 | SXGCXY | sxevc.jw.chaoxing.com | —(pending) |
| C | 广东交通职业技术学院 | GDCP | cas.gdcp.edu.cn | —(pending) |
| C | 广东科贸职业学院 | GDKM | aic.gdkm.edu.cn | —(pending) |
| C | 江苏师范大学 | JSNU | i.jsnu.edu.cn | —(pending) |
| C | 江西理工大学 | JXUST | authserver.jxust.edu.cn | —(pending) |
| C | 泉州职业技术大学 | QVTU | jw.qvtu.edu.cn | —(pending) |
| C | 淮北理工学院 | HBLGXY | a.hblgxy.edu.cn:5111 | —(pending) |
| C | 湖南信息职业技术学院 | HNIU | authserver.hniu.cn | —(pending) |
| C | 湖南科技学院 | HUSE | jw.huse.cn | —(pending) |
| C | 湖南邮电职业技术学院 | HNPTC | jx.hnxyjf.com:10081 | —(pending) |
| C | 福州职业技术大学 | FVTI | jw.webvpn.fvti.cn | —(pending) |
| C | 衡阳师范学院 | HYNU | hysfjw.hynu.edu.cn | —(pending) |
| C | 重庆人文科技学院 | CQRK | https-auth-cqrk-edu-cn-443.webvpn.cqrk.edu.cn:8480 | —(pending) |
| C | 重庆电子科技职业大学 | CQUET | cquet.edu.cn | —(pending) |
| C | 长春工程学院 | CCIT | webvpn.ccit.edu.cn | —(pending) |
| C | 青岛农业大学 | QAU | jwglxt.qau.edu.cn | —(pending) |
| C | 马鞍山学院 | MASU | jwxt.masu.edu.cn | —(pending) |
| A | 内蒙古大学 | IMU | jwxt.imu.edu.cn | urp_new |
| A | 天津科技大学 | TUST | id.tust.edu.cn | urp_new |
| A | 辽宁大学 | LNU | jwstudent.lnu.edu.cn | urp_new |

### classic_eams(16 校)

正方经典 EAMS(login.action / taskTable 家族),NBCC/南开战役同族,二次适配路径已有先例。

动作分布:A 7 · B 2 · C 7

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 南开大学 | NANKAI | yjs.nankai.edu.cn | classic_eams |
| B | 西安建筑科技大学 | XAUAT | swjw.xauat.edu.cn | classic_eams |
| C | 上海师范大学 | SHNU | course.shnu.edu.cn | —(pending) |
| C | 上海电力大学 | SHIEP | jw.shiep.edu.cn | —(pending) |
| C | 河南信息科技学院 | HIIT | 39.164.230.243:9527 | —(pending) |
| C | 河南理工大学 | HPU | zhjw.hpu.edu.cn | —(pending) |
| C | 郑州汽车工程职业学院 | ZZVCAE | cas.zzvcae.edu.cn | —(pending) |
| C | 重庆科技大学 | CQUST | web.cqust.edu.cn | —(pending) |
| C | 长江大学 | YANGTZEU | cas-yangtzeu-edu-cn.atrust.yangtzeu.edu.cn | —(pending) |
| A | 东北大学秦皇岛分校 | NEUQ | jwxt.neuq.edu.cn | classic_eams |
| A | 南京航空航天大学 | NUAA | aao-eas.nuaa.edu.cn | classic_eams |
| A | 合肥师范学院 | HFNU | jw.hfnu.edu.cn | classic_eams |
| A | 湖南师范大学 | HUNNU | jwglnew.hunnu.edu.cn | classic_eams |
| A | 电子科技大学 | UESTC | eams.uestc.edu.cn | classic_eams |
| A | 西安工业大学 | XATU | jwgl2018.xatu.edu.cn | classic_eams |
| A | 郑州航空工业管理学院 | ZUA | jwglxt.zua.edu.cn | classic_eams |

### wisedu(13 校)

综合信息平台 ehall。Sleepy 有 wisedu parser。

动作分布:A 9 · B 3 · C 1

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 华侨大学 | HQU | atrust.hqu.edu.cn | wisedu |
| B | 南京工业职业技术大学 | NIIT | jwxt.niit.edu.cn | wisedu |
| B | 新疆政法学院 | XJZFU | jwxt.xjzfu.edu.cn | wisedu |
| C | 西北农林科技大学 | NWAFU | newehall.nwafu.edu.cn | —(pending) |
| A | 三峡大学 | CTGU | jwxt.ctgu.edu.cn | wisedu |
| A | 中国科学技术大学 | USTC | id.ustc.edu.cn | wisedu |
| A | 兰州理工大学 | LUT | jwxt.lut.edu.cn | wisedu |
| A | 南京信息工程大学 | NUIST | jwxt.nuist.edu.cn | wisedu |
| A | 哈尔滨工程大学 | HRBEU | jwgl.hrbeu.edu.cn | wisedu |
| A | 大连工程学院 | DLUTCI | jw.dlutci.edu.cn | wisedu |
| A | 武汉船舶职业技术学院 | WHCB | jw.wspc.edu.cn | wisedu |
| A | 沈阳科技学院 | SYIST | jwxt.syist.edu.cn:30334 | wisedu |
| A | 河北农业大学 | HEBAU | urp.hebau.edu.cn:1009 | wisedu |

### kingo_new(7 校)

金格 kingo。Sleepy 有 kingo_new parser(WakeUp 战役修过三校)。

动作分布:A 4 · B 3 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 北京师范大学珠海校区 | BNUZH | jwxt.bnuzh.edu.cn | kingo_new |
| B | 厦门城市职业学院 | XMCU | eone-443.webvpn.xmcu.edu.cn | kingo_new |
| B | 河南职业技术学院 | HNZY | one.hnzj.edu.cn | kingo_new |
| A | 信阳农林学院 | XYAFU | sso.xyafu.edu.cn | kingo_new |
| A | 宿州学院 | AHSZU | 211.86.128.194 | kingo_new |
| A | 河南科技学院 | HIST | jwgl.hist.edu.cn | kingo_new |
| A | 泰山学院 | TSU | jwxt.tsu.edu.cn | kingo_new |

### cf(7 校)

长方 cf(老版)。

动作分布:A 6 · B 1 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 广东工业大学 | GDUT | authserver.gdut.edu.cn | cf |
| A | 南方医科大学 | SMU | zhjw.smu.edu.cn | cf |
| A | 成都中医药大学 | CDUTCM | jwweb.cdutcm.edu.cn | cf |
| A | 桂林医科大学 | GLMU | ejwc.glmu.edu.cn | cf |
| A | 江西飞行学院 | JXFU | jwxt.jxfu.edu.cn | cf |
| A | 济宁医学院 | JNMC | 210.44.16.13 | cf |
| A | 湖北医药学院 | HBMU | jw.hbmu.edu.cn | cf |

### cf_new(6 校)

长方 cf_new(cas/login?service 链)。

动作分布:A 4 · B 2 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 山东石油化工学院 | SDIPCT | webvpn.sdipct.edu.cn | cf_new |
| B | 广西医科大学 | GXMU | cas.gxmu.edu.cn | cf_new |
| A | 成都医学院 | CMC | jwweb.cmc.edu.cn | cf_new |
| A | 河南师范大学 | HNSF | jwc.htu.edu.cn | cf_new |
| A | 洛阳理工学院 | LIT | lljw.lit.edu.cn | cf_new |
| A | 绥化学院 | SHXY | jwgl.shxy.edu.cn | cf_new |

### shuwei_json(5 校)

数维 shuwei_json。

动作分布:A 2 · B 3 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 四川师范大学 | SICNU | 202.115.194.60 | shuwei_json |
| B | 大连海事大学 | DLMU | jw.xpaas.dlmu.edu.cn | shuwei_json |
| B | 西北工业大学 | NWPU | ecampus.nwpu.edu.cn | shuwei_json |
| A | 天津农学院 | TJAU | jwxt.tjau.edu.cn | shuwei_json |
| A | 成都信息工程大学 | CUIT | ywtb.cuit.edu.cn | shuwei_json |

### chaoxing(4 校)

超星。

动作分布:A 1 · B 3 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 河南城建学院 | HUUC | jwglxt.huuc.edu.cn:8061 | chaoxing |
| B | 贵州机电职业技术学院 | GZTCME | jwxt.gztcme.cn | chaoxing |
| B | 长沙工业学院 | CCSUT | auth.ccsut.cn | chaoxing |
| A | 辽宁科技大学 | USTL | ustl.jw.chaoxing.com | chaoxing |

### zf(3 校)

正方 zf 老家族(非 zf_new)。

动作分布:A 1 · B 2 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 南京邮电大学 | NJUPT | i.njupt.edu.cn | zf |
| B | 湖南农业大学 | HUNAU | webvpn.hunau.edu.cn | zf |
| A | 四川托普信息技术职业学院 | SCETOP | zf.scetop.com | zf |

### qz_with_node(2 校)

强智带 Node 中转形态。

动作分布:A 1 · B 1 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 北京邮电大学 | BUPT | jwgl.bupt.edu.cn | qz_with_node |
| A | 曲阜师范大学 | QFNU | zhjw.qfnu.edu.cn | qz_with_node |

### 待判定(41 校)

JS 静态特征不足以定族:多数拾光脚本走 webvpn 包装或通用 XHR,无协议指纹;需在线侦察后归族。

动作分布:A 0 · B 0 · C 41

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| C | 中国石油大学(北京) | CUP | bk.cup.edu.cn | —(pending) |
| C | 中国石油大学（北京）克拉玛依校区 | CUPK | eams.cupk.edu.cn | —(pending) |
| C | 佛山大学 | FOSU | 100.fosu.edu.cn | —(pending) |
| C | 北方工业大学 | NCUT | jwxtbk.ncut.edu.cn | —(pending) |
| C | 北部湾大学 | BBGU | zhjw.bbgu.edu.cn | —(pending) |
| C | 华东交通大学 | ECJTU | jwxt.ecjtu.edu.cn | —(pending) |
| C | 华中科技大学 | HUST | mhub.hust.edu.cn | —(pending) |
| C | 南昌科技职业大学 | THDM | jw.tellhowedu.com:19995 | —(pending) |
| C | 南昌航空大学 | NCHU | jwc-publish2.jwc.nchu.edu.cn | —(pending) |
| C | 南阳师范学院 | NYNU | vpn.nynu.edu.cn | —(pending) |
| C | 吉利学院 | GUC | jw.guc.edu.cn | —(pending) |
| C | 同济大学 | TONGJI | 1.tongji.edu.cn | —(pending) |
| C | 呼和浩特民族学院 | IMNC | webvpn.imnc.edu.cn | —(pending) |
| C | 哈尔滨工业大学 | HIT | yjsgl-hit-edu-cn.ivpn.hit.edu.cn:1080 | —(pending) |
| C | 天津城建大学 | TCU | authserver.tcu.edu.cn | —(pending) |
| C | 安徽中医药高等专科学校 | AHZYYGZ | authserver.ahzyygz.edu.cn | —(pending) |
| C | 安徽财经大学 | AUFE | all.aufe.edu.cn | —(pending) |
| C | 山东交通学院 | SDJTU | wwebvpn.sdjtu.edu.cn | —(pending) |
| C | 山东药品食品职业学院 | SDDFVC | cas.sddfvc.edu.cn | —(pending) |
| C | 山东轻工职业学院 | SDLIVC | crp.sdlivc.cn | —(pending) |
| C | 山西电子科技学院 | SXDZKJ | sso.sxdzkj.edu.cn | —(pending) |
| C | 广东轻工职业技术大学 | GDIPU | jw.gdipu.edu.cn | —(pending) |
| C | 徐州工程学院 | XZIT | jwglxt.xzit.edu.cn | —(pending) |
| C | 怀化职业技术学院 | HHVTC | jwgl.hhvtc.com.cn | —(pending) |
| C | 景德镇艺术职业大学 | JDZVUA | jwc.jdzvua.edu.cn | —(pending) |
| C | 桂林信息科技学院 | GUIT | 172-16-18-132.webvpn.guit.edu.cn | —(pending) |
| C | 梧州职业学院 | WZZY | 222.217.195.24:805 | —(pending) |
| C | 江苏旅游职业学院 | JSTC | authserver.jstc.edu.cn | —(pending) |
| C | 江西师范大学 | JXNU | jwc.jxnu.edu.cn | —(pending) |
| C | 河南科技大学 | HAUST | vpn.haust.edu.cn | —(pending) |
| C | 湖北民族大学 | HBMZU | jwgl.hbmzu.edu.cn | —(pending) |
| C | 湖北汽车工业学院 | HUAT | cas.huat.edu.cn | —(pending) |
| C | 湖南商务职业技术学院 | HNVCC | jwxt.hnvcc.edu.cn | —(pending) |
| C | 福建信息职业技术学院 | FJPIT | jw.fjpit.com | —(pending) |
| C | 苏州大学 | SUDA | xk.suda.edu.cn | —(pending) |
| C | 郑州大学 | ZZU | cas.s.zzu.edu.cn | —(pending) |
| C | 重庆工程学院 | CQIE | i.cqie.edu.cn | —(pending) |
| C | 铜仁学院 | GZTRC | gztrc.edu.cn:17232 | —(pending) |
| C | 长春理工大学 | CUST | kbpro.cust.edu.cn | —(pending) |
| C | 黑龙江大学 | HLJU | yjsglxt.hlju.edu.cn | —(pending) |
| C | 齐齐哈尔大学 | QQHRU | webvpn.qqhru.edu.cn | —(pending) |

### bjtu(1 校)

动作分布:A 0 · B 1 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 北京交通大学 | BJTU | mis.bjtu.edu.cn | bjtu |

### boya_pp(1 校)

动作分布:A 1 · B 0 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| A | 燕山大学 | YSU | cer.ysu.edu.cn | boya_pp |

### cqu(1 校)

动作分布:A 1 · B 0 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| A | 重庆大学 | CQU | my.cqu.edu.cn | cqu |

### cumtb(1 校)

动作分布:A 1 · B 0 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| A | 大连理工大学 | DLUT | jxgl.dlut.edu.cn | cumtb |

### hnust(1 校)

动作分布:A 1 · B 0 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| A | 湖南科技大学 | HNUST | kdjw.hnust.edu.cn | hnust |

### neu(1 校)

动作分布:A 1 · B 0 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| A | 东北大学 | NEU | jwxt.neu.edu.cn | neu |

### qz_old(1 校)

动作分布:A 0 · B 1 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 东北师范大学 | NENU | bkjx.nenu.edu.cn | qz_old |

### umooc(1 校)

动作分布:A 0 · B 1 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| B | 内蒙古工业大学 | IMUT | jw.imut.edu.cn | umooc |

### whut(1 校)

动作分布:A 1 · B 0 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| A | 武汉理工大学 | WHUT | jwxt.whut.edu.cn | whut |

### yethan(1 校)

动作分布:A 1 · B 0 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| A | 西南交通大学 | SWJTU | yhxt.swjtu.edu.cn | yethan |

### zf_1(1 校)

动作分布:A 1 · B 0 · C 0

| 动作 | 学校 | 拾光 ID | 拾光入口主机 | Sleepy 目录 type |
|---|---|---|---|---|
| A | 福建农林大学 | FAFU | jwgl.fafu.edu.cn | zf_1 |

## 4. 批次建议

1. **第一批(zf_new A 级 29 校 + qz A 级 20 校)**:协议族已在 Sleepy 主力 parser 覆盖内,开闸对照成本最低,与南开 PR #243 合入后的「zf_new/qz 密集省份战役」直接衔接。
2. **第二批(wisedu / kingo_new / cf / cf_new / chaoxing / shuwei_json 的 A+B 级)**:同族小批量,单 agent 每校走 SOP 全量流程。
3. **第三批(urp_new C 级 24 校)**:目录补 type 与在线侦察合一;URP 形态分散,预期个别校需 parser 增补。
4. **第四批(待判定 41 校)**:先在线侦察归族(拾光脚本接口路径是侦察起点),归族后回填本表重排批次。
5. 每校战役触达拾光脚本仓库时,按 SOP Step 5.5 致谢 shiguang_warehouse(MIT)。

## 5. 数据文件与口径说明

- `gap-rows.json`:264 校全字段机器可读清单(拾光 ID/名称/省份(部分)/协议族/族判定来源/adapter 数与分类/拾光 import_url/Sleepy 目录行 type·url·status·catalogId·enableFetch/同主机判定/动作)。
- 族判定:Sleepy 目录已注 type 为权威;99 校 pending 用拾光 school.js 静态特征(接口路径指纹,命中 ≥2 才记)判定;零命中记「待判定」。
- 省份列仅约 69/264 校校名可精确对上 MOE 城市表,缺失不影响派单(派单主键是协议族)。
