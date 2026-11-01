package cn.edu.xatu.helper.ui

import androidx.compose.ui.graphics.Color

data class CourseColors(val background: Color, val content: Color)

/** Independent of wallpaper colors, with matching readable text in both themes. */
object CoursePalette {
    val light = listOf(
        CourseColors(Color(0xFFFFD1CC), Color(0xFF5E170F)), // coral
        CourseColors(Color(0xFFC9DEFF), Color(0xFF123B69)), // blue
        CourseColors(Color(0xFFC7EBD1), Color(0xFF164B2B)), // green
        CourseColors(Color(0xFFFFE1A0), Color(0xFF593B00)), // amber
        CourseColors(Color(0xFFE2CCFF), Color(0xFF45246B)), // violet
        CourseColors(Color(0xFFB4E5DE), Color(0xFF004D46)), // teal
        CourseColors(Color(0xFFFFCCE4), Color(0xFF6B1746)), // rose
        CourseColors(Color(0xFFBDE6FF), Color(0xFF064764)), // sky
        CourseColors(Color(0xFFDFE9AE), Color(0xFF3F4C0C)), // olive
        CourseColors(Color(0xFFFFD4B3), Color(0xFF713509)), // orange
        CourseColors(Color(0xFFD4D5FF), Color(0xFF303272)), // indigo
        CourseColors(Color(0xFFE4CCB9), Color(0xFF583923))  // brown
    )
    val dark = listOf(
        CourseColors(Color(0xFF6B2920), Color(0xFFFFE0DC)),
        CourseColors(Color(0xFF1D416A), Color(0xFFD7E7FF)),
        CourseColors(Color(0xFF245137), Color(0xFFD5F2DC)),
        CourseColors(Color(0xFF62470D), Color(0xFFFFE8BD)),
        CourseColors(Color(0xFF4D326C), Color(0xFFEBDDFF)),
        CourseColors(Color(0xFF12544D), Color(0xFFC8F0EB)),
        CourseColors(Color(0xFF6B2B4D), Color(0xFFFFDAED)),
        CourseColors(Color(0xFF1A4D63), Color(0xFFD9F0FF)),
        CourseColors(Color(0xFF47501F), Color(0xFFEAEEC6)),
        CourseColors(Color(0xFF6A3A1B), Color(0xFFFFE1CB)),
        CourseColors(Color(0xFF383E74), Color(0xFFE1E2FF)),
        CourseColors(Color(0xFF594232), Color(0xFFEFDFD3))
    )

    /** Allocate from the whole semester, never just the visible week or hash remainder. */
    fun indices(courseKeys: List<String>): Map<String, Int> = courseKeys.distinct().sorted()
        .mapIndexed { index, key -> key to index % light.size }.toMap()
}
