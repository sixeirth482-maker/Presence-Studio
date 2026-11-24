package com.presencestudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.presencestudio.MainViewModel
import com.presencestudio.ui.theme.*

@Composable
fun TemplatesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf("") }
    var idle by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Templates", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "บันทึกชุดข้อความไว้ใช้ซ้ำ กดใช้เพื่อเปลี่ยนสถานะ Discord ทันที",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("ชื่อเทมเพลต") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = playing,
                onValueChange = { playing = it },
                label = { Text("ข้อความตอนเล่น (state)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = idle,
                onValueChange = { idle = it },
                label = { Text("ข้อความตอนไม่ได้เข้าแมพ") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveTemplate(name, idle, playing)
                        name = ""
                        playing = ""
                        idle = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("บันทึกเทมเพลต")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (viewModel.templates.isEmpty()) {
            Text("ยังไม่มีเทมเพลต", color = TextTertiary, fontSize = 14.sp)
        }

        viewModel.templates.forEach { t ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .background(Surface, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(t.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("ตอนเล่น: ${t.playingText}", color = TextSecondary, fontSize = 13.sp)
                Text("ตอนไม่เล่น: ${t.idleText}", color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row {
                    Button(
                        onClick = { viewModel.applyTemplate(t) },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("ใช้")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.deleteTemplate(t.id) }) {
                        Text("ลบ")
                    }
                }
            }
        }
    }
}
