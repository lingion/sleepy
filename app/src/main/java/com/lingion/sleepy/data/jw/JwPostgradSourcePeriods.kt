package com.lingion.sleepy.data.jw

/** Optional source defaults. Captured authenticated periods must take priority at the caller. */
internal object JwPostgradSourcePeriods {
    fun resolve(type: String): List<Triple<Int, String, String>> = when (type) {
        "shtu_post", "shtu_post_2024" -> rows("08:15-09:00 09:10-09:55 10:15-11:00 11:10-11:55 13:00-13:45 13:55-14:40 15:00-15:45 15:55-16:40 16:50-17:35 18:00-18:45 18:55-19:40 19:50-20:35 20:45-21:30")
        "xjtu_post" -> rows("08:00-08:50 09:00-09:50 10:10-11:00 11:10-12:00 14:30-15:20 15:30-16:20 16:40-17:30 17:40-18:30 19:40-20:30 20:40-21:30 21:40-22:30")
        "ustc_post" -> rows("07:50-08:35 08:40-09:25 09:45-10:30 10:35-11:20 11:25-12:10 14:00-14:45 14:50-15:35 15:55-16:40 16:45-17:30 17:35-18:20 19:30-20:15 20:20-21:05 21:10-21:55")
        "bfa_post" -> rows("08:30-09:15 09:15-10:00 10:15-11:00 11:00-11:45 13:30-14:15 14:15-15:00 15:15-16:00 16:00-16:45 18:00-18:45 18:45-19:30 19:45-20:30 20:30-21:15")
        else -> emptyList()
    }

    private fun rows(text: String): List<Triple<Int, String, String>> = text.split(' ').mapIndexed { index, value ->
        Triple(index + 1, value.substringBefore('-'), value.substringAfter('-'))
    }
}
