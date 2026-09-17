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
        maven("https://jitpack.io") {
            content { includeGroup("com.github.topjohnwu.libsu") }
        }
    }
}

// Local miuix source (same approach as InstallerX Revived).
// Path is machine-specific; override with -PmiuixDir=... if needed.
val miuixDir = providers.gradleProperty("miuixDir")
    .orElse("D:/Tools/miuix")
    .get()

includeBuild(miuixDir) {
    dependencySubstitution {
        substitute(module("top.yukonga.miuix.kmp:miuix-core-android")).using(project(":miuix-core"))
        substitute(module("top.yukonga.miuix.kmp:miuix-ui-android")).using(project(":miuix-ui"))
        substitute(module("top.yukonga.miuix.kmp:miuix-shader-android")).using(project(":miuix-shader"))
        substitute(module("top.yukonga.miuix.kmp:miuix-blur-android")).using(project(":miuix-blur"))
        substitute(module("top.yukonga.miuix.kmp:miuix-preference-android")).using(project(":miuix-preference"))
    }
}

rootProject.name = "Quieta"
include(":app")
include(":core")
include(":ui")
