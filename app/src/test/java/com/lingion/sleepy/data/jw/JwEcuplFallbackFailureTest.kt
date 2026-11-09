package com.lingion.sleepy.data.jw

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class JwEcuplFallbackFailureTest {
    private val course = JwCourse("法学", "A101", "甲老师", 2, 3, 4, 1, 3)

    private fun parser(result: () -> List<JwCourse>) = object : JwParser("") {
        override fun generateCourseList() = result()
    }

    private fun superParser(annual: () -> List<JwCourse>, script: () -> List<JwCourse>) =
        JwEcuplSuperParser("", parser(annual), parser(script))

    @Test fun firstNonemptyAnnualResultStopsFallback() {
        assertEquals(listOf(course), superParser({ listOf(course) }, { error("must not run") }).generateCourseList())
    }

    @Test fun annualFailureCanFallBackToValidScript() {
        assertEquals(listOf(course), superParser({ throw IllegalArgumentException("annual failure") },
            { listOf(course) }).generateCourseList())
    }

    @Test fun twoFailuresKeepBothBranchesAndOriginalCauses() {
        val annual = IllegalArgumentException("annual failure")
        val script = IllegalStateException("script failure")
        val error = assertThrows(IllegalStateException::class.java) {
            superParser({ throw annual }, { throw script }).generateCourseList()
        }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("ECUPLParser: annual failure"))
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("ECUPLScriptParser: script failure"))
        assertEquals(listOf(annual, script), error.suppressed.toList())
    }

    @Test fun annualFailureIsRetainedWhenScriptIsEmpty() {
        val annual = IllegalArgumentException("annual failure")
        val error = assertThrows(IllegalStateException::class.java) {
            superParser({ throw annual }, { emptyList() }).generateCourseList()
        }
        assertSame(annual, error.suppressed.single())
    }

    @Test fun scriptFailureIsRetainedWhenAnnualIsEmpty() {
        val script = IllegalArgumentException("script failure")
        val error = assertThrows(IllegalStateException::class.java) {
            superParser({ emptyList() }, { throw script }).generateCourseList()
        }
        assertSame(script, error.suppressed.single())
    }

    @Test fun fatalErrorIsNotConvertedToAnEmptySchedule() {
        val fatal = StackOverflowError("parser exhausted stack")
        val error = assertThrows(StackOverflowError::class.java) {
            superParser({ throw fatal }, { listOf(course) }).generateCourseList()
        }
        assertSame(fatal, error)
    }

    @Test fun cancellationStopsFallback() {
        val cancellation = CancellationException("cancel import")
        val error = assertThrows(CancellationException::class.java) {
            superParser({ throw cancellation }, { listOf(course) }).generateCourseList()
        }
        assertSame(cancellation, error)
    }

    @Test fun twoEmptyBranchesRemainAnEmptySchedule() {
        assertEquals(emptyList<JwCourse>(), superParser({ emptyList() }, { emptyList() }).generateCourseList())
    }
}
