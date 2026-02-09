pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ReadAssistant"

include(":app")
include(":core:core-ui")
include(":core:core-data")
include(":core:core-network")
include(":core:core-domain")
include(":core:core-llm")
include(":feature:feature-rss")
include(":feature:feature-library")
include(":feature:feature-reader")
include(":feature:feature-webarticle")
include(":feature:feature-translation")
include(":feature:feature-chat")
include(":feature:feature-settings")
