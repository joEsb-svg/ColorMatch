package it.joesb.colormatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

enum class Screen { Home, Verify, Refs, NewRef, Settings, Calibrate }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent {
            ColorMatchTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    App(store)
                }
            }
        }
    }
}

@Composable
fun App(store: Store) {
    val stack = remember { mutableStateListOf(Screen.Home) }
    val go: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    BackHandler(enabled = stack.size > 1) { back() }

    when (stack.last()) {
        Screen.Home -> HomeScreen(store, go)
        Screen.Verify -> VerifyScreen(store, back, go)
        Screen.Refs -> RefsScreen(store, back, go)
        Screen.NewRef -> NewRefScreen(store, back, go)
        Screen.Settings -> SettingsScreen(store, back, go)
        Screen.Calibrate -> CalibrateScreen(store, back)
    }
}
