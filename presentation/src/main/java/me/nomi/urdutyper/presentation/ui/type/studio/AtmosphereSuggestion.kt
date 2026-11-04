package me.nomi.urdutyper.presentation.ui.type.studio

/** Conservative, editable hints. Color alone cannot establish actual weather. */
data class AtmosphereSuggestion(val effect: String, val reason: String)
object AtmosphereHints {
    fun suggest(name: String, pixels: IntArray): AtmosphereSuggestion {
        val words = name.lowercase(java.util.Locale.ROOT)
        if (listOf("snow","winter","برف","snowfall").any { it in words }) return AtmosphereSuggestion("snow","Snow / winter in image name")
        if (listOf("rain","barish","بارش","monsoon").any { it in words }) return AtmosphereSuggestion("rain","Rain in image name")
        if (listOf("autumn","khiza","خزاں","خزان","fall-leaf","fall_lea","fall foliage").any { it in words }) return AtmosphereSuggestion("leaves","Autumn in image name")
        if (pixels.isEmpty()) return AtmosphereSuggestion("none","Choose an effect manually")
        var white = 0; var cool = 0; var warm = 0; var foliage = 0; var brown = 0
        pixels.forEach { color ->
            val r = (color ushr 16 and 255)/255f; val g = (color ushr 8 and 255)/255f; val b = (color and 255)/255f
            val hi = maxOf(r,g,b); val lo = minOf(r,g,b); val saturation = if (hi == 0f) 0f else (hi-lo)/hi
            if (hi > .78f && lo > .66f && saturation < .22f) white++
            if (b > r*1.08f && b > g*.95f) cool++
            if (r > .30f && r > g*1.13f && g > b*1.12f && saturation > .25f) warm++
            if (g > r*1.05f && g > b*1.12f && saturation > .2f) foliage++
            if (r in .20f.. .65f && r > g*1.12f && g > b*1.1f) brown++
        }
        val n = pixels.size.toFloat()
        if (white/n > .38f && cool/n > .08f) return AtmosphereSuggestion("snow","Bright white and cool image palette")
        if (warm/n > .30f && (foliage/n > .06f || brown/n > .25f)) return AtmosphereSuggestion("leaves","Warm foliage-like image palette")
        return AtmosphereSuggestion("none","No reliable weather cue; choose an effect below")
    }
}
