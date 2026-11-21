package com.presencestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.presencestudio.ui.PresenceApp
import com.presencestudio.ui.theme.Background
import com.presencestudio.ui.theme.PresenceStudioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.restoreSession()
        enableEdgeToEdge()
        setContent {
            PresenceStudioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    PresenceApp(viewModel = viewModel)
                }
            }
        }
    }
}
