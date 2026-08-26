package com.lingion.sleepy.data.parser

import org.junit.Test

class TimeBlockRealInputTest {
    @Test fun parsesAiTimeBlockWithMultiPeriodRanges() {
        val text = """<<<SLEEPY-BEGIN>>>
<<<SLEEPY-TIME-BEGIN>>>
第1-2节 08:20-10:00
第3-4节 10:20-12:00
第5-6节 13:20-15:00
第7-8节 15:20-16:50
第9-10节 18:00-19:50
<<<SLEEPY-TIME-END>>>
算法设计与分析(理论)	王斌	3教337	1	1-2	1-12	0
Linux操作系统实验(环宇)	何静	3教337	1	1-2	17	0
<<<SLEEPY-END>>>"""
        val result = ScheduleParser.parse(text, 0L)
        if (result.isFailure) {
            val e = result.exceptionOrNull()!!
            throw AssertionError("PARSE FAILED: ${e.javaClass.simpleName}: $e\n${e.stackTrace.take(8).joinToString("\n")}")
        }
        val parsed = result.getOrThrow()
        assert(parsed.courses.size == 2) { "courses: ${parsed.courses.size}" }
        assert(parsed.nodesPerDay == 10) { "nodesPerDay: ${parsed.nodesPerDay}" }
    }
}
