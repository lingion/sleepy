import org.gradle.api.tasks.testing.Test
import groovy.json.JsonGenerator
import groovy.json.JsonSlurper

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
        val result = providers.exec {
            commandLine("git", "describe", "--tags", "--match", "v*.*.*", "--abbrev=0")
            isIgnoreExitValue = true
        }
        if (result.result.get().exitValue == 0) result.standardOutput.asText.get().trim() else null
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

// Keep the complete location audit in Git; ship only the map consumed by SchoolCityIndex.
val runtimeAssets = layout.buildDirectory.dir("generated/runtimeAssets")
val prepareRuntimeAssets = tasks.register<Sync>("prepareRuntimeAssets") {
    from("src/main/assets") { exclude("school_cities.json", "schools.json") }
    into(runtimeAssets)
    val cities = layout.projectDirectory.file("src/main/assets/school_cities.json")
    val schools = layout.projectDirectory.file("src/main/assets/schools.json")
    inputs.file(cities).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(schools).withPathSensitivity(PathSensitivity.RELATIVE)
    doLast {
        val source = JsonSlurper().parse(cities.asFile) as Map<*, *>
        val json = JsonGenerator.Options().disableUnicodeEscaping().build()
        runtimeAssets.get().file("school_cities.json").asFile.writeText(
            json.toJson(mapOf("schools" to checkNotNull(source["schools"])))
        )
        runtimeAssets.get().file("schools.json").asFile.writeText(json.toJson(JsonSlurper().parse(schools.asFile)))
    }
}

val catalogTestResources = tasks.register<Sync>("catalogTestResources") {
    from("src/main/assets/schools.json") { into("jw") }
    into(layout.buildDirectory.dir("generated/catalogTestResources"))
}

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

        // Agent Debug Console 解锁口令 (SPEC §4): 编译期常量, 仅参与 /v1/enable 校验,
        // 不落日志/不进响应体/不写文件。build 时可覆盖: -Psleepy.console.passphrase=...
        val consolePassphrase = (findProperty("sleepy.console.passphrase") as String?)
            ?.takeIf { it.isNotBlank() } ?: "sleepy.is-my.app"
        buildConfigField("String", "CONSOLE_ENABLE_PASSPHRASE", "\"$consolePassphrase\"")
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
            isShrinkResources = true
            // R8 removes unused code and resources. Reflection rules remain in proguard-rules.pro.
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

    sourceSets.getByName("androidTest").assets.directories.add("schemas")

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

    sourceSets.getByName("main").assets.directories.apply {
        clear()
        add(runtimeAssets.get().asFile.path)
    }
    sourceSets.getByName("test").resources.directories.add(
        layout.buildDirectory.dir("generated/catalogTestResources").get().asFile.path
    )

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
    systemProperty("sleepy.test.settingsScreenshots", providers.gradleProperty("settingsScreenshots").getOrElse("false"))
}

tasks.named("preBuild") { dependsOn(prepareRuntimeAssets) }
tasks.matching { it.name.endsWith("UnitTestJavaRes") }.configureEach {
    dependsOn(catalogTestResources)
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

    // 液态玻璃引擎 (Kyant0/AndroidLiquidGlass 2.0.0, third-party/kyant-backdrop)
    implementation(project(":kyant-backdrop"))
    implementation(project(":liquid-glass-runtime"))
    implementation("io.github.kyant0:shapes:1.2.1")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.jsoup)
    implementation(libs.work.runtime)
    // Agent Debug Console HTTP 服务器 (SPEC §4 用户预授权的唯一新增依赖)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation(libs.core.splashscreen)

    // HCT 色调运算 (液态玻璃暗色 tint / 镜片托盘), 移植自 FLClash 的
    // material-color-utilities Hct; com.google.android.material:material-color-utilities
    // 不存在于任何 Maven 仓库, 故用 API 等价的 studio.lunabee.compose 分发包。
    implementation(libs.material.color.utilities)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.sqlite.jdbc)
    // JVM-side pixel golden tests (Robolectric NATIVE graphics = real LayoutLib
    // renderer, no emulator/device). Baselines live in app/src/test/golden/.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(composeBom)
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    // ConsoleAcceptanceAndroidTest makes real HTTP requests to the debug server.
    androidTestImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
