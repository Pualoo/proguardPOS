package com.example.arduinoposproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel // Use compose viewmodel factory which is safer if activity-ktx is missing
import com.example.arduinoposproject.SecureIoTApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SecureIoTViewModel = viewModel()
            SecureIoTApp(viewModel)
        }
    }
}