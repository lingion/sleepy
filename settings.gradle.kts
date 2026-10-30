pluginManagement {
    repositories {
        // 2026-09-29 CI 取证: maven.aliyun.com 对 GitHub US runner 间歇性故障
        // (502 或连接挂起), 挂起时毒性阻断整个插件解析链。插件 POM 都是小文件,
        // google()/mavenCentral()/gradlePluginPortal() 直连稳定, 故插件域不走镜像。
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
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // 2026-09-30 CI 取证: 依赖域同样被 aliyun 502 毒性阻断
        // (59 个依赖经 aliyun 解析, 一个 502 即挂起整个 releaseRuntimeClasspath),
        // 且 aliyun 404/502 不返回标准 maven 元数据错误。依赖域直连 google/mavenCentral。
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // compose-markdown (com.github.jeziellago) 只在 JitPack 发布
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "WakeUpPure"
include(":app")
include(":kyant-backdrop")
project(":kyant-backdrop").projectDir = file("third-party/kyant-backdrop")