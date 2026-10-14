package com.lingion.sleepy.data.jw

import org.json.JSONObject

/** City metadata is separate from the import directory, so it cannot alter URLs or protocols. */
object SchoolCityIndex {
    fun parse(text: String): Map<String, List<String>> {
        val schools = JSONObject(text).getJSONObject("schools")
        return schools.keys().asSequence().associateWith { name ->
            val cities = schools.getJSONArray(name)
            (0 until cities.length()).map { cities.getString(it) }.filter { it.isNotBlank() }
        }
    }

    // Recommendation boundaries are prefecture-level; retain official metadata spellings.
    fun normalizeCity(city: String): String = when (val name = city.trim().removeSuffix("市")) {
        "临夏州" -> "临夏回族自治州"
        "昆山" -> "苏州"
        else -> name
    }

    fun schoolsInCity(schools: List<JwSchoolInfo>, city: String): List<JwSchoolInfo> {
        val normalized = normalizeCity(city)
        if (normalized.isBlank()) return emptyList()
        return schools.filter { school -> school.cities.any { normalizeCity(it) == normalized } }
            .distinctBy { it.stableKey }
            .sortedWith(compareBy<JwSchoolInfo> { !it.isSupported }
                .thenBy { it.sortKeyFull.ifBlank { it.name } })
    }
}
