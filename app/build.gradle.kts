import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
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
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.material3:material3-window-size-class:1.5.0-alpha28")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha28")
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.material3.adaptive:adaptive-layout")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")

    // Activity + Lifecycle
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // Navigation
    // issue#45: typed back stack + gesture-progress predictive back.
    // 官方 navigation3 1.1.7 (kotlin-stdlib 2.1.20, 与本仓 Kotlin 2.2.0 编译器兼容;
    // miuix-nav 0.9.4 metadata 2.4.0 超出 compiler 2.3.0 上限已弃用)。
    implementation("androidx.navigation3:navigation3-runtime:1.1.7")
    implementation("androidx.navigation3:navigation3-ui:1.1.7")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0")

    // DataStore (preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room
    val roomVersion = "2.7.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Kotlinx Serialization (JSON parsing for WakeUp JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // jsoup (HTML parsing for 教务直连 import)
    implementation("org.jsoup:jsoup:1.18.1")

    // WorkManager (Daily notifications)
    // Glance 依赖已随死代码删除移除(决策 D5-11): 5 个生产 widget 全走 RemoteViews + Canvas bitmap
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testImplementation("org.json:json:20231013")
    // issue#26: 迁移测试用 sqlite-jdbc 直接执行 MIGRATION_5_6_STATEMENTS(单一事实来源),
    //             避免 Robolectric/Instrumentation 依赖(本仓库无)
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
