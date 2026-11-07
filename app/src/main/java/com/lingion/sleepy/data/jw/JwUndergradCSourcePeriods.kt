package com.lingion.sleepy.data.jw

/** Source defaults only; authenticated captured periods take precedence. */
object JwUndergradCSourcePeriods {
    fun resolve(type:String):List<Triple<Int,String,String>> = when(type) {
        "lngd" -> rows("08:20-09:05 09:15-10:00 10:10-10:55 11:05-11:50 13:20-14:05 14:15-15:00 15:10-15:55 16:05-16:50 17:15-18:00 18:10-18:55 19:05-19:50 20:00-20:45")
        "yzzy" -> rows("08:00-08:40 08:50-09:30 09:40-10:20 10:30-11:10 11:20-12:00 12:50-13:30 13:40-14:20 14:30-15:10 15:20-16:00 16:10-16:50 17:00-17:40 19:30-20:10 20:20-21:00")
        "thu" -> rows("08:00-08:45 08:50-09:35 09:50-10:35 10:40-11:25 11:30-12:15 13:30-14:15 14:20-15:05 15:20-16:05 16:10-16:55 17:05-17:50 17:55-18:40 19:20-20:05 20:10-20:55 21:00-21:45")
        else -> emptyList()
    }
    private fun rows(value:String)=value.split(' ').mapIndexed { index,range -> Triple(index+1,range.substringBefore('-'),range.substringAfter('-')) }
}
