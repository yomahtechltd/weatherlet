package com.weatherlet.app.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

enum class IconKind { CLEAR_DAY, CLEAR_NIGHT, PARTLY_DAY, PARTLY_NIGHT, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/**
 * Hand-drawn weather icons rendered to bitmaps, so the app (Compose) and the home-screen widgets
 * (Glance, which can't run Canvas code) share exactly the same artwork.
 */
object WeatherIcons {
    fun kind(code: Int, isDay: Boolean = true): IconKind = when (code) {
        0, 1 -> if (isDay) IconKind.CLEAR_DAY else IconKind.CLEAR_NIGHT
        2 -> if (isDay) IconKind.PARTLY_DAY else IconKind.PARTLY_NIGHT
        3 -> IconKind.CLOUDY
        45, 48 -> IconKind.FOG
        51, 53, 55, 56, 57 -> IconKind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> IconKind.RAIN
        71, 73, 75, 77, 85, 86 -> IconKind.SNOW
        95, 96, 99 -> IconKind.STORM
        else -> IconKind.CLOUDY
    }

    private val cache = LruCache<String, Bitmap>(40)

    fun bitmap(code: Int, isDay: Boolean, px: Int): Bitmap = bitmap(kind(code, isDay), px)

    fun bitmap(kind: IconKind, px: Int): Bitmap {
        val key = "${kind.name}@$px"
        cache.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        draw(kind, Canvas(bmp), px.toFloat())
        cache.put(key, bmp)
        return bmp
    }

    // ---- drawing (everything is laid out on a 100×100 grid, scaled by s/100) ----------------------

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.style = style
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private class G(val c: Canvas, val s: Float) {
        fun v(x: Float) = x * s / 100f
    }

    private fun draw(kind: IconKind, c: Canvas, s: Float) {
        val g = G(c, s)
        when (kind) {
            IconKind.CLEAR_DAY -> sun(g, 50f, 50f, 22f)
            IconKind.CLEAR_NIGHT -> moon(g, 50f, 50f, 26f)
            IconKind.PARTLY_DAY -> { sun(g, 36f, 36f, 17f); cloud(g, 58f, 62f, 64f, Palette.CLOUD, Palette.CLOUD_SHADE) }
            IconKind.PARTLY_NIGHT -> { moon(g, 36f, 36f, 18f); cloud(g, 58f, 62f, 64f, Palette.CLOUD, Palette.CLOUD_SHADE) }
            IconKind.CLOUDY -> {
                cloud(g, 40f, 40f, 48f, Palette.CLOUD_BACK, Palette.CLOUD_BACK_SHADE)
                cloud(g, 56f, 60f, 66f, Palette.CLOUD, Palette.CLOUD_SHADE)
            }
            IconKind.FOG -> {
                cloud(g, 50f, 38f, 58f, Palette.CLOUD, Palette.CLOUD_SHADE)
                val p = paint(Palette.FOG, Paint.Style.STROKE, g.v(5f))
                c.drawLine(g.v(22f), g.v(68f), g.v(78f), g.v(68f), p)
                c.drawLine(g.v(30f), g.v(79f), g.v(70f), g.v(79f), p)
                c.drawLine(g.v(22f), g.v(90f), g.v(62f), g.v(90f), p)
            }
            IconKind.DRIZZLE -> {
                cloud(g, 50f, 40f, 64f, Palette.CLOUD, Palette.CLOUD_SHADE)
                drops(g, floatArrayOf(34f, 50f, 66f), floatArrayOf(72f, 78f, 72f), 7f, Palette.DRIZZLE)
            }
            IconKind.RAIN -> {
                cloud(g, 50f, 38f, 68f, Palette.CLOUD_DARK, Palette.CLOUD_DARK_SHADE)
                drops(g, floatArrayOf(30f, 46f, 62f, 38f, 54f, 70f), floatArrayOf(68f, 68f, 68f, 84f, 84f, 84f), 10f, Palette.RAIN)
            }
            IconKind.SNOW -> {
                cloud(g, 50f, 38f, 68f, Palette.CLOUD, Palette.CLOUD_SHADE)
                for ((x, y) in listOf(32f to 72f, 50f to 80f, 68f to 72f, 41f to 90f, 60f to 90f)) flake(g, x, y, 5.5f)
            }
            IconKind.STORM -> {
                cloud(g, 50f, 36f, 70f, Palette.CLOUD_DARK, Palette.CLOUD_DARK_SHADE)
                bolt(g, 48f, 52f, 26f)
            }
        }
    }

    private object Palette {
        val CLOUD = Color.parseColor("#F2F5FA")
        val CLOUD_SHADE = Color.parseColor("#C9D3E2")
        val CLOUD_BACK = Color.parseColor("#C5CEDB")
        val CLOUD_BACK_SHADE = Color.parseColor("#A9B5C6")
        val CLOUD_DARK = Color.parseColor("#B6C0CF")
        val CLOUD_DARK_SHADE = Color.parseColor("#8F9BAE")
        val SUN = Color.parseColor("#FFC93C")
        val SUN_RAY = Color.parseColor("#FFB020")
        val MOON = Color.parseColor("#FFF0B3")
        val RAIN = Color.parseColor("#3D8BFF")
        val DRIZZLE = Color.parseColor("#6FB1FF")
        val FLAKE = Color.parseColor("#7DBFFF")
        val BOLT = Color.parseColor("#FFC400")
        val FOG = Color.parseColor("#AEB9C9")
    }

    private fun sun(g: G, cx: Float, cy: Float, r: Float) {
        val c = g.c
        // soft glow
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(g.v(cx), g.v(cy), g.v(r * 1.9f), Color.argb(90, 255, 201, 60), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawCircle(g.v(cx), g.v(cy), g.v(r * 1.9f), glow)
        val ray = paint(Palette.SUN_RAY, Paint.Style.STROKE, g.v(r * 0.2f))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val r1 = r * 1.28f; val r2 = r * 1.62f
            c.drawLine(
                g.v(cx + (cos(a) * r1).toFloat()), g.v(cy + (sin(a) * r1).toFloat()),
                g.v(cx + (cos(a) * r2).toFloat()), g.v(cy + (sin(a) * r2).toFloat()), ray,
            )
        }
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(g.v(cx - r), g.v(cy - r), g.v(cx + r), g.v(cy + r), Color.parseColor("#FFE066"), Palette.SUN, Shader.TileMode.CLAMP)
        }
        c.drawCircle(g.v(cx), g.v(cy), g.v(r), disc)
    }

    private fun moon(g: G, cx: Float, cy: Float, r: Float) {
        val c = g.c
        val outer = Path().apply { addCircle(g.v(cx), g.v(cy), g.v(r), Path.Direction.CW) }
        val bite = Path().apply { addCircle(g.v(cx + r * 0.55f), g.v(cy - r * 0.35f), g.v(r * 0.85f), Path.Direction.CW) }
        outer.op(bite, Path.Op.DIFFERENCE)
        c.drawPath(outer, paint(Palette.MOON))
        val star = paint(Color.parseColor("#FFF7CC"))
        c.drawCircle(g.v(cx + r * 0.95f), g.v(cy + r * 0.55f), g.v(r * 0.10f), star)
        c.drawCircle(g.v(cx + r * 0.55f), g.v(cy - r * 0.95f), g.v(r * 0.07f), star)
    }

    /** A fluffy cloud centred at ([cx],[cy]) of overall width [w], with a darker underside. */
    private fun cloud(g: G, cx: Float, cy: Float, w: Float, fill: Int, shade: Int) {
        val c = g.c
        val path = Path().apply {
            addCircle(g.v(cx - w * 0.24f), g.v(cy + w * 0.07f), g.v(w * 0.17f), Path.Direction.CW)
            addCircle(g.v(cx - w * 0.02f), g.v(cy - w * 0.08f), g.v(w * 0.25f), Path.Direction.CW)
            addCircle(g.v(cx + w * 0.25f), g.v(cy + w * 0.06f), g.v(w * 0.18f), Path.Direction.CW)
            addRoundRect(
                RectF(g.v(cx - w * 0.41f), g.v(cy + w * 0.02f), g.v(cx + w * 0.43f), g.v(cy + w * 0.24f)),
                g.v(w * 0.11f), g.v(w * 0.11f), Path.Direction.CW,
            )
        }
        c.drawPath(path, paint(fill))
        c.save()
        c.clipRect(0f, g.v(cy + w * 0.11f), g.s, g.s)
        c.drawPath(path, paint(shade))
        c.restore()
        // re-draw the lighter top edge so the shading reads as an underside, not a stripe
        c.save()
        c.clipRect(0f, 0f, g.s, g.v(cy + w * 0.11f))
        c.drawPath(path, paint(fill))
        c.restore()
    }

    private fun drops(g: G, xs: FloatArray, ys: FloatArray, len: Float, color: Int) {
        val p = paint(color, Paint.Style.STROKE, g.v(4.4f))
        for (i in xs.indices) g.c.drawLine(g.v(xs[i]), g.v(ys[i]), g.v(xs[i] - len * 0.35f), g.v(ys[i] + len), p)
    }

    private fun flake(g: G, x: Float, y: Float, r: Float) {
        val p = paint(Palette.FLAKE, Paint.Style.STROKE, g.v(2.4f))
        for (i in 0 until 3) {
            val a = Math.toRadians(i * 60.0)
            val dx = (cos(a) * r).toFloat(); val dy = (sin(a) * r).toFloat()
            g.c.drawLine(g.v(x - dx), g.v(y - dy), g.v(x + dx), g.v(y + dy), p)
        }
    }

    private fun bolt(g: G, x: Float, y: Float, h: Float) {
        val p = Path().apply {
            moveTo(g.v(x + h * 0.12f), g.v(y))
            lineTo(g.v(x - h * 0.30f), g.v(y + h * 0.62f))
            lineTo(g.v(x - h * 0.02f), g.v(y + h * 0.62f))
            lineTo(g.v(x - h * 0.16f), g.v(y + h * 1.25f))
            lineTo(g.v(x + h * 0.34f), g.v(y + h * 0.48f))
            lineTo(g.v(x + h * 0.04f), g.v(y + h * 0.48f))
            close()
        }
        g.c.drawPath(p, paint(Palette.BOLT))
    }
}

/** "All clear" mark: a green disc with a bold white tick. Drawn, so it looks identical everywhere. */
object StatusIcons {
    private val cache = LruCache<Int, Bitmap>(8)

    fun allClear(px: Int): Bitmap {
        cache.get(px)?.let { return it }
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = px.toFloat()
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, s, s, Color.parseColor("#4CCB7E"), Color.parseColor("#2A9A57"), Shader.TileMode.CLAMP)
        }
        c.drawCircle(s / 2, s / 2, s / 2, disc)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = s * 0.12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val p = Path().apply {
            moveTo(s * 0.29f, s * 0.53f)
            lineTo(s * 0.45f, s * 0.68f)
            lineTo(s * 0.72f, s * 0.35f)
        }
        c.drawPath(p, tick)
        cache.put(px, bmp)
        return bmp
    }
}

@Composable
fun AllClearIcon(size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(16)
    val bmp = remember(px) { StatusIcons.allClear(px).asImageBitmap() }
    Image(bitmap = bmp, contentDescription = "All clear", modifier = modifier.size(size))
}

/** The artwork at [size], crisp at the device's density. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(16)
    val bmp = remember(code, isDay, px) { WeatherIcons.bitmap(code, isDay, px).asImageBitmap() }
    Image(bitmap = bmp, contentDescription = null, modifier = modifier.size(size))
}
