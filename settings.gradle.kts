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
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        // compose-markdown (com.github.jeziellago) 只在 JitPack 发布
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "WakeUpPure"
include(":app")