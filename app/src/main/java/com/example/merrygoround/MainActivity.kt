package com.example.merrygoround

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.example.merrygoround.di.AppContainer
import com.example.merrygoround.navigation.AppNavHost
import com.example.merrygoround.ui.theme.MerryGoRoundTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MerryGoRoundApp).container

        setContent {
            MerryGoRoundTheme {
                AppRoot(container)
            }
        }
    }
}

@Composable
private fun AppRoot(container: AppContainer) {
    // Resolve the persisted session once before choosing the start destination.
    // Window insets are handled per screen; the root must not pad or they'd double.
    val loggedIn by produceState(initialValue = false) {
        value = try {
            container.sessionStore.isLoggedIn.first()
        } catch (e: Exception) {
            false
        }
    }
    Surface {
        AppNavHost(container = container, loggedIn = loggedIn)
    }
}
