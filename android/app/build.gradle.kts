import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
}

/**
 * 唯一的 application 模块 —— 只做续火花，单一产品形态，不再有 flavor。
 *
 * 业务全部在 `:core-spark`；本模块只装壳：入口类、设置页注入器、图标、字符串、清单。
 * 体积约 0.8 MB。
 *
 * 打包：`./gradlew :app:assembleRelease`
 */

// 签名信息读取自根目录 release-signing.properties（不入库），缺失时不签名
val releaseSigning =
    rootProject.file("release-signing.properties").takeIf { it.exists() }?.let { props ->
        Properties().apply { props.inputStream().use { load(it) } }
    }

android {
    namespace = "com.tiantong.spark.shell"
    buildToolsVersion = "36.0.0"
    compileSdk = 36

    if (releaseSigning != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        // 应用 id 与版本号唯一真源：发版只改这三行
        applicationId = "io.github.tiantong007.douyinspark"
        versionCode = 34800
        versionName = "v3.4.8"
        minSdk = 28
        targetSdk = 36
    }

    // 按 ABI 分包：arm64-v8a / armeabi-v7a 各出一个 APK
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-base.pro")
            signingConfig = releaseSigning?.let { signingConfigs["release"] }
        }
        debug {
            // 调试包与发布包同一签名（覆盖安装不丢登录态，便于真机联调）
            signingConfig = releaseSigning?.let { signingConfigs["release"] }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core-spark"))
    compileOnly(libs.libxposed.api)

}
