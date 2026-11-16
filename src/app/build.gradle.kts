import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
}

val gitCommitCount = 1

// 签名信息读取自根目录 release-signing.properties（不入库），缺失时不签名
val releaseSigning =
    rootProject.file("release-signing.properties").takeIf { it.exists() }?.let { props ->
        Properties().apply { props.inputStream().use { load(it) } }
    }

android {
    namespace = "com.worldmargin.autospark"
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
        applicationId = "io.github.tiantong007.douyinspark"
        minSdk = 26
        targetSdk = 36
        // 固定版本号：历史压缩后提交数会变小，不能再拿它当 versionCode（会触发降级安装失败）
        versionCode = 20001
        versionName = "v2.0.0"
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
            proguardFiles("proguard-rules.pro")
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
    compileOnly(libs.libxposed.api)
    implementation(libs.rhino)
    implementation(libs.webkit)
}

/**
 * 使用文档：**仓库根的 `README.md` 是唯一来源**。
 *
 * README 既给网页看，也是 App 内「使用文档」的内容 —— 一份文件两处用，不会出现
 * 「网页上写了、App 里没更新」这种分叉。构建时复制进 `assets/docs/manual.md`，
 * 那份是构建产物，不入库（见 src/.gitignore）。
 */
val manualSource = layout.projectDirectory.file("../../README.md")

/** `assets/docs/manual.md`：App 内使用文档，构建时从仓库根 README.md 复制，不入库。 */
val syncManual =
    tasks.register<Copy>("syncManual") {
        onlyIf { manualSource.asFile.exists() }
        from(manualSource)
        into(layout.projectDirectory.dir("src/main/assets/docs"))
        rename { "manual.md" }
    }

tasks.named("preBuild") { dependsOn(syncManual) }
