pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NoteTaker"
include(":app")
include(":core:data")
include(":core:domain")
include(":core:network")
include(":core:ui")
include(":feature:workspace")
include(":feature:editor")
include(":feature:conflict")
include(":feature:auth")
