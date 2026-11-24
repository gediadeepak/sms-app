@file:Suppress("DEPRECATION") // Movie provides dependency-free GIF playback on the minimum API 24.
package com.gediadeepak.smsapp.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Movie
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gediadeepak.smsapp.R

@Composable
fun SandeshLogo(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.sandesh_logo), contentDescription = "Sandesh: a winged message lifted by a hand",
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary), modifier = modifier)
}

/** Plays the supplied 27-frame GIF once, preserving its hand, envelope, wordmark and timing. */
@Composable
fun SandeshSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val movie = remember { context.resources.openRawResource(R.raw.sandesh_flight).use { Movie.decodeStream(it) } }
    val progress = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    val duration = movie?.duration()?.takeIf { it > 0 } ?: 1800
    val accent = MaterialTheme.colorScheme.primary
    // Key the source's opaque cream background (255,249,238) into alpha and theme-tint
    // the remaining artwork. This operates at playback, leaving the supplied GIF intact.
    val paint = remember(accent) { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, accent.red * 255f,
            0f, 0f, 0f, 0f, accent.green * 255f,
            0f, 0f, 0f, 0f, accent.blue * 255f,
            -1f, -1f, -1f, 0f, 742f,
        )))
    } }
    BackHandler(onBack = onFinished)
    // Animatable honours Android's animator duration scale, including disabled animations.
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(duration, easing = LinearEasing)); finish() }
    Box(Modifier.fillMaxSize().background(Canvas).pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
    }, contentAlignment = Alignment.Center) {
        if (movie == null) SandeshLogo(Modifier.size(220.dp))
        else Canvas(Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(1f)
            .semantics { contentDescription = "Sandesh is opening: a winged message flying above a supporting hand" }) {
            val time = (progress.value * duration).toInt().coerceIn(0, duration - 1)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(size.width / movie.width(), size.height / movie.height())
                movie.setTime(time)
                movie.draw(native, 0f, 0f, paint)
                native.restore()
            }
        }
    }
}
