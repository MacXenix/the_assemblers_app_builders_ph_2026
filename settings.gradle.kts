pluginManagement {
    repositories {
        google()
        // Google-hosted Maven Central mirror (avoids Central rate limits on CI/cloud boxes)
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google-hosted Maven Central mirror (avoids Central rate limits on CI/cloud boxes)
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
    }
}
rootProject.name = "SnapOut"
include(":app")
