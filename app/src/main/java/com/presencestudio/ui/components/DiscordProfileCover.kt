package com.presencestudio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.presencestudio.discord.DiscordUser
import com.presencestudio.ui.theme.*

@Composable
fun DiscordProfileCover(
    user: DiscordUser,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "cover")

    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val accent = user.accentColor
        ?.let { Color(0xFF000000L or (it.toLong() and 0xFFFFFFL)) }
        ?: Primary

    Box(
        modifier = modifier
            .height(190.dp)
            .clip(RoundedCornerShape(22.dp))
    ) {
        val bannerUrl = user.bannerUrl
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = "Discord banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(accent, PrimaryVariant, Primary)))
            )
        }

        // แสงวิ่งผ่านปก
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.22f * pulse),
                            Color.Transparent
                        ),
                        start = Offset(sweep * 1200f - 400f, 0f),
                        end = Offset(sweep * 1200f, 400f)
                    )
                )
        )

        // ไล่สีเข้มด้านล่างให้ตัวหนังสืออ่านง่าย
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Background.copy(alpha = 0.92f)),
                        startY = 80f
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // วงแหวนเรืองแสงรอบรูปโปรไฟล์
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.35f * pulse))
            ) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = "Discord avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = user.displayName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${user.username}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
