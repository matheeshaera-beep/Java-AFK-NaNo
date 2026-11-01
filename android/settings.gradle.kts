// Network note: dl.google.com, maven.google.com and plugins.gradle.org are not
// reachable from this build machine. The Tencent public mirror proxies Google
// Maven, Maven Central and the Gradle Plugin Portal, so it is listed first and
// the official repositories stay as fallbacks for machines that can reach them.
pluginManagement {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
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
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        google()
        mavenCentral()
    }
}

rootProject.name = "afk"
include(":app")
include(":engine")
