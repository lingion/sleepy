package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwSchoolNodeRangesTest {
    @Test fun explicitHigherNodesAndHolesRemainExact() {
        assertEquals(listOf(31 to 32, 60 to 60), schoolNodeRanges("31-32,60"))
        assertEquals(listOf(1 to 5, 9 to 11), schoolNodeRanges("3-5,1-3,10-11,9,3"))
    }
    @Test fun largeIntervalsAreMergedWithoutExpandingEveryNode() {
        assertEquals(listOf(1 to Int.MAX_VALUE), schoolNodeRanges("1-2147483647,2-3"))
        assertEquals(listOf(Int.MAX_VALUE to Int.MAX_VALUE), schoolNodeRanges("2147483647"))
    }
    @Test fun invalidCoordinatesInvalidateWholeArrangement() {
        for (raw in listOf("0", "3-2", "-1", "1,", "1,bad", "1.5", "2147483648"))
            assertEquals(raw, emptyList<Pair<Int, Int>>(), schoolNodeRanges(raw))
    }
}
