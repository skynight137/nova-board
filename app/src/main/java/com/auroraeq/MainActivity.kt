package com.auroraeq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.auroraeq.app.presentation.navigation.AppNavigation
import com.auroraeq.app.presentation.theme.AuroraEqTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuroraEqTheme {
                AppNavigation()
            }
        }
    }
}
