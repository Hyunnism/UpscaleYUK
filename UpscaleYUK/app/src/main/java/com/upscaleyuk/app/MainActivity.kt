package com.upscaleyuk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private external fun stringFromJNI(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val jniStatus = try {
            stringFromJNI()
        } catch (e: Throwable) {
            "Error: ${e.message}"
        }

        setContent {
            // Token warna Material 3 (DESIGN.md: surface #101312, onSurface #E1E4E2)
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF101312)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = jniStatus,
                        color = Color(0xFFE1E4E2),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    companion object {
        init {
            // Memuat lib native C++ yang dikompilasi oleh CMake/NDK
            System.loadLibrary("upscaleyuk")
        }
    }
}

