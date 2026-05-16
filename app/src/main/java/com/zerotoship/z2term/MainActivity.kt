package com.zerotoship.z2term

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zerotoship.z2term.ui.terminal.TerminalScreen
import com.zerotoship.z2term.ui.theme.Z2TermTheme
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Z2TermTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ZtsBgPrimary
                ) {
                    TerminalScreen()
                }
            }
        }
    }
}
