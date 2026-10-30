import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    android {
        namespace = "com.kyant.backdrop"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
        withHostTestBuilder {}.configure {}
    }
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        commonMain.dependencies {
            implementation("androidx.compose.foundation:foundation:1.11.2")
            api("androidx.compose.ui:ui:1.11.2")
            implementation("io.github.kyant0:shapes:1.2.0")
        }
        // ContextParameters is stable in Kotlin 2.0+; enableLanguageFeature deprecated in Gradle Kotlin DSL
    }
}
