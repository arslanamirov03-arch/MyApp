package com.discipline.hoeren

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.discipline.hoeren.ui.AppRoot
import com.discipline.hoeren.ui.AppState
import com.discipline.hoeren.ui.theme.DisziplinTheme

class MainActivity : ComponentActivity() {

    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = AppState(this)
        setContent {
            DisziplinTheme {
                AppRoot(state)
            }
        }
    }

    /** Возврат в приложение — перечитываем дату и время: день мог смениться. */
    override fun onResume() {
        super.onResume()
        state.refreshNow()
    }
}
