plugins {
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

val benchmarkSuppressErrors = providers.gradleProperty("androidx.benchmark.suppressErrors").orNull

android {
    namespace = "com.lingion.sleepy.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        benchmarkSuppressErrors?.let {
            testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = it
        }
    }

    targetProjectPath = ":app"
}

dependencies {
    implementation(libs.macrobenchmark.junit4)
    implementation(libs.androidx.test.junit)
    implementation(libs.test.ui.automator)
}
