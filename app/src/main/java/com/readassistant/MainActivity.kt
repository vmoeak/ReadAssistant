package com.readassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.readassistant.core.data.datastore.UserPreferences
import com.readassistant.core.ui.theme.ReadAssistantTheme
import com.readassistant.core.ui.theme.ReadingThemeType
import com.readassistant.navigation.BottomNavBar
import com.readassistant.navigation.NavGraph
import com.readassistant.navigation.Screen
import com.readassistant.navigation.bottomNavItems
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeType by userPreferences.themeType.collectAsState(initial = "LIGHT")

            ReadAssistantTheme(
                themeType = try {
                    ReadingThemeType.valueOf(themeType)
                } catch (_: Exception) {
                    ReadingThemeType.LIGHT
                }
            ) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val showBottomBar = currentRoute in bottomNavItems.map { it.route }

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(navController)
                        }
                    }
                ) { innerPadding ->
                    NavGraph(
                        navController = navController,
                    )
                }
            }
        }
    }
}
