package com.lingion.sleepy.data.jw

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class OfflineSchoolCityIndexTest {
    companion object {
        private val index by lazy {
            val root = System.getProperty("sleepy.test.root")?.let(::File) ?: File("..")
            GZIPInputStream(File(root, "app/src/main/assets/school_city_boundaries.bin.gz").inputStream())
                .use { OfflineSchoolCityIndex.parse(it) }
        }
    }

    @Test fun `Huizhou coordinate resolves without any geocoder`() {
        assertEquals("惠州市", index.cityAt(23.103775, 114.471620))
    }

    @Test fun `adjacent Guangdong cities do not use the nearest city centre`() {
        assertEquals("佛山市", index.cityAt(23.1385, 113.0270))
        assertEquals("广州市", index.cityAt(23.1291, 113.2644))
        assertEquals("深圳市", index.cityAt(22.5431, 114.0579))
        assertEquals("东莞市", index.cityAt(23.0207, 113.7518))
    }

    @Test fun `municipality districts resolve to city names used in school data`() {
        assertEquals("北京市", index.cityAt(39.9042, 116.4074))
        assertEquals("上海市", index.cityAt(31.2304, 121.4737))
        assertEquals("天津市", index.cityAt(39.1256, 117.1902))
        assertEquals("重庆市", index.cityAt(29.5630, 106.5516))
    }

    @Test fun `locations outside boundaries never return an arbitrary Chinese city`() {
        assertNull(index.cityAt(37.4220, -122.0840))
        assertNull(index.cityAt(0.0, 0.0))
        assertNull(index.cityAt(35.6762, 139.6503))
    }

    @Test fun `invalid coordinates cannot resolve to a city`() {
        assertNull(index.cityAt(Double.NaN, 114.0))
        assertNull(index.cityAt(23.0, Double.POSITIVE_INFINITY))
        assertNull(index.cityAt(91.0, 114.0))
        assertNull(index.cityAt(23.0, -181.0))
    }

    @Test fun `holes and disconnected islands are respected`() {
        val small = SchoolBoundaryFixture.parse("""{"areas":[{"name":"A","polygons":[
            [[[0,0],[10,0],[10,10],[0,10],[0,0]],[[4,4],[6,4],[6,6],[4,6],[4,4]]],
            [[[20,20],[22,20],[22,22],[20,22],[20,20]]]
        ]}]}""")
        assertEquals("A", small.cityAt(1.0, 1.0))
        assertNull(small.cityAt(5.0, 5.0))
        assertEquals("A", small.cityAt(21.0, 21.0))
        assertNull(small.cityAt(15.0, 15.0))
        assertEquals("A", small.cityAt(0.0, 2.0))
    }

    @Test fun `Huizhou city result selects its actual school entry`() {
        val school = JwSchoolInfo("H", "惠州学院", cities = listOf("惠州市"))
        val other = JwSchoolInfo("G", "广东东软学院", cities = listOf("佛山市"))
        val city = requireNotNull(index.cityAt(23.103775, 114.471620))
        assertEquals(listOf(school), SchoolCityIndex.schoolsInCity(listOf(school, other), city))
    }

    @Test fun `all configured cities resolve or have an explicit audited boundary limitation`() {
        val root = System.getProperty("sleepy.test.root")?.let(::File) ?: File("..")
        val assets = File(root, "app/src/main/assets")
        val names = index.cityNames.map(SchoolCityIndex::normalizeCity).toSet()
        val unsupported = OfflineSchoolCityIndex.unsupportedCityReasons
        val cities = SchoolCityIndex.parse(File(assets, "school_cities.json").readText())
        for ((school, locations) in cities) {
            for (city in locations) assertTrue("Unaccounted boundary for $school: $city",
                SchoolCityIndex.normalizeCity(city) in names || city in unsupported)
        }
        assertEquals(unsupported.keys, cities.values.flatten().filter {
            SchoolCityIndex.normalizeCity(it) !in names
        }.toSet())
        assertTrue(unsupported.values.all { it.isNotBlank() })
    }

    @Test fun `province outline never masquerades as a city while Hong Kong and Macau resolve`() {
        assertFalse("台湾省" in index.cityNames)
        assertEquals("臺北市", index.cityAt(25.0330, 121.5654))
        assertEquals("臺中市", index.cityAt(24.1477, 120.6736))
        assertEquals("香港", index.cityAt(22.3193, 114.1694))
        assertEquals("澳门", index.cityAt(22.1987, 113.5439))
    }

    @Test fun `old province-only asset is rejected as city coverage`() {
        val old = SchoolBoundaryFixture.parse("""{"areas":[{"name":"台湾省","polygons":[
            [[[120,20],[125,20],[125,26],[120,26],[120,20]]]
        ]}]}""")
        assertNull(old.cityAt(25.0330, 121.5654))
        assertTrue(old.cityNames.isEmpty())
    }

    @Test fun `Taiwan municipalities counties and offshore islands resolve separately`() {
        assertEquals("新北市", index.cityAt(25.012, 121.465))
        assertEquals("高雄市", index.cityAt(22.6273, 120.3014))
        assertEquals("澎湖縣", index.cityAt(23.565, 119.565))
        assertEquals("金門縣", index.cityAt(24.432, 118.318))
        assertEquals("連江縣", index.cityAt(26.151, 119.930))
    }

    @Test fun `official city aliases use actual prefecture boundaries`() {
        val schools = listOf(
            JwSchoolInfo("K", "昆山杜克大学", cities = listOf("昆山市")),
            JwSchoolInfo("L", "临夏现代职业学院", cities = listOf("临夏州")))
        assertEquals("苏州市", index.cityAt(31.385, 120.953))
        assertEquals(listOf(schools[0]), SchoolCityIndex.schoolsInCity(schools, "苏州市"))
        assertEquals(listOf(schools[1]), SchoolCityIndex.schoolsInCity(schools, "临夏回族自治州"))
        assertTrue(SchoolCityIndex.schoolsInCity(schools, "临夏").isEmpty())
    }

    @Test fun `prefecture location resolves a moved school independently of its name`() {
        val root = System.getProperty("sleepy.test.root")?.let(::File) ?: File("..")
        val cities = SchoolCityIndex.parse(File(root, "app/src/main/assets/school_cities.json").readText())
        val moved = JwSchoolInfo("J", "江西农业大学南昌商学院",
            cities = cities.getValue("江西农业大学南昌商学院"))
        val city = requireNotNull(index.cityAt(29.2, 115.8)) // A point in the Gongqingcheng area.
        assertEquals("九江市", city)
        assertEquals(listOf(moved), SchoolCityIndex.schoolsInCity(listOf(moved), city))
        assertTrue(SchoolCityIndex.schoolsInCity(listOf(moved), "南昌市").isEmpty())
    }
}
