package cn.edu.xatu.helper.data

import android.graphics.Bitmap
import cn.edu.xatu.helper.model.SemesterSpan
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

object CalendarOcr {
    data class Result(val text: String, val semesters: List<SemesterSpan>, val coverageEnd: String?)
    fun read(bitmap: Bitmap): Result {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            val full = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            var text = full.text
            var semesters = CalendarParser.semesters(text)
            if (semesters.size < 2) {
                val crop = Bitmap.createBitmap(bitmap, 0, bitmap.height * 3 / 5, bitmap.width, bitmap.height - bitmap.height * 3 / 5)
                val bottom = try { Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0))).text } finally { if (crop !== bitmap) crop.recycle() }
                val spans = CalendarParser.semesters(bottom)
                if (spans.size > semesters.size) { semesters = spans; text += "\n" + bottom }
            }
            val cells = full.textBlocks.flatMap { block -> block.lines.flatMap { line ->
                (listOf(line.text to line.boundingBox) + line.elements.map { it.text to it.boundingBox }).mapNotNull { (value, bounds) ->
                    bounds?.let { CalendarParser.Cell(value, it.left, it.top, it.right, it.bottom) }
                }
            } }.distinct()
            Result(text, semesters, CalendarParser.coverageEnd(cells, semesters))
        } finally { recognizer.close() }
    }
}
