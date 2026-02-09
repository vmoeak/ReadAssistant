package com.readassistant.navigation

sealed class Screen(val route: String) {
    // Bottom nav tabs
    data object FeedList : Screen("feeds")
    data object Library : Screen("library")
    data object Articles : Screen("articles")
    data object Settings : Screen("settings")

    // RSS
    data object ArticleList : Screen("feed/{feedId}/articles") {
        fun createRoute(feedId: Long) = "feed/$feedId/articles"
    }

    // Reader
    data object Reader : Screen("reader/{contentType}/{contentId}") {
        fun createRoute(contentType: String, contentId: String) = "reader/$contentType/$contentId"
    }

    // Web article
    data object UrlInput : Screen("url_input")

    // Library
    data object ImportBook : Screen("import_book")

    // Settings
    data object LlmConfig : Screen("llm_config")
}
