package cvc.dashingdog.vaart

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

enum class SignShape { CIRCLE, TRIANGLE, RECTANGLE }

data class SignColorScheme(
    val key: String,
    val name: String,
    val maxShape: SignShape = SignShape.CIRCLE,
    val maxBackgroundColor: Int,
    val maxBorderColor: Int,
    val maxTextColor: Int,
    val maxFixedText: String? = null,   // e.g. "SPEED LIMIT" for rectangular variants
    val minShape: SignShape = SignShape.CIRCLE,
    val minBackgroundColor: Int,
    val minBorderColor: Int,
    val minTextColor: Int,
    val minFixedText: String? = null
) {
    fun buildMaxDrawable(strokeWidthPx: Int): GradientDrawable =
        buildDrawable(maxShape, maxBackgroundColor, maxBorderColor, strokeWidthPx)

    fun buildMinDrawable(strokeWidthPx: Int): GradientDrawable =
        buildDrawable(minShape, minBackgroundColor, minBorderColor, strokeWidthPx)

    private fun buildDrawable(shape: SignShape, bg: Int, border: Int, strokeWidthPx: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = when (shape) {
            SignShape.CIRCLE -> GradientDrawable.OVAL
            SignShape.RECTANGLE -> GradientDrawable.RECTANGLE
            SignShape.TRIANGLE -> GradientDrawable.OVAL // placeholder until a real triangle path is needed
        }
        drawable.setColor(bg)
        drawable.setStroke(strokeWidthPx, border)
        return drawable
    }
}

object SignColorSchemes {
    val INTERNATIONAL = SignColorScheme(
        key = "international",
        name = "International Standard Signs",
        maxBackgroundColor = Color.WHITE,
        maxBorderColor = Color.parseColor("#CC0000"),
        maxTextColor = Color.BLACK,
        minBackgroundColor = Color.parseColor("#1E3A8A"),
        minBorderColor = Color.WHITE,
        minTextColor = Color.WHITE
    )

    val SA_1974_1993 = SignColorScheme( // Reference: https://simple.wikipedia.org/wiki/Road_signs_in_South_Africa#Prohibition_signs_2
        key = "sa_1974_1993",
        name = "South African Standard (1974–1993)",
        maxBackgroundColor = Color.parseColor("#1E3A8A"),
        maxBorderColor = Color.parseColor("#CC0000"),
        maxTextColor = Color.WHITE,
        minBackgroundColor = Color.parseColor("#1E3A8A"),
        minBorderColor = Color.WHITE,
        minTextColor = Color.WHITE
    )

    val ALL = listOf(INTERNATIONAL, SA_1974_1993)

    fun byKey(key: String?): SignColorScheme =
        ALL.find { it.key == key } ?: INTERNATIONAL
}