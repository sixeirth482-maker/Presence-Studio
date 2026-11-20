package com.presencestudio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.presencestudio.ui.theme.Background
import com.presencestudio.ui.theme.Primary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class GlowBlob(
    val baseX: Float,
    val baseY: Float,
    val radius: Float,     // fraction of the longer screen side
    val color: Color,
    val speed: Float,      // integer multiplier so the loop closes seamlessly
    val offset: Float
)

private data class Mote(
    val x: Float,
    val y: Float,
    val size: Float,       // dp
    val speed: Float,      // integer multiplier so the loop closes seamlessly
    val alpha: Float
)

private val glowBlobs = listOf(
    GlowBlob(0.15f, 0.20f, 0.55f, Primary, 1f, 0f),
    GlowBlob(0.85f, 0.35f, 0.50f, Color(0xFF3A6BFF), 2f, 1.3f),
    GlowBlob(0.50f, 0.90f, 0.60f, Color(0xFFB05CFF), 1f, 2.6f)
)

private fun createMotes(count: Int): List<Mote> {
    val random = Random(7)
    return List(count) {
        Mote(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = 1f + random.nextFloat() * 1.5f,
            speed = if (random.nextBoolean()) 1f else 2f,
            alpha = 0.15f + random.nextFloat() * 0.35f
        )
    }
}

/**
 * Dark animated backdrop: drifting purple glow blobs that gently breathe,
 * plus slowly rising motes. Draws behind the whole app.
 */
@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "background")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val breathe by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val motes = remember { createMotes(count = 36) }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(Background)

        val angle = phase * 2f * PI.toFloat()
        val maxSide = maxOf(size.width, size.height)

        glowBlobs.forEach { blob ->
            val cx = size.width * (blob.baseX + 0.08f * cos(angle * blob.speed + blob.offset))
            val cy = size.height * (blob.baseY + 0.06f * sin(angle * blob.speed + blob.offset))
            val r = maxSide * blob.radius * breathe
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        blob.color.copy(alpha = 0.30f),
                        blob.color.copy(alpha = 0f)
                    ),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }

        motes.forEach { mote ->
            val y = ((mote.y - phase * mote.speed) % 1f + 1f) % 1f
            drawCircle(
                color = Color.White.copy(alpha = mote.alpha),
                radius = mote.size.dp.toPx(),
                center = Offset(mote.x * size.width, y * size.height)
            )
        }
    }
}
