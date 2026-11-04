package me.nomi.urdutyper.presentation.ui.type.studio

import android.graphics.*
import kotlin.math.*

/** Seeded motion: seeking and exporting a given timestamp always gives the same frame. */
object StudioAtmosphere {
    fun draw(canvas: Canvas, width: Float, height: Float, timeMs: Long, effect: String, intensity: Float, speed: Float) {
        val strength = intensity.coerceIn(0f,1f)
        if (effect == "none" || strength <= 0) return
        val time = timeMs.coerceAtLeast(0)/1000f*speed.coerceIn(.25f,2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val leaf = Path().apply { moveTo(-1f,0f); cubicTo(-.2f,-.8f,.8f,-.7f,1f,0f); cubicTo(.4f,.8f,-.3f,.7f,-1f,0f); close() }
        val count = ((if (effect == "rain") 120 else if (effect == "snow") 86 else 44)*strength).roundToInt().coerceAtLeast(1)
        fun unit(index: Int, salt: Int) = ((index*7919+salt*104729).toLong().let { (it*48271L+17)%100003 }/100003f)
        repeat(count) { i ->
            val depth = .35f+.65f*unit(i,1)
            val baseX = unit(i,2)*width
            val margin = height*.08f
            val period = when(effect) { "rain" -> 1.2f/depth; "snow" -> 10f/depth; "leaves" -> 7f/depth; else -> 14f/depth }
            val y = ((unit(i,3)+time/period)%1f)*(height+2*margin)-margin
            val drift = when(effect) { "rain" -> -width*.07f*(y/height); "leaves" -> sin(time*(.8f+depth)+i)*width*.07f; else -> sin(time*.55f+i)*width*.025f }
            val x = (baseX+drift+width)%width
            paint.style = Paint.Style.FILL
            when(effect) {
                "rain" -> {
                    paint.color = 0x889fc9e7.toInt(); paint.strokeWidth = (width/600f*depth).coerceAtLeast(.7f)
                    canvas.drawLine(x,y,x-width*.012f*depth,y+height*.035f*depth,paint)
                }
                "snow" -> {
                    paint.color = Color.argb((120+120*depth).toInt(),246,250,255)
                    canvas.drawCircle(x,y,width/210f*depth,paint)
                }
                "leaves" -> {
                    val colors = intArrayOf(0xffc97628.toInt(),0xffae4b21.toInt(),0xffd6a143.toInt(),0xff895132.toInt())
                    val r = width/65f*depth
                    canvas.save(); canvas.translate(x,y); canvas.rotate(time*(24f+32*depth)+i*47f)
                    canvas.scale(r,(.45f+.25f*abs(sin(time+i)))*r)
                    paint.color = colors[i%colors.size]; canvas.drawPath(leaf,paint)
                    paint.color = 0x88634423.toInt(); paint.strokeWidth = .07f; canvas.drawLine(-.8f,0f,.8f,0f,paint)
                    canvas.restore()
                }
                else -> {
                    paint.color = 0x77fff1c9; canvas.drawCircle(x,y,width/240f*depth,paint)
                }
            }
        }
    }
}
