package com.presencestudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.presencestudio.MainViewModel
import com.presencestudio.data.ActivityProfile
import com.presencestudio.data.ConnectionState
import com.presencestudio.ui.components.DiscordProfileCover
import com.presencestudio.ui.components.RobloxPresenceCard
import com.presencestudio.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Presence Studio",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (viewModel.connectionState) {
                                ConnectionState.READY -> Success
                                ConnectionState.CONNECTING, ConnectionState.CONNECTED -> Color(0xFFFFB020)
                                else -> TextTertiary
                            }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        viewModel.discordUser?.let { user ->
            DiscordProfileCover(user = user, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Roblox current game
        RobloxPresenceCard(viewModel = viewModel)

        Spacer(modifier = Modifier.height(20.dp))

        // Preview Card
        PresencePreviewCard(
            profile = viewModel.currentProfile,
            isActive = viewModel.isPresenceActive,
            connectionState = viewModel.connectionState,
            username = viewModel.connectedUsername
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.startPresence() },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Primary, PrimaryVariant)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Start Presence",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.clearPresence() },
                modifier = Modifier
                    .weight(0.7f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(Border, Border))
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear")
            }
        }

        // Status message
        if (viewModel.statusMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = viewModel.statusMessage,
                color = TextTertiary,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Saved Profiles header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Saved Profiles",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Select a profile to load",
                    color = TextTertiary,
                    fontSize = 13.sp
                )
            }
            TextButton(onClick = {
                val newProfile = viewModel.createNewProfile()
                viewModel.addOrUpdateProfile(newProfile)
                viewModel.selectProfile(newProfile)
            }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Profile", color = Primary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Profiles row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(viewModel.profiles) { profile ->
                ProfileChip(
                    profile = profile,
                    isSelected = viewModel.currentProfile?.id == profile.id,
                    onClick = { viewModel.selectProfile(profile) }
                )
            }
        }
    }
}

@Composable
private fun PresencePreviewCard(
    profile: ActivityProfile?,
    isActive: Boolean,
    connectionState: ConnectionState,
    username: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RICH PRESENCE PREVIEW",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isActive) {
                    Text(
                        text = "● Live",
                        color = Success,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Success else TextTertiary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = profile?.details?.ifBlank { profile.name } ?: "No activity selected",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!profile?.state.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profile!!.state,
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Placeholder for game image
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceVariant)
                            .border(1.dp, Border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "GAME IMAGE",
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Started just now",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileChip(
    profile: ActivityProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Primary else Border
    val bgColor = if (isSelected) Primary.copy(alpha = 0.12f) else Surface

    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = if (isSelected) Primary else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = profile.name,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
