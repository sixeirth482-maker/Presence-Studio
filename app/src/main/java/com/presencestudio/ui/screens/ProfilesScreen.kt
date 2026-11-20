package com.presencestudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.presencestudio.MainViewModel
import com.presencestudio.data.ActivityProfile
import com.presencestudio.ui.theme.*

@Composable
fun ProfilesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var editingProfile by remember { mutableStateOf<ActivityProfile?>(null) }

    if (editingProfile != null) {
        EditProfileScreen(
            profile = editingProfile!!,
            onSave = {
                viewModel.addOrUpdateProfile(it)
                editingProfile = null
            },
            onCancel = { editingProfile = null },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profiles",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = {
                editingProfile = viewModel.createNewProfile()
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(viewModel.profiles) { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    onClick = { viewModel.selectProfile(profile) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            if (profile.details.isNotBlank()) {
                                Text(
                                    text = profile.details,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                            if (profile.state.isNotBlank()) {
                                Text(
                                    text = profile.state,
                                    color = TextTertiary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        IconButton(onClick = { editingProfile = profile }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditProfileScreen(
    profile: ActivityProfile,
    onSave: (ActivityProfile) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(profile.name) }
    var details by remember { mutableStateOf(profile.details) }
    var state by remember { mutableStateOf(profile.state) }
    var largeKey by remember { mutableStateOf(profile.largeImageKey) }
    var largeText by remember { mutableStateOf(profile.largeImageText) }
    var smallKey by remember { mutableStateOf(profile.smallImageKey) }
    var smallText by remember { mutableStateOf(profile.smallImageText) }
    var appId by remember { mutableStateOf(profile.applicationId) }
    var showElapsed by remember { mutableStateOf(profile.showElapsed) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Edit Profile",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = Border,
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Primary,
            focusedLabelColor = Primary,
            unfocusedLabelColor = TextTertiary
        )

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Profile Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors, singleLine = true)
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("Details (e.g. Playing Roblox)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors, singleLine = true)
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State (e.g. Brookhaven RP)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors, singleLine = true)
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(value = largeKey, onValueChange = { largeKey = it }, label = { Text("Large Image Key") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors, singleLine = true)
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(value = largeText, onValueChange = { largeText = it }, label = { Text("Large Image Text") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors, singleLine = true)
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("Application ID (for assets)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = fieldColors, singleLine = true)

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show elapsed time", color = TextSecondary, modifier = Modifier.weight(1f))
            Switch(
                checked = showElapsed,
                onCheckedChange = { showElapsed = it },
                colors = SwitchDefaults.colors(checkedTrackColor = Primary)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = TextSecondary)
            }
            Button(
                onClick = {
                    onSave(
                        profile.copy(
                            name = name,
                            details = details,
                            state = state,
                            largeImageKey = largeKey,
                            largeImageText = largeText,
                            smallImageKey = smallKey,
                            smallImageText = smallText,
                            applicationId = appId,
                            showElapsed = showElapsed
                        )
                    )
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
