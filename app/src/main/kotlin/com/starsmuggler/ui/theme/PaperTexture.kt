package com.starsmuggler.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.random.Random

private const val GRAIN_TILE = 256

// Fixed seed: the grain looks the same on every screen and every launch.
private const val GRAIN_SEED = 1982

/**
 * A tile of aged-paper grain: specks of ink and lighter fibres with low alpha, drawn
 * repeated over a flat colour so no texture image has to ship with the app.
 */
fun paperGrainTile(size: Int = GRAIN_TILE, seed: Int = GRAIN_SEED): ImageBitmap {
    val random = Random(seed)
    val pixels = IntArray(size * size)
    val dark = Ink.toArgb() and 0x00FFFFFF
    val light = Color.White.toArgb() and 0x00FFFFFF
    for (i in pixels.indices) {
        val roll = random.nextFloat()
        pixels[i] = when {
            roll < 0.012f -> (random.nextInt(40, 90) shl 24) or dark
            roll < 0.20f -> (random.nextInt(8, 22) shl 24) or dark
            roll < 0.32f -> (random.nextInt(10, 30) shl 24) or light
            else -> 0
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Fills the element with [color] and the repeated paper grain on top. */
fun Modifier.paper(grain: ImageBitmap, color: Color = Paper): Modifier = drawBehind {
    drawRect(color)
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)))
}

/** A full-size box of grained paper, the background of every screen. */
@Composable
fun PaperBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val grain = remember { paperGrainTile() }
    Box(modifier.paper(grain), content = content)
}
