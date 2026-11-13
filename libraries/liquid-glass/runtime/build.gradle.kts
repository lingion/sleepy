import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    android {
        namespace = "com.lingion.liquidglass"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
        withHostTestBuilder {}.configure {}
    }
    sourceSets {
        androidMain.dependencies {
            // Same runtime resolved by Sleepy's existing Compose BOM.
            api("androidx.compose.runtime:runtime:1.12.1")
            api("androidx.compose.ui:ui:1.12.1")
            implementation(libs.coroutines.core)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}
