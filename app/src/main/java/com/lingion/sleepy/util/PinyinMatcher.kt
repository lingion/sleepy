package com.lingion.sleepy.util

import java.util.Locale

/**
 * 拼音首字母搜索匹配器（轻量级，零依赖）
 */
object PinyinMatcher {

    private val FIRST_LETTER: Array<String?> by lazy {
        val arr = arrayOfNulls<String>(20902)
        for (entry in BASIC_MAP) {
            val ch = entry.first
            val letter = entry.second
            val code = ch.code
            if (code in 0x4E00..0x9FA5) {
                arr[code - 0x4E00] = letter
            }
        }
        arr
    }

    fun firstLetterOf(ch: Char): String? {
        if (ch !in '一'..'鿿') return null
        val idx = ch.code - 0x4E00
        return if (idx in FIRST_LETTER.indices) FIRST_LETTER[idx] else null
    }

    fun namePinyinShort(name: String, sortKey: String): String {
        val sb = StringBuilder()
        if (sortKey.length == 1 && sortKey[0].isLetter()) {
            sb.append(sortKey.lowercase(Locale.getDefault()))
        }
        for (ch in name) {
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9') {
                sb.append(ch.toString().lowercase(Locale.getDefault()))
            } else {
                val fl = firstLetterOf(ch)
                if (fl != null) sb.append(fl.lowercase(Locale.getDefault()))
            }
        }
        return sb.toString()
    }

    fun match(name: String, sortKey: String, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase(Locale.getDefault())
        if (name.lowercase(Locale.getDefault()).contains(q)) return true
        val py = namePinyinShort(name, sortKey)
        return py.contains(q)
    }

    // 精选 1500 常用汉字首字母表
    private val BASIC_MAP: List<Pair<Char, String>> = listOf(
        '啊' to "A", '阿' to "A", '埃' to "A", '挨' to "A", '哎' to "A", '唉' to "A",
        '爱' to "A", '矮' to "A", '安' to "A", '暗' to "A", '昂' to "A", '袄' to "A", '澳' to "A",
        '八' to "B", '巴' to "B", '白' to "B", '百' to "B", '办' to "B", '半' to "B",
        '帮' to "B", '包' to "B", '报' to "B", '北' to "B", '本' to "B", '比' to "B",
        '边' to "B", '便' to "B", '变' to "B", '遍' to "B", '标' to "B", '表' to "B",
        '别' to "B", '宾' to "B", '冰' to "B", '并' to "B", '病' to "B", '波' to "B",
        '伯' to "B", '博' to "B", '不' to "B", '布' to "B", '步' to "B", '保' to "B",
        '蚌' to "B", '滨' to "B", '渤' to "B",
        '才' to "C", '材' to "C", '采' to "C", '彩' to "C", '菜' to "C", '参' to "C",
        '操' to "C", '草' to "C", '测' to "C", '层' to "C", '查' to "C", '产' to "C",
        '长' to "C", '常' to "C", '场' to "C", '厂' to "C", '唱' to "C", '超' to "C",
        '朝' to "C", '车' to "C", '称' to "C", '城' to "C", '成' to "C", '程' to "C",
        '吃' to "C", '出' to "C", '初' to "C", '除' to "C", '处' to "C", '川' to "C",
        '春' to "C", '词' to "C", '此' to "C", '从' to "C", '存' to "C", '错' to "C",
        '创' to "C", '沧' to "C", '潮' to "C", '承' to "C", '池' to "C", '重' to "C", '崇' to "C",
        '楚' to "C", '纯' to "C", '慈' to "C", '磁' to "C", '崔' to "C", '巢' to "C",
        '大' to "D", '打' to "D", '带' to "D", '代' to "D", '单' to "D", '但' to "D",
        '当' to "D", '到' to "D", '道' to "D", '得' to "D", '的' to "D", '灯' to "D",
        '等' to "D", '低' to "D", '地' to "D", '第' to "D", '点' to "D", '电' to "D",
        '调' to "D", '定' to "D", '东' to "D", '冬' to "D", '懂' to "D", '动' to "D",
        '都' to "D", '读' to "D", '短' to "D", '段' to "D", '对' to "D", '多' to "D",
        '达' to "D", '丹' to "D", '德' to "D", '迪' to "D", '丁' to "D", '端' to "D",
        '饿' to "E", '儿' to "E", '而' to "E", '二' to "E", '额' to "E",
        '发' to "F", '法' to "F", '翻' to "F", '反' to "F", '饭' to "F", '方' to "F",
        '房' to "F", '防' to "F", '放' to "F", '飞' to "F", '非' to "F", '分' to "F",
        '风' to "F", '封' to "F", '佛' to "F", '服' to "F", '复' to "F", '副' to "F",
        '富' to "F", '付' to "F", '福' to "F", '凤' to "F", '扶' to "F", '府' to "F",
        '阜' to "F",
        '高' to "G", '告' to "G", '哥' to "G", '歌' to "G", '格' to "G", '个' to "G",
        '各' to "G", '给' to "G", '跟' to "G", '更' to "G", '工' to "G", '公' to "G",
        '功' to "G", '够' to "G", '故' to "G", '顾' to "G", '观' to "G", '管' to "G",
        '光' to "G", '广' to "G", '贵' to "G", '国' to "G", '果' to "G", '过' to "G",
        '甘' to "G", '戈' to "G", '共' to "G", '古' to "G", '关' to "G", '官' to "G",
        '桂' to "G",
        '哈' to "H", '还' to "H", '孩' to "H", '海' to "H", '害' to "H", '韩' to "H",
        '汉' to "H", '行' to "H", '好' to "H", '号' to "H", '和' to "H", '河' to "H",
        '何' to "H", '合' to "H", '黑' to "H", '很' to "H", '红' to "H", '后' to "H",
        '湖' to "H", '互' to "H", '户' to "H", '花' to "H", '华' to "H", '话' to "H",
        '划' to "H", '化' to "H", '画' to "H", '怀' to "H", '坏' to "H", '欢' to "H",
        '环' to "H", '换' to "H", '黄' to "H", '回' to "H", '会' to "H", '活' to "H",
        '火' to "H", '或' to "H", '获' to "H", '杭' to "H", '贺' to "H", '横' to "H",
        '虹' to "H", '虎' to "H", '滑' to "H", '淮' to "H", '桓' to "H",
        '湟' to "H", '惠' to "H", '珲' to "H", '辉' to "H", '汇' to "H", '卉' to "H", '晖' to "H",
        '邯' to "H", '菏' to "H", '鹤' to "H", '宏' to "H", '洪' to "H", '侯' to "H", '呼' to "H",
        '加' to "J", '家' to "J", '价' to "J", '驾' to "J", '架' to "J", '假' to "J",
        '尖' to "J", '坚' to "J", '间' to "J", '检' to "J", '简' to "J", '建' to "J",
        '件' to "J", '健' to "J", '将' to "J", '江' to "J", '奖' to "J", '讲' to "J",
        '交' to "J", '教' to "J", '角' to "J", '叫' to "J", '接' to "J", '街' to "J",
        '结' to "J", '姐' to "J", '解' to "J", '介' to "J", '今' to "J", '金' to "J",
        '紧' to "J", '进' to "J", '近' to "J", '京' to "J", '经' to "J", '精' to "J",
        '井' to "J", '景' to "J", '警' to "J", '净' to "J", '究' to "J", '九' to "J",
        '酒' to "J", '旧' to "J", '就' to "J", '举' to "J", '句' to "J", '剧' to "J",
        '决' to "J", '觉' to "J", '军' to "J", '均' to "J", '济' to "J", '继' to "J",
        '纪' to "J", '基' to "J", '集' to "J", '技' to "J", '计' to "J", '记' to "J",
        '寄' to "J", '佳' to "J", '嘉' to "J", '甲' to "J", '监' to "J", '减' to "J",
        '见' to "J", '荐' to "J", '箭' to "J", '剑' to "J", '鉴' to "J", '疆' to "J",
        '蒋' to "J", '降' to "J", '酱' to "J", '蕉' to "J", '胶' to "J", '焦' to "J",
        '礁' to "J", '狡' to "J", '轿' to "J", '较' to "J", '阶' to "J", '截' to "J",
        '杰' to "J", '洁' to "J", '捷' to "J", '睫' to "J", '竭' to "J", '戒' to "J",
        '届' to "J", '借' to "J", '巾' to "J", '津' to "J", '尽' to "J", '晋' to "J",
        '浸' to "J", '禁' to "J", '茎' to "J", '惊' to "J", '晶' to "J", '睛' to "J",
        '鲸' to "J", '颈' to "J", '竞' to "J", '敬' to "J", '靖' to "J", '境' to "J",
        '静' to "J", '镜' to "J", '救' to "J", '舅' to "J", '居' to "J", '局' to "J",
        '菊' to "J", '橘' to "J", '矩' to "J", '巨' to "J", '拒' to "J", '具' to "J",
        '距' to "J", '聚' to "J", '据' to "J", '卷' to "J", '倦' to "J", '绝' to "J",
        '掘' to "J", '俊' to "J", '峻' to "J", '集' to "J", '吉' to "J", '暨' to "J",
        '开' to "K", '看' to "K", '靠' to "K", '科' to "K", '可' to "K", '渴' to "K",
        '刻' to "K", '客' to "K", '肯' to "K", '空' to "K", '口' to "K", '苦' to "K",
        '哭' to "K", '酷' to "K", '快' to "K", '宽' to "K", '况' to "K", '矿' to "K",
        '昆' to "K", '喀' to "K", '凯' to "K",
        '拉' to "L", '来' to "L", '兰' to "L", '蓝' to "L", '懒' to "L", '浪' to "L",
        '老' to "L", '乐' to "L", '了' to "L", '雷' to "L", '冷' to "L", '离' to "L",
        '里' to "L", '理' to "L", '力' to "L", '历' to "L", '利' to "L", '立' to "L",
        '连' to "L", '联' to "L", '脸' to "L", '练' to "L", '良' to "L", '两' to "L",
        '亮' to "L", '量' to "L", '料' to "L", '林' to "L", '灵' to "L", '零' to "L",
        '领' to "L", '另' to "L", '流' to "L", '六' to "L", '龙' to "L", '楼' to "L",
        '路' to "L", '旅' to "L", '绿' to "L", '律' to "L", '篮' to "L", '廊' to "L",
        '朗' to "L", '捞' to "L", '劳' to "L", '勒' to "L", '累' to "L", '丽' to "L",
        '栗' to "L", '莲' to "L", '廉' to "L", '凉' to "L", '梁' to "L", '粮' to "L",
        '辽' to "L", '列' to "L", '临' to "L", '邻' to "L", '岭' to "L", '令' to "L",
        '柳' to "L", '聋' to "L", '笼' to "L", '隆' to "L", '陇' to "L", '漏' to "L",
        '陆' to "L", '录' to "L", '鹿' to "L", '滤' to "L", '卵' to "L",
        '乱' to "L", '轮' to "L", '论' to "L", '罗' to "L", '螺' to "L", '洛' to "L", '落' to "L",
        '莱' to "L", '陵' to "L",
        '吗' to "M", '妈' to "M", '马' to "M", '买' to "M", '满' to "M", '忙' to "M",
        '毛' to "M", '冒' to "M", '没' to "M", '每' to "M", '美' to "M", '妹' to "M",
        '门' to "M", '们' to "M", '梦' to "M", '米' to "M", '面' to "M", '民' to "M",
        '名' to "M", '明' to "M", '命' to "M", '母' to "M", '木' to "M", '目' to "M",
        '玛' to "M", '麻' to "M", '麦' to "M", '曼' to "M", '漫' to "M", '慢' to "M",
        '芒' to "M", '茫' to "M", '猫' to "M", '茅' to "M", '茂' to "M", '贸' to "M",
        '梅' to "M", '煤' to "M", '萌' to "M", '蒙' to "M", '盟' to "M", '猛' to "M",
        '弥' to "M", '秘' to "M", '密' to "M", '蜜' to "M", '绵' to "M", '棉' to "M",
        '免' to "M", '勉' to "M", '苗' to "M", '描' to "M", '秒' to "M", '妙' to "M",
        '庙' to "M", '灭' to "M", '敏' to "M", '鸣' to "M", '摩' to "M", '模' to "M",
        '膜' to "M", '磨' to "M", '魔' to "M", '末' to "M", '莫' to "M", '墨' to "M",
        '默' to "M", '漠' to "M", '陌' to "M", '谋' to "M", '某' to "M", '牡' to "M",
        '亩' to "M", '幕' to "M", '慕' to "M", '穆' to "M",
        '那' to "N", '哪' to "N", '南' to "N", '难' to "N", '脑' to "N", '呢' to "N",
        '内' to "N", '能' to "N", '你' to "N", '年' to "N", '念' to "N", '鸟' to "N",
        '牛' to "N", '农' to "N", '弄' to "N", '女' to "N", '暖' to "N", '拿' to "N",
        '纳' to "N", '乃' to "N", '奈' to "N", '男' to "N", '囊' to "N", '恼' to "N",
        '闹' to "N", '妮' to "N", '尼' to "N", '泥' to "N", '娘' to "N", '酿' to "N",
        '尿' to "N", '捏' to "N", '您' to "N", '宁' to "N", '凝' to "N", '扭' to "N",
        '奴' to "N", '努' to "N", '怒' to "N", '诺' to "N",
        '哦' to "O", '欧' to "O", '偶' to "O",
        '怕' to "P", '拍' to "P", '排' to "P", '派' to "P", '判' to "P", '旁' to "P",
        '跑' to "P", '朋' to "P", '片' to "P", '漂' to "P", '票' to "P", '平' to "P",
        '评' to "P", '破' to "P", '普' to "P", '爬' to "P", '帕' to "P", '牌' to "P",
        '潘' to "P", '盘' to "P", '盼' to "P", '庞' to "P", '胖' to "P", '抛' to "P",
        '炮' to "P", '泡' to "P", '培' to "P", '赔' to "P", '陪' to "P", '配' to "P",
        '佩' to "P", '喷' to "P", '盆' to "P", '彭' to "P", '蓬' to "P", '膨' to "P",
        '碰' to "P", '批' to "P", '披' to "P", '皮' to "P", '疲' to "P", '匹' to "P",
        '辟' to "P", '篇' to "P", '偏' to "P", '骗' to "P", '飘' to "P", '拼' to "P",
        '贫' to "P", '品' to "P", '聘' to "P", '乒' to "P", '苹' to "P", '凭' to "P",
        '屏' to "P", '瓶' to "P", '萍' to "P", '坡' to "P", '泼' to "P", '婆' to "P",
        '魄' to "P", '扑' to "P", '铺' to "P", '葡' to "P", '朴' to "P", '谱' to "P",
        '期' to "Q", '齐' to "Q", '其' to "Q", '起' to "Q", '气' to "Q", '千' to "Q",
        '前' to "Q", '钱' to "Q", '浅' to "Q", '强' to "Q", '桥' to "Q", '切' to "Q",
        '亲' to "Q", '青' to "Q", '清' to "Q", '情' to "Q", '请' to "Q", '秋' to "Q",
        '求' to "Q", '球' to "Q", '区' to "Q", '取' to "Q", '去' to "Q", '全' to "Q",
        '确' to "Q", '群' to "Q", '七' to "Q", '漆' to "Q", '奇' to "Q", '歧' to "Q",
        '旗' to "Q", '企' to "Q", '启' to "Q", '弃' to "Q", '汽' to "Q", '契' to "Q",
        '砌' to "Q", '器' to "Q", '迁' to "Q", '签' to "Q", '潜' to "Q", '枪' to "Q",
        '墙' to "Q", '抢' to "Q", '悄' to "Q", '巧' to "Q", '茄' to "Q", '且' to "Q",
        '侵' to "Q", '勤' to "Q", '琴' to "Q", '禽' to "Q", '轻' to "Q", '晴' to "Q",
        '庆' to "Q", '穷' to "Q", '曲' to "Q", '驱' to "Q", '趋' to "Q", '圈' to "Q",
        '权' to "Q", '泉' to "Q", '拳' to "Q", '犬' to "Q", '劝' to "Q", '缺' to "Q",
        '却' to "Q", '裙' to "Q",
        '然' to "R", '让' to "R", '热' to "R", '人' to "R", '认' to "R", '任' to "R",
        '日' to "R", '容' to "R", '肉' to "R", '如' to "R", '入' to "R", '软' to "R",
        '若' to "R", '燃' to "R", '染' to "R", '壤' to "R", '嚷' to "R", '饶' to "R",
        '扰' to "R", '绕' to "R", '惹' to "R", '忍' to "R", '扔' to "R", '荣' to "R",
        '柔' to "R", '乳' to "R", '瑞' to "R", '润' to "R", '弱' to "R",
        '三' to "S", '色' to "S", '山' to "S", '上' to "S", '少' to "S", '社' to "S",
        '设' to "S", '生' to "S", '声' to "S", '十' to "S", '时' to "S", '事' to "S",
        '是' to "S", '手' to "S", '书' to "S", '数' to "S", '双' to "S", '谁' to "S",
        '水' to "S", '顺' to "S", '说' to "S", '思' to "S", '四' to "S", '送' to "S",
        '速' to "S", '酸' to "S", '算' to "S", '岁' to "S", '所' to "S", '孙' to "S",
        '萨' to "S", '塞' to "S", '散' to "S", '桑' to "S", '扫' to "S", '森' to "S",
        '杀' to "S", '沙' to "S", '善' to "S", '伤' to "S", '商' to "S", '赏' to "S",
        '尚' to "S", '绍' to "S", '蛇' to "S", '深' to "S", '神' to "S", '审' to "S",
        '升' to "S", '省' to "S", '盛' to "S", '剩' to "S", '失' to "S", '师' to "S",
        '诗' to "S", '施' to "S", '石' to "S", '识' to "S", '实' to "S", '史' to "S",
        '使' to "S", '始' to "S", '士' to "S", '世' to "S", '市' to "S", '示' to "S",
        '式' to "S", '势' to "S", '侍' to "S", '饰' to "S", '试' to "S", '视' to "S",
        '适' to "S", '室' to "S", '释' to "S", '收' to "S", '守' to "S", '首' to "S",
        '寿' to "S", '受' to "S", '售' to "S", '授' to "S", '输' to "S", '熟' to "S",
        '术' to "S", '束' to "S", '树' to "S", '丝' to "S", '司' to "S",
        '松' to "S", '宋' to "S", '搜' to "S", '苏' to "S", '素' to "S", '宿' to "S",
        '诉' to "S", '随' to "S", '损' to "S", '缩' to "S", '索' to "S", '锁' to "S", '沈' to "S",
        '他' to "T", '她' to "T", '太' to "T", '台' to "T", '谈' to "T", '汤' to "T",
        '糖' to "T", '特' to "T", '体' to "T", '天' to "T", '条' to "T", '听' to "T",
        '同' to "T", '头' to "T", '土' to "T", '团' to "T", '推' to "T", '腿' to "T",
        '退' to "T", '托' to "T", '塔' to "T", '态' to "T", '摊' to "T", '潭' to "T",
        '坦' to "T", '叹' to "T", '炭' to "T", '探' to "T", '唐' to "T", '倘' to "T",
        '躺' to "T", '涛' to "T", '套' to "T", '腾' to "T", '田' to "T", '甜' to "T",
        '填' to "T", '挑' to "T", '跳' to "T", '铁' to "T", '亭' to "T", '停' to "T",
        '挺' to "T", '通' to "T", '铜' to "T", '童' to "T", '统' to "T", '痛' to "T",
        '偷' to "T", '投' to "T", '透' to "T", '突' to "T", '图' to "T", '徒' to "T",
        '涂' to "T", '吐' to "T", '兔' to "T", '脱' to "T", '泰' to "T",
        '外' to "W", '完' to "W", '玩' to "W", '晚' to "W", '万' to "W", '王' to "W",
        '为' to "W", '位' to "W", '文' to "W", '问' to "W", '我' to "W", '无' to "W",
        '五' to "W", '午' to "W", '物' to "W", '挖' to "W", '瓦' to "W", '弯' to "W",
        '湾' to "W", '腕' to "W", '亡' to "W", '网' to "W", '往' to "W", '忘' to "W",
        '微' to "W", '围' to "W", '违' to "W", '委' to "W", '伟' to "W", '尾' to "W",
        '味' to "W", '卫' to "W", '温' to "W", '闻' to "W", '翁' to "W", '窝' to "W",
        '沃' to "W", '卧' to "W", '握' to "W", '乌' to "W", '伍' to "W", '武' to "W",
        '务' to "W", '雾' to "W", '误' to "W", '芜' to "W", '皖' to "W",
        '西' to "X", '希' to "X", '息' to "X", '习' to "X", '洗' to "X", '喜' to "X",
        '系' to "X", '下' to "X", '夏' to "X", '先' to "X", '现' to "X", '相' to "X",
        '想' to "X", '向' to "X", '象' to "X", '消' to "X", '小' to "X", '笑' to "X",
        '校' to "X", '些' to "X", '写' to "X", '谢' to "X", '心' to "X", '新' to "X",
        '信' to "X", '兴' to "X", '星' to "X", '行' to "X", '幸' to "X", '性' to "X",
        '修' to "X", '需' to "X", '许' to "X", '续' to "X", '学' to "X", '雪' to "X",
        '寻' to "X", '夕' to "X", '吸' to "X", '悉' to "X", '惜' to "X", '稀' to "X",
        '溪' to "X", '锡' to "X", '熙' to "X", '膝' to "X", '席' to "X", '袭' to "X",
        '细' to "X", '虾' to "X", '瞎' to "X", '侠' to "X", '厦' to "X", '纤' to "X",
        '掀' to "X", '鲜' to "X", '闲' to "X", '弦' to "X", '贤' to "X", '咸' to "X",
        '嫌' to "X", '显' to "X", '险' to "X", '限' to "X", '线' to "X", '香' to "X",
        '箱' to "X", '湘' to "X", '乡' to "X", '详' to "X", '响' to "X", '项' to "X",
        '巷' to "X", '像' to "X", '橡' to "X", '肖' to "X", '萧' to "X", '晓' to "X",
        '孝' to "X", '效' to "X", '歇' to "X", '协' to "X", '邪' to "X", '斜' to "X",
        '蟹' to "X", '芯' to "X", '辛' to "X", '欣' to "X", '形' to "X", '型' to "X",
        '醒' to "X", '杏' to "X", '姓' to "X", '兄' to "X", '胸' to "X", '雄' to "X",
        '休' to "X", '秀' to "X", '袖' to "X", '绣' to "X", '虚' to "X", '绪' to "X",
        '宣' to "X", '悬' to "X", '旋' to "X", '选' to "X", '穴' to "X", '血' to "X",
        '巡' to "X", '讯' to "X", '迅' to "X", '襄' to "X", '邢' to "X",
        '呀' to "Y", '亚' to "Y", '言' to "Y", '颜' to "Y", '眼' to "Y", '演' to "Y",
        '验' to "Y", '阳' to "Y", '样' to "Y", '要' to "Y", '也' to "Y", '业' to "Y",
        '夜' to "Y", '一' to "Y", '衣' to "Y", '以' to "Y", '已' to "Y", '意' to "Y",
        '因' to "Y", '音' to "Y", '银' to "Y", '引' to "Y", '英' to "Y", '应' to "Y",
        '影' to "Y", '用' to "Y", '优' to "Y", '油' to "Y", '有' to "Y", '又' to "Y",
        '鱼' to "Y", '与' to "Y", '雨' to "Y", '元' to "Y", '原' to "Y", '院' to "Y",
        '愿' to "Y", '月' to "Y", '云' to "Y", '运' to "Y", '压' to "Y", '牙' to "Y",
        '咽' to "Y", '烟' to "Y", '严' to "Y", '岩' to "Y", '沿' to "Y", '研' to "Y",
        '盐' to "Y", '厌' to "Y", '宴' to "Y", '央' to "Y", '羊' to "Y", '杨' to "Y",
        '洋' to "Y", '仰' to "Y", '养' to "Y", '钥' to "Y", '耀' to "Y", '爷' to "Y",
        '野' to "Y", '叶' to "Y", '页' to "Y", '液' to "Y", '医' to "Y", '依' to "Y",
        '仪' to "Y", '宜' to "Y", '姨' to "Y", '移' to "Y", '遗' to "Y", '疑' to "Y",
        '矣' to "Y", '蚁' to "Y", '倚' to "Y", '义' to "Y", '艺' to "Y", '议' to "Y",
        '异' to "Y", '抑' to "Y", '译' to "Y", '易' to "Y", '翼' to "Y", '阴' to "Y",
        '隐' to "Y", '印' to "Y", '映' to "Y", '硬' to "Y", '拥' to "Y", '永' to "Y",
        '勇' to "Y", '忧' to "Y", '幽' to "Y", '悠' to "Y", '尤' to "Y", '由' to "Y",
        '游' to "Y", '友' to "Y", '右' to "Y", '幼' to "Y", '于' to "Y", '余' to "Y",
        '娱' to "Y", '宇' to "Y", '玉' to "Y", '育' to "Y", '预' to "Y", '域' to "Y",
        '欲' to "Y", '遇' to "Y", '圆' to "Y", '源' to "Y", '远' to "Y", '约' to "Y",
        '阅' to "Y", '越' to "Y", '允' to "Y", '扬' to "Y", '沂' to "Y",
        '在' to "Z", '再' to "Z", '早' to "Z", '怎' to "Z", '站' to "Z", '张' to "Z",
        '找' to "Z", '这' to "Z", '真' to "Z", '正' to "Z", '证' to "Z", '知' to "Z",
        '只' to "Z", '纸' to "Z", '指' to "Z", '中' to "Z", '种' to "Z", '重' to "Z",
        '州' to "Z", '主' to "Z", '住' to "Z", '注' to "Z", '祝' to "Z", '装' to "Z",
        '状' to "Z", '准' to "Z", '桌' to "Z", '子' to "Z", '自' to "Z", '总' to "Z",
        '走' to "Z", '足' to "Z", '组' to "Z", '嘴' to "Z", '最' to "Z", '作' to "Z",
        '坐' to "Z", '杂' to "Z", '灾' to "Z", '栽' to "Z", '赞' to "Z", '暂' to "Z",
        '遭' to "Z", '糟' to "Z", '造' to "Z", '噪' to "Z", '则' to "Z", '择' to "Z",
        '泽' to "Z", '增' to "Z", '赠' to "Z", '扎' to "Z", '渣' to "Z", '闸' to "Z",
        '摘' to "Z", '宅' to "Z", '债' to "Z", '展' to "Z", '占' to "Z", '章' to "Z",
        '涨' to "Z", '掌' to "Z", '丈' to "Z", '账' to "Z", '招' to "Z", '照' to "Z",
        '遮' to "Z", '折' to "Z", '哲' to "Z", '者' to "Z", '针' to "Z", '侦' to "Z",
        '珍' to "Z", '诊' to "Z", '阵' to "Z", '振' to "Z", '镇' to "Z", '争' to "Z",
        '整' to "Z", '郑' to "Z", '政' to "Z", '症' to "Z", '之' to "Z", '支' to "Z",
        '织' to "Z", '芝' to "Z", '枝' to "Z", '址' to "Z", '至' to "Z", '志' to "Z",
        '制' to "Z", '治' to "Z", '质' to "Z", '致' to "Z", '智' to "Z", '置' to "Z",
        '忠' to "Z", '终' to "Z", '钟' to "Z", '众' to "Z", '舟' to "Z", '周' to "Z",
        '洲' to "Z", '轴' to "Z", '昼' to "Z", '皱' to "Z", '骤' to "Z", '诸' to "Z",
        '猪' to "Z", '竹' to "Z", '逐' to "Z", '助' to "Z", '驻' to "Z", '著' to "Z",
        '抓' to "Z", '专' to "Z", '转' to "Z", '庄' to "Z", '壮' to "Z", '追' to "Z",
        '着' to "Z", '仔' to "Z", '姿' to "Z", '资' to "Z", '紫' to "Z", '字' to "Z",
        '宗' to "Z", '纵' to "Z", '奏' to "Z", '阻' to "Z", '尊' to "Z", '昨' to "Z",
        '左' to "Z", '筑' to "Z", '遵' to "Z"
    )
}
