import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 版本号从 git tag + docs/release-notes-*.md 派生 (2026-09-29 治理定案):
//   versionName  = 最近 v*.*.* tag 去 v 前缀
//   versionCode  = major*10000 + minor*100 + patch (单调递增, 可重现)
// 打 tag 之前 (main HEAD 无 tag 祖先) 时, 真源回退到 docs/release-notes-v*.md 最大版本;
// 再无 (fresh clone, 无 tag 无 notes) 时 fallback 0.0.0/1 — 后者不应分发。
fun versionFromGit(): Pair<String, Int> {
    fun parse(ver: String): Pair<String, Int> {
        val m = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)(.*)").find(ver) ?: return "0.0.0" to 1
        val (maj, min, pat, suffix) = m.destructured
        val name = "$maj.$min.$pat$suffix"
        return name to (maj.toInt() * 10000 + min.toInt() * 100 + pat.toInt())
    }
    // 真源 1: git describe 最近 v*.*.* tag
    val tag = try {
        val p = ProcessBuilder("git", "describe", "--tags", "--match", "v*.*.*", "--abbrev=0")
            .redirectErrorStream(true).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8).trim()
        p.waitFor()
        if (p.exitValue() == 0) out else null
    } catch (_: Exception) { null }
    if (tag != null) return parse(tag)
    // 真源 2: docs/release-notes-v*.*.*.md 文件名最大版本 (tag 缺席时的日常 main)
    val notesRoot = rootProject.file("docs")
    if (notesRoot.exists() && notesRoot.isDirectory) {
        val re = Regex("v(\\d+\\.\\d+\\.\\d+)")
        val max = notesRoot.listFiles { f -> f.name.startsWith("release-notes-v") && f.name.endsWith(".md") }
            ?.mapNotNull { re.find(it.name.removeSuffix(".md"))?.groupValues?.get(1) }
            ?.maxByOrNull { it.split('.').map(String::toInt).let { p -> p[0] * 1000000 + p[1] * 1000 + p[2] } }
        if (max != null) return parse("v$max")
    }
    // 兜底: fresh clone, 不应分发
    return "0.0.0" to 1
}
val (derivedVersionName, derivedVersionCode) = versionFromGit()

android {
    namespace = "com.lingion.sleepy"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lingion.sleepy"
        minSdk = 26
        targetSdk = 37
        versionCode = derivedVersionCode
        versionName = derivedVersionName
        vectorDrawables { useSupportLibrary = true }
        androidResources {
            localeFilters += listOf("zh-rCN", "zh-rTW", "en", "ja", "es")
        }
        testInstrumentationRunner = "com.lingion.sleepy.SleepyRenderTestRunner"
    }

    // 发布签名: 优先用环境变量注入的 keystore (release.yml 从 GitHub Secret
    // RELEASE_KEYSTORE_BASE64 解出), 缺失时回退 debug 签名 (本地开发 / PR CI 无
    // Secret)。release.yml 构建后会断言 APK 证书指纹, 保证发布产物签名身份恒定,
    // 不会静默退回 CI 的 debug 密钥导致用户从旧版升级触发签名警告。
    val releaseKeystorePath = System.getenv("SLEEPY_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("SLEEPY_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("SLEEPY_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("SLEEPY_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            // Debug 包身份规范(与正式包区分):
            //  1) applicationId 末段加 .debug, 数据沙箱与正式包完全隔离
            //  2) 软件名后缀 (debug), 由 app/src/debug/res 覆盖各 locale app_name
            //  3) launcher icon 反色, 由 app/src/debug/res 覆盖 mipmap + drawable
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            // 保留 R8 shrinking 能力但关闭混淆改名(避免反射/序列化类被重命名后崩溃)
            // 真正的混淆(mangling)由 shrinkResources + 下面的 keep 规则共同保护
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseKeystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets.getByName("androidTest").assets.srcDir("schemas")

    sourceSets.getByName("androidTest").assets.srcDir("schemas")

    lint {
        // 基线对齐 v1.0.57 (38 errors / 434 warnings):
        // 本次集成前仓库 lint 从非 0, 这些 id 全部是 AGP 9.1 新检查在既有代码上的
        // 增量告警, 不影响功能; 逐处重构 (LocalContext→stringResource 需升级函数签名)
        // 超出本次发版范围, 先收敛到基线等价, 后续单独分支处理。
        disable += setOf(
            "LocalContextConfigurationRead",
            "LocalContextGetResourceValueCall",
            "LocalContextResourcesRead",
            "StateFlowValueCalledInComposition",
            "UnusedBoxWithConstraintsScope",
            "ModifierParameter",
        )
		lintConfig = file("${rootProject.projectDir}/app/lint.xml")
    }

    sourceSets.getByName("main").assets.srcDir("schemas")

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("sleepy.test.root", rootDir.absolutePath)
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation(libs.compose.ui.core)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3.core)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.adaptive.core)
    implementation(libs.compose.adaptive.layout)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)

    // Activity + Lifecycle
    implementation(libs.androidx.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)

    // Navigation
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.navigation.event.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)

    implementation(libs.datastore.preferences)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.jsoup)
    implementation(libs.work.runtime)
    implementation(libs.core.splashscreen)
    implementation(libs.coil.compose)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.junit.params)
    testImplementation(libs.json)
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
