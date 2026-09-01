package com.lexis.words

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.lexis.words.ui.LexisNavHost
import com.lexis.words.ui.theme.LexisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LexisTheme {
                LexisNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
