package com.lingion.sleepy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SchoolCityIndexTest {
    private fun assets(): File {
        val root = System.getProperty("sleepy.test.root")?.let(::File) ?: File("..")
        return File(root, "app/src/main/assets")
    }

    private fun bundledCities() = SchoolCityIndex.parse(File(assets(), "school_cities.json").readText())

    private fun school(name: String, city: String, key: String = name, status: String = JwSchoolInfo.STATUS_SUPPORTED) =
        JwSchoolInfo("A", name, status = status, sortKeyFull = key, cities = listOf(city))

    @Test fun `city suffix and whitespace are normalized without substring matching`() {
        val schools = listOf(school("合肥学校", "合肥市"), school("肥城学校", "肥城市"))
        assertEquals(listOf("合肥学校"), SchoolCityIndex.schoolsInCity(schools, " 合肥 ").map { it.name })
        assertTrue(SchoolCityIndex.schoolsInCity(schools, "肥").isEmpty())
        assertTrue(SchoolCityIndex.schoolsInCity(schools, "安徽省").isEmpty())
        assertTrue(SchoolCityIndex.schoolsInCity(schools, " ").isEmpty())
    }

    @Test fun `recommendations prefer supported entries then use pinyin without changing input order`() {
        val pending = school("待适配学校", "合肥市", "a", JwSchoolInfo.STATUS_PENDING)
        val beta = school("乙校", "合肥市", "b")
        val alpha = school("甲校", "合肥市", "a")
        val input = listOf(pending, beta, alpha)
        assertEquals(listOf(alpha, beta, pending), SchoolCityIndex.schoolsInCity(input, "合肥市"))
        assertEquals(listOf(pending, beta, alpha), input)
    }

    @Test fun `multi city schools appear once and unknown metadata is not guessed from name`() {
        val multi = school("多校区大学", "北京市").copy(cities = listOf("北京市", "保定市"))
        val unknown = JwSchoolInfo("B", "保定未知学校")
        assertEquals(listOf(multi), SchoolCityIndex.schoolsInCity(listOf(multi, multi, unknown), "保定市"))
    }

    @Test fun `metadata is ignored by index parser and empty city strings are excluded`() {
        val result = SchoolCityIndex.parse("""{"_source":"official","schools":{"学校":["", "合肥市"]}}""")
        assertEquals(mapOf("学校" to listOf("合肥市")), result)
    }

    @Test fun `supported entries retain cities and optional new mappings are valid without stale entries`() {
        val root = System.getProperty("sleepy.test.root")?.let(::File) ?: File("..")
        val assets = File(root, "app/src/main/assets")
        val entries = JSONArray(File(assets, "schools.json").readText())
        val directory = (0 until entries.length()).map { entries.getJSONObject(it) }
            .filterNot { entry -> entry.keys().asSequence().any { it.startsWith("_") } }
        val namesList = directory.map { it.getString("name") }
        val duplicates = namesList.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue("重复学校名称，无法独立关联城市: $duplicates", duplicates.isEmpty())
        assertTrue("学校名称不能为空或带首尾空格", namesList.all { it.isNotBlank() && it == it.trim() })
        val names = namesList.toSet()
        val raw = JSONObject(File(assets, "school_cities.json").readText()).getJSONObject("schools")
        val cities = SchoolCityIndex.parse(JSONObject().put("schools", raw).toString())
        val supportedNames = JwImportViewModel.parseSchoolsJson(File(assets, "schools.json").readText())
            .filter { it.isSupported }.map { it.name }.toSet()
        assertTrue("Existing supported cities missing: ${supportedNames - cities.keys}", (supportedNames - cities.keys).isEmpty())
        assertTrue("城市表中不存在于目录的学校: ${cities.keys - names}", (cities.keys - names).isEmpty())
        for (name in cities.keys) {
            val values = raw.getJSONArray(name).let { array ->
                (0 until array.length()).map { array.getString(it) }
            }
            assertTrue("学校 $name 必须至少填写一个城市", values.isNotEmpty())
            assertTrue("学校 $name 的城市不能为空或带首尾空格: $values",
                values.all { it.isNotBlank() && it == it.trim() })
            assertEquals("学校 $name 的城市不能重复: $values", values.distinct().size, values.size)
        }
        assertEquals(listOf("佛山市"), cities["广东东软学院"])
        assertEquals(listOf("秦皇岛市"), cities["东北大学秦皇岛分校"])
        assertEquals(listOf("威海市"), cities["山东大学（威海）"])
        assertEquals(cities["中国科学技术大学"], cities["中国科学技术大学 - 研究生"])
    }

    @Test fun `every directory entry participates in recommendations for every configured city`() {
        // Load through the app's directory parser: future entries join without a hard-coded school count.
        val cities = bundledCities()
        val schools = JwImportViewModel.parseSchoolsJson(File(assets(), "schools.json").readText())
            .map { it.copy(cities = cities[it.name].orEmpty()) }
        for (city in cities.values.flatten().distinct()) {
            val expected = cities.filterValues { locations ->
                locations.any { SchoolCityIndex.normalizeCity(it) == SchoolCityIndex.normalizeCity(city) }
            }.keys
            val actual = SchoolCityIndex.schoolsInCity(schools, city).map { it.name }
            assertEquals("城市 $city 的推荐遗漏或多出学校", expected, actual.toSet())
            assertEquals("城市 $city 重复推荐学校", actual.distinct().size, actual.size)
        }
    }

    @Test fun `misleading names independent colleges and moved campuses use their own locations`() {
        val cities = bundledCities()
        assertEquals(listOf("江门市"), cities["五邑大学"])
        assertEquals(listOf("秦皇岛市"), cities["燕山大学"])
        assertEquals(listOf("乐山市"), cities["成都理工大学工程技术学院"])
        assertEquals(listOf("镇江市"), cities["南京师范大学中北学院"])
        assertEquals(listOf("扬州市"), cities["南京邮电大学通达学院"])
        assertEquals(listOf("烟台市"), cities["青岛农业大学海都学院"])
        assertEquals(listOf("中山市"), cities["电子科技大学中山学院"])
        assertEquals(listOf("绍兴市"), cities["浙江工业大学之江学院"])
        assertEquals(listOf("九江市"), cities["江西农业大学南昌商学院"])

        // SYSU is not currently an import entry. Exercise its real multi-city case without adding one.
        val sysu = school("中山大学", "广州市").copy(cities = listOf("广州市", "珠海市", "深圳市"))
        assertTrue(SchoolCityIndex.schoolsInCity(listOf(sysu), "中山市").isEmpty())
        for (city in sysu.cities) assertEquals(listOf(sysu), SchoolCityIndex.schoolsInCity(listOf(sysu), city))
    }

    @Test fun `actual cross city campuses are recommended while branch entries stay separate`() {
        val cities = bundledCities()
        val entries = JSONArray(File(assets(), "schools.json").readText())
        val schools = (0 until entries.length()).map { index ->
            val name = entries.getJSONObject(index).getString("name")
            school(name, "").copy(cities = cities[name].orEmpty())
        }
        fun names(city: String) = SchoolCityIndex.schoolsInCity(schools, city).map { it.name }
        assertTrue("河海大学" in names("常州市"))
        assertTrue("南京航空航天大学" in names("常州市"))
        assertTrue("合肥工业大学" in names("宣城市"))
        assertTrue("广东医科大学" in names("东莞市"))
        assertTrue("华南师范大学" in names("汕尾市"))
        assertTrue("苏州科技大学天平学院" in names("苏州市"))
        assertTrue("苏州科技大学天平学院" in names("南京市"))
        assertTrue("浙江工业大学之江学院" in names("绍兴市"))
        assertFalse("浙江工业大学之江学院" in names("杭州市"))
        assertTrue("江西农业大学南昌商学院" in names("九江市"))
        assertFalse("江西农业大学南昌商学院" in names("南昌市"))
        assertTrue("厦门大学" in names("漳州市"))
        assertTrue("厦门大学 - 研究生" in names("漳州市"))
        assertFalse("山东大学（威海）" in names("济南市"))
        assertFalse("东北大学秦皇岛分校" in names("沈阳市"))
        assertFalse("电子科技大学中山学院" in names("成都市"))
    }

    @Test fun `campus overrides have traceable sources and apply to explicit aliases only`() {
        val metadata = JSONObject(File(assets(), "school_cities.json").readText())
        val cities = SchoolCityIndex.parse(metadata.toString())
        val aliases = metadata.getJSONObject("_nameMappings")
        val overrides = metadata.getJSONObject("_campusOverrides")
        for (canonical in overrides.keys()) {
            val entry = overrides.getJSONObject(canonical)
            val expected = entry.getJSONArray("cities").let { values ->
                (0 until values.length()).map { values.getString(it) }
            }
            assertTrue(expected.isNotEmpty())
            assertEquals(expected.size, expected.distinct().size)
            val sources = entry.getJSONArray("sources")
            assertTrue(sources.length() > 0)
            for (i in 0 until sources.length()) assertTrue(sources.getString(i).startsWith("https://"))
            assertTrue(entry.getString("reason").isNotBlank())
            val names = cities.keys.filter { aliases.optString(it, it) == canonical }
            assertTrue("Unused campus override: $canonical", names.isNotEmpty())
            for (name in names) assertEquals("Alias: $name", expected, cities[name])
        }
    }
}
