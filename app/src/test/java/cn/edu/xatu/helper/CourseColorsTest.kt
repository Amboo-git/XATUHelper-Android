package cn.edu.xatu.helper

import androidx.compose.ui.graphics.luminance
import cn.edu.xatu.helper.model.*
import cn.edu.xatu.helper.ui.CoursePalette
import org.junit.Assert.*
import org.junit.Test

class CourseColorsTest {
    @Test fun twelveCoursesHaveDistinctColorsEvenWithCollidingHashes() {
        val keys = listOf("AaAa", "BBBB", "AaBB", "BBAa") + (1..8).map { "course$it" }
        assertEquals("AaAa".hashCode(), "BBBB".hashCode())
        val indices = CoursePalette.indices(keys)
        assertEquals(12, indices.values.toSet().size)
        assertEquals(indices, CoursePalette.indices(keys.reversed() + keys))
        assertEquals(12, CoursePalette.light.map { it.background }.toSet().size)
        assertEquals(12, CoursePalette.dark.map { it.background }.toSet().size)
    }

    @Test fun assignmentUsesTheSemesterAcrossAlternatingWeeksAndRepeatedLessons() {
        val courses = listOf(
            Course("A", "单周课", "", "", 1, 1, listOf(1)),
            Course("B", "双周课", "", "", 2, 1, listOf(2)),
            Course("C", "每周课", "", "", 3, 1, listOf(1, 2)),
            Course("C", "每周课", "", "", 4, 2, listOf(1, 2))
        )
        val indices = CoursePalette.indices(courses.map { it.key })
        val first = blocksFor(courses, 1).map { indices.getValue(it.key) }
        val second = blocksFor(courses, 2).map { indices.getValue(it.key) }
        assertEquals(first.last(), second.last())
        assertNotEquals(first.first(), second.first())
        assertEquals(2, first.toSet().size)
    }

    @Test fun bothThemesKeepTextContrastAndLargerTablesRemainBounded() {
        assertEquals(CoursePalette.light.size, CoursePalette.dark.size)
        (CoursePalette.light + CoursePalette.dark).forEach { colors ->
            val a = colors.background.luminance()
            val b = colors.content.luminance()
            val contrast = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
            assertTrue("Text contrast was $contrast", contrast >= 4.5f)
        }
        assertTrue(CoursePalette.indices((1..40).map { "course$it" }).values.all { it in CoursePalette.light.indices })
    }
}
