package com.presencestudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.presencestudio.MainViewModel
import com.presencestudio.roblox.RobloxPresence
import com.presencestudio.ui.theme.*

@Composable
fun RobloxPresenceCard(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf(viewModel.robloxUsername) }
    val game = viewModel.robloxGame

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .padding(16.dp)
    ) {
        Text(
            text = "Roblox ของคุณกำลังเล่นอะไรอยู่",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                placeholder = { Text("ชื่อผู้ใช้ Roblox") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.setRobloxUsername(input) },
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("ตรวจสอบ")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (game != null && game.presenceType == 2) {
            Text(text = "กำลังเล่น", color = Success, fontSize = 12.sp)
            Text(
                text = game.gameName ?: game.lastLocation ?: "ไม่ทราบชื่อแมพ",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else if (game != null) {
            Text(
                text = RobloxPresence.presenceLabel(game.presenceType),
                color = TextSecondary,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "อัปเดตสถานะ Discord ตามเกมอัตโนมัติ",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = "ตั้งชื่อแมพที่กำลังเล่นเป็นสถานะ และคืนค่าเดิมเมื่อเลิกเล่น",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = viewModel.autoGameStatus,
                onCheckedChange = { viewModel.setAutoGameStatus(it) }
            )
        }

        if (viewModel.robloxStatus.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = viewModel.robloxStatus, color = TextTertiary, fontSize = 12.sp)
        }
    }
}
