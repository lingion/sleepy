# 拾光 264 校批量开闸战役台账(2026-10-27)

用户裁决: A 类直接用开闸, B 类检查, C 类直接复用拾光数据但标记未实测。
数据源: shiguang_warehouse(拾光, MIT, 致谢已落 a4ab8bb5)264 校登记 +
逐校 curl 探针(/tmp/gate_probe_264.jsonl, 目录侧与拾光侧 URL 实打)。

## 处置总账

| 批次 | 行数 | 结果 |
|------|------|------|
| A 直接开闸 | 95 | 95 校已有运营 URL(42 baseline + 53 admissions 早已开闸); A 批 pending 2 校补登: 河北农业大学以拾光探针晋升 unverified, 大连工程学院被域名碰撞闸拦回 pending |
| B 入口比对 | 70 | 检查报告见下, 目录数据零改动 |
| C 目录补 | 99 | 41 校晋升 unverified(标记未实测), 探针非 200 拦 18, mappedType 不可路由或无 admission 拦 38, 已有 URL 2 |

晋升合计 43 校(8e3524cc), 全部 enableFetch=False, supported 有效计数
351 不动, 生成器 L55 断言保证拾光行永不 supported。

## C 批拦截明细(如实记录, 不硬开)

探针非 200(18 校): 安徽电气工程职业技术学院 302、安徽财经大学 403、
常州大学 302、佛山大学 0、广东药科大学 302、贵州大学 0、淮北理工学院 0、
哈尔滨工业大学 404、湖南商务职业技术学院 0、湖南科技学院 0、兰州大学 412、
南昌航空大学 0、四川机电职业技术学院 0、山东师范大学 0、上海电力大学 0、
南昌航空大学科技学院 0、苏州大学 0、武汉商学院 0。

mappedType 不可路由或无 admission(38 校): ziyan 15 校(成都航空职业技术大学、
重庆电子科技职业大学、长春理工大学、德州学院、甘肃财贸职业学院、桂林信息
科技学院、铜仁学院、怀化职业技术学院、景德镇艺术职业大学、山西工程职业学院、
山东药品食品职业学院、南阳师范学院、北方工业大学、郑州汽车工程职业学院、
山西电子科技学院——其中山西电子科技学院为名称匹配漏判复核: admission 名
带"(原山西师范大学现代文理学院)"转设注释后缀, 精确 join 未命中, 但即使命中
ziyan 也不可路由, 处置不变)、shuwei_m 2(上海师范大学、长江大学)、aic 2、
独立院校私有协议 14(ecjtu/huat/hust/hitsz/sysu/tju/xsyu_shuwei/qz_bjfu/
changzhou/jxnu/javtc/fstvc/hbmzu/login×2)、无 admission 决策 5(福州职业
技术大学、河南信息科技学院、山西电子科技学院[见上]、南昌科技职业大学、
梧州职业学院——五校 admission 表模糊名复核均无同名或近名决策)。

## B 批两侧探针检查报告(70 校)

目录侧 URL 全部重打(/tmp/b_probe_dir.jsonl), 拾光侧沿用战役探针。
检查结论: 拾光 URL 与目录 URL 不等价的 70 校中, 目录侧自身可达 56 校;
无任何一所因此改写运营 URL——B 类只检查不动数据, 修正候选待官方证据另批。

## B1-两侧均活-异入口(维持目录URL)(48 校)

| 校名 | 目录侧 URL | 目录侧 | 拾光侧 URL | 拾光侧 |
|---|---|---|---|---|
| 北京交通大学 | https://aa.bjtu.edu.cn/ | 200 | https://mis.bjtu.edu.cn/home/ | 200 |
| 北京师范大学珠海校区 | https://www.bnuzh.edu.cn | 200 | https://jwxt.bnuzh.edu.cn/caslogin | 200 |
| 北京工商大学 | https://sso.btbu.edu.cn/login | 200 | https://jwgl.btbu.edu.cn/ | 200 |
| 长沙工业学院 | https://csgyxy.jw.chaoxing.com/admin/login | 200 | https://auth.ccsut.cn/backstage/cas/login?service=https%3A | 200 |
| 重庆城市科技学院 | http://cquccjw.minghuaetc.com/cqdxcskjxy/ | 200 | http://jw.cqcst.edu.cn/cqdxcskjxy_jsxsd/ | 200 |
| 重庆工商职业学院 | https://jwgl.cqtbi.edu.cn/ | 200 | http://szxy.cqtbi.edu.cn/cqdddt/index.html?v=0.36722854908 | 200 |
| 重庆理工大学 | https://uis.cqut.edu.cn/unified_identity_logon/ | 200 | https://timetable-cfc.cqut.edu.cn/ | 200 |
| 中南大学 | http://csujwc.its.csu.edu.cn/jsxsd/ | 200 | https://ca.csu.edu.cn/authserver/login?service=http%3A%2F% | 200 |
| 大连海事大学 | https://id.dlmu.edu.cn/login | 200 | http://jw.xpaas.dlmu.edu.cn/eams | 200 |
| 福建船政交通职业学院 | https://sso.fjcpc.edu.cn/ZAS/login | 200 | https://webvpn.fjcpc.edu.cn/ | 200 |
| 福建师范大学 | https://jwglxt.fjnu.edu.cn/jwglxt/xtgl/login_slogin.html | 200 | https://gedu.fjnu.edu.cn/cas/login?service=https%3A%2F%2Fg | 200 |
| 广东科学技术职业学院 | http://jw.gdit.edu.cn/jwglxt/xtgl/login_slogin.html | 200 | https://ids.gdit.edu.cn/authserver/login?service=https://w | 200 |
| 广东科技学院 | https://portal.gdust.edu.cn/ | 200 | https://webvpn.gdust.edu.cn/https/77726476706e697374686562 | 200 |
| 广东技术师范大学 | https://cas-443.webvpn.gpnu.edu.cn/lyuapServer/login | 200 | https://jwglxt.gpnu.edu.cn/sso/lyiotlogin | 200 |
| 广西医科大学 | http://jwxt.gxmu.edu.cn/ | 200 | https://cas.gxmu.edu.cn/lyuapServer/login | 200 |
| 贵州机电职业技术学院 | https://gzjdvc.mh.chaoxing.com/page/135966/show | 200 | https://jwxt.gztcme.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.ht | 200 |
| 河南工程学院 | http://jwgl.haue.edu.cn/ | 200 | http://106.system.haue.edu.cn/jwglxt/xtgl/login_slogin.htm | 200 |
| 湖南大学 | https://cas.hnu.edu.cn/cas/login?service=http://hdjw.hnu.e | 200 | http://hdjw.hnu.edu.cn/jsxsd/sso.jsp | 200 |
| 河南职业技术学院 | https://we.hnzj.edu.cn/sso/login | 200 | https://one.hnzj.edu.cn/common-service/kcb-pc/personage/pe | 200 |
| 华侨大学 | https://jwc.hqu.edu.cn | 200 | https://atrust.hqu.edu.cn/portal/?redirectid=0.66689720847 | 200 |
| 广州华立学院 | https://www.hltz.net/hlxy_jsxsd/ | 200 | https://jw.hualixy.edu.cn/sso/ddlogin | 200 |
| 河南城建学院 | https://icas.huuc.edu.cn/cas/login | 200 | http://jwglxt.huuc.edu.cn:8061/admin/login | 200 |
| 内蒙古工业大学 | https://authserver.imut.edu.cn/cas/login | 200 | http://jw.imut.edu.cn/academic/login/imut/loginIds6Valid.j | 200 |
| 江苏科技大学 | https://www.just.edu.cn | 200 | https://my.just.edu.cn/ | 200 |
| 喀什大学 | http://jwc.ksu.edu.cn/ | 200 | https://cas.ksu.edu.cn/cas/login?service=https://jwnet.ksu | 200 |
| 上海立达学院 | https://shldxyjw.yinghuaonline.com/shldzyjsxy/ | 200 | http://jw.lidapoly.edu.cn/shldzyjsxy_jsxsd | 200 |
| 绵阳师范学院 | http://sso.mtc.edu.cn/login | 200 | http://jw.mtc.edu.cn/ | 200 |
| 东北师范大学 | https://authserver-443.webvpn.nenu.edu.cn/authserver/login | 200 | https://bkjx.nenu.edu.cn/ | 200 |
| 南京工业职业技术大学 | https://auth.njuit.edu.cn/cas/login | 200 | https://jwxt.niit.edu.cn/ | 200 |
| 南京信息职业技术学院 | https://i.njcit.cn | 200 | https://jw.njcit.cn/sso/lyiotlogin | 200 |
| 西北工业大学 | https://uis.nwpu.edu.cn/cas/login | 200 | https://ecampus.nwpu.edu.cn/ | 200 |
| 齐齐哈尔工程学院 | http://portal.qqhrit.com:2180/cas/login | 200 | http://tysf.qqhrit.com:20261/cas/login | 200 |
| 山东石油化工学院 | https://www.sdipct.edu.cn/ | 200 | https://webvpn.sdipct.edu.cn/https/77726476706e69737468656 | 200 |
| 山东水利职业学院 | https://jw.sdwcvc.edu.cn/eams/loginExt.action | 200 | https://sso.sdwcvc.cn/ | 200 |
| 上海中侨职业技术大学 | https://jw.shzq.edu.cn/jwglxt/xtgl/login_slogin.html | 200 | https://cas.shzq.edu.cn/cas/login?service=https://jw.shzq. | 200 |
| 四川师范大学 | http://jwcxk.sicnu.edu.cn/student/login | 200 | http://202.115.194.60/ | 200 |
| 江苏大学 | http://xuanke.ujs.edu.cn/ | 200 | https://jwxt.ujs.edu.cn/sso/jziotlogin | 200 |
| 南华大学 | http://61.187.179.66:8924/ | 200 | http://jwzx.usc.edu.cn:8924 | 200 |
| 西安建筑科技大学 | https://authserver.xauat.edu.cn/authserver/login | 200 | https://swjw.xauat.edu.cn/student/login/ | 200 |
| 西安理工大学 | http://jwc.xaut.edu.cn/ | 200 | http://jwgl.xaut.edu.cn/ | 200 |
| 新疆政法学院 | https://authserver.xjzfu.edu.cn/authserver/login | 200 | https://jwxt.xjzfu.edu.cn/jwapp/sys/homeapp/home/index.htm | 200 |
| 厦门城市职业学院 | https://jw.xmcu.edu.cn/ | 200 | https://eone-443.webvpn.xmcu.edu.cn/ | 200 |
| 西安科技大学 | https://jwc.xust.edu.cn/ | 200 | http://jwsso.xust.edu.cn/frontend/center_portal/home/index | 200 |
| 宜宾学院 | https://ehall.yibinu.edu.cn/new/index.html | 200 | https://authserver.yibinu.edu.cn/authserver/login?service= | 200 |
| 成都银杏酒店管理学院 | http://jwxt.gingkoc.edu.cn/xtgl/login_slogin.html | 200 | http://zfjw.gingkoc.edu.cn/xtgl/login_slogin.html | 200 |
| 扬州大学 | https://sso.yzu.edu.cn/login | 200 | https://webvpn.yzu.edu.cn/login | 200 |
| 浙江工商职业技术学院 | https://jwxt.zjgsu.edu.cn/jwglxt/xtgl/login_slogin.html | 200 | https://webvpn.zjbti.net.cn | 200 |
| 浙江科技大学 | http://zust.jxjy.chaoxing.com/login | 200 | https://https-authserver-zust-edu-cn-443.webvpn.zust.edu.c | 200 |

## B2-同主机一致(无需动作)(3 校)

| 校名 | 目录侧 URL | 目录侧 | 拾光侧 URL | 拾光侧 |
|---|---|---|---|---|
| 广东工业大学 | http://jxfw.gdut.edu.cn/ | 200 | https://authserver.gdut.edu.cn/authserver/login?service=ht | 200 |
| 暨南大学 | https://icas.jnu.edu.cn/cas/login | 200 | https://jw.jnu.edu.cn | 200 |
| 浙江树人学院 | https://rz.zjsru.edu.cn/cas/login | 200 | http://xk.jwc.zjsru.edu.cn/ | 200 |

## B3-仅目录侧活(维持目录URL)(5 校)

| 校名 | 目录侧 URL | 目录侧 | 拾光侧 URL | 拾光侧 |
|---|---|---|---|---|
| 北京邮电大学 | https://webvpn.bupt.edu.cn/login | 200 | https://jwgl.bupt.edu.cn/jsxsd/ | 0 |
| 广西电力职业技术学院 | https://ids.vpn.gxdlxy.com/authserver/login | 200 | https://jw.gxdlxy.com/jsxsd/framework/xsMain.jsp | 0 |
| 南京邮电大学 | http://jwxt.njupt.edu.cn/ | 200 | https://i.njupt.edu.cn/ | 302 |
| 青海师范大学 | http://idsnew.qhnu.edu.cn/authserver/login | 200 | http://one.qhnu.edu.cn/default/portal/index.jsp | 0 |
| 西南大学 | https://v4study.eduwest.com/login.shtml | 200 | https://i.swu.edu.cn/ | 412 |

## B4-仅拾光侧活(baseline修正候选,需官方证据)(12 校)

| 校名 | 目录侧 URL | 目录侧 | 拾光侧 URL | 拾光侧 |
|---|---|---|---|---|
| 中南林业科技大学 | http://jwgl.csuft.edu.cn/ | 0 | https://webvpn.csuft.edu.cn/ | 200 |
| 大连大学 | http://202.199.155.130/jwglxt | 0 | https://webvpn.dlu.edu.cn/login | 200 |
| 甘肃医学院 | https://115.155.160.104/jwglxt/xtgl/login_slogin.html | 0 | https://jw.gsmc.edu.cn/jwglxt/xtgl/login_slogin.html | 200 |
| 广州医科大学 | https://jwgl.gzhmu.edu.cn/jsxsd | 0 | https://webvpn.gzhmu.edu.cn/ | 200 |
| 淮南师范学院 | http://211.70.176.173/jwglxt/xtgl/ | 0 | https://xxmh.hnnu.edu.cn/zhxy/ | 200 |
| 湖南农业大学 | https://jwc.hunau.edu.cn/jwglxt/xtgl/login_slogin.html | 404 | https://webvpn.hunau.edu.cn/login?cas_login=true | 200 |
| 茂名职业技术学院 | http://jwc.mmvtc.cn/ | 0 | https://jwc.mmpt.edu.cn/xtgl/login_slogin.html | 200 |
| 东北林业大学 | http://jwcnew.nefu.edu.cn/dblydx_jsxsd/ | 0 | https://jwxt.nefu.edu.cn/jsxsd/ | 200 |
| 塔里木大学 | https://jwgl.taru.edu.cn/jwglxt/xtgl/login_slogin.html | 0 | https://auth.taru.edu.cn/cas/login?service=https%3A%2F%2Fh | 200 |
| 济南大学 | https://jwgl.ujn.edu.cn/jwglxt/xtgl/login_slogin.html | 403 | https://sso.ujn.edu.cn/tpass/login?service=http%3A%2F%2Fjw | 200 |
| 宣化科技职业学院 | https://manager.xhkjzyxy.edu.cn:1443/u.php | 502 | https://xhkjzyxy.edu.cn:18088/cas/login?service=https%3A%2 | 200 |
| 徐州医科大学 | https://jwpt.xzhmu.edu.cn/jwglxt/xtgl/login_slogin.html | 0 | https://authserver.xzhmu.edu.cn/authserver/login?service=h | 200 |

## B5-两侧皆不可达(保持现状待实测)(2 校)

| 校名 | 目录侧 URL | 目录侧 | 拾光侧 URL | 拾光侧 |
|---|---|---|---|---|
| 临沂大学 | http://jwgl.lyu.edu.cn/jwglxt | 0 | https://sdp.lyu.edu.cn/https/webvpn3146b55e2aeee09087ebc4e | 302 |
| 南开大学 | https://eamis.nankai.edu.cn/ | 0 | https://yjs.nankai.edu.cn/py/page/student/grkcb.htm | 403 |


### B 批处置决定

- B1(48)+B2(3)+B3(5): 维持目录 URL, 零动作。拾光入口不同不等于目录入口错,
  第三方声明无官方证据不得覆盖 baseline/已验证行(catalog_ownership 纪律)。
- B4(12): 记为 baseline 修正候选。目录侧死链/仅拾光侧活, 但修正
  需 official-school-link 级证据, 单拾光声明过不了 source_proves, 转后续
  官方证据批处理(与 withheld-new-unproven-owner 549 校同池)。
- B5(2): 南开大学目录侧 eamis 探针 0 为 curl 环境性失败(10-27 战役当日
  UA 空白页已知问题, 南开 #243 已真机合入 supported, 不受影响);
  临沂大学两侧皆不可达, 保持 unverified 现状待实测。

## A 批 classic_eams enableFetch 翻转清单(待用户逐校裁决, 本批零翻转)

JwWebViewLoginScreen L542 只在 type==classic_eams 且 enableFetch 时走
fetch 通路; 生成器对非 baseline 行恒置 enableFetch=False, baseline 行
保字节不动, 翻转属 baseline 运营配置变更, 须单独 commit。

| 校名 | 目录 status | 现 enableFetch |
|------|-------------|----------------|
| 合肥师范学院 | baseline(supported) | 无字段(False) |
| 湖南师范大学 | baseline(supported) | 无字段(False) |
| 东北大学秦皇岛分校 | baseline(supported) | 无字段(False) |
| 南京航空航天大学 | baseline(supported) | 无字段(False) |
| 电子科技大学 | baseline(supported) | 无字段(False) |
| 西安工业大学 | unverified(拾光批) | False |
| 郑州航空工业管理学院 | baseline(supported) | 无字段(False) |

模板: 南开 a4fc9e48 单校翻转形态。

## 域名碰撞闸实例(大连工程学院)

2024 年大连理工大学城市学院转设更名大连工程学院, 拾光入口
jw.dlutci.edu.cn/jwapp/sys/emapfunauth 与 baseline 运营行
jw.dlutci.edu.cn/jwapp/sys/emaphome 同 host。SchoolDomainMatch 按域名
反查遇同域双行即整域拒识, 强行开闸会静默破坏既有 baseline 校的域名查询,
fail-closed 拦回 pending 是正确裁决。转设承接关系若需开闸, 走
school_catalog_renames 显式映射 + 官方证据批, 不走批量探针。

## 复现命令

```
python3 -I scripts/shiguang_batch_promote.py --dry-run
python3 -I scripts/shiguang_batch_promote.py
python3 scripts/generate_school_catalog.py && python3 scripts/generate_school_catalog.py --check
python3 -m unittest test_catalog_ownership   # scripts/, 45 tests OK
./gradlew :app:testDebugUnitTest --tests Schools179CrossValidationTest \
  --tests SchoolsJsonConsistencyTest --tests XjuPostgraduateAdmissionTest \
  --tests AboutLicenseAttributionTest
```
