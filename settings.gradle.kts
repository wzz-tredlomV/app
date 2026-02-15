pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Filament官方仓库
        maven { 
            url = uri("https://storage.googleapis.com/filament-android")
            content { 
                includeGroup("com.google.android.filament")
            }
        }
    }
}
rootProject.name = "Model3DViewer"
include(":app")
