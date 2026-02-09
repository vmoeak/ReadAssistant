package com.readassistant.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.readassistant.feature.chat.presentation.ChatBottomSheet
import com.readassistant.feature.library.presentation.ImportBookScreen
import com.readassistant.feature.library.presentation.LibraryScreen
import com.readassistant.feature.reader.presentation.reader.ReaderScreen
import com.readassistant.feature.rss.presentation.ArticleListScreen
import com.readassistant.feature.rss.presentation.FeedListScreen
import com.readassistant.feature.settings.presentation.LlmConfigScreen
import com.readassistant.feature.settings.presentation.SettingsScreen
import com.readassistant.feature.webarticle.presentation.SavedArticlesScreen
import com.readassistant.feature.webarticle.presentation.UrlInputScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.FeedList.route
    ) {
        // Bottom nav tabs
        composable(Screen.FeedList.route) {
            FeedListScreen(
                onFeedClick = { feedId ->
                    navController.navigate(Screen.ArticleList.createRoute(feedId))
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onBookClick = { bookId ->
                    navController.navigate(Screen.Reader.createRoute("BOOK", bookId.toString()))
                },
                onImportClick = {
                    navController.navigate(Screen.ImportBook.route)
                }
            )
        }

        composable(Screen.Articles.route) {
            SavedArticlesScreen(
                onArticleClick = { articleId ->
                    navController.navigate(Screen.Reader.createRoute("WEB_ARTICLE", articleId.toString()))
                },
                onAddClick = {
                    navController.navigate(Screen.UrlInput.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onLlmConfigClick = {
                    navController.navigate(Screen.LlmConfig.route)
                }
            )
        }

        // RSS article list
        composable(
            route = Screen.ArticleList.route,
            arguments = listOf(navArgument("feedId") { type = NavType.LongType })
        ) {
            ArticleListScreen(
                onArticleClick = { articleId ->
                    navController.navigate(Screen.Reader.createRoute("RSS_ARTICLE", articleId.toString()))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Reader
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("contentType") { type = NavType.StringType },
                navArgument("contentId") { type = NavType.StringType }
            )
        ) {
            ReaderScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // URL input
        composable(Screen.UrlInput.route) {
            UrlInputScreen(
                onArticleSaved = { articleId ->
                    navController.popBackStack()
                    navController.navigate(Screen.Reader.createRoute("WEB_ARTICLE", articleId.toString()))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Import book
        composable(Screen.ImportBook.route) {
            ImportBookScreen(
                onBookImported = { bookId ->
                    navController.popBackStack()
                    navController.navigate(Screen.Reader.createRoute("BOOK", bookId.toString()))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // LLM Config
        composable(Screen.LlmConfig.route) {
            LlmConfigScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
