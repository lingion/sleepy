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
        androidResources { enable = true }
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
        withHostTestBuilder {}.configure {}
    }
    sourceSets {
        androidMain.dependencies {
            // Same runtime resolved by Sleepy's existing Compose BOM.
            api("androidx.compose.runtime:runtime:1.12.1")
            api("androidx.compose.ui:ui:1.12.1")
            implementation(libs.coroutines.core)
            implementation("io.github.kyant0:shapes:1.2.1")
            api(project(":kyant-backdrop"))
            api(libs.compose.material3.core)
            implementation("androidx.compose.foundation:foundation:1.12.1")
            implementation(libs.material.color.utilities)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}
