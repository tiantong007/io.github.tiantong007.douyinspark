import java.security.MessageDigest

plugins {
    alias(libs.plugins.agp.library)
}

/**
 * 共享业务库：续火花引擎、配置模型、宿主 hook 桥、脚本引擎、界面组件。
 *
 * 这里放的是全部业务代码。` :app ` 只装壳（入口类、注入器、图标、字符串），
 * 单向依赖本模块。
 */

/**
 * 内容指纹：把「会原样进 APK 的资源」按固定顺序哈希，构建期注入 `BuildConfig.CONTENT_SHA256`。
 *
 * 运行时 `core/ContentFingerprint.kt` 用同一套规则读 APK 里的条目重算。
 * 参与计算的清单必须与 `ContentFingerprint.PREFIXES` 一致。
 */
val contentEntries: List<Pair<String, File>> =
    buildList {
        rootProject.file("../README.md").takeIf { it.isFile }?.let { add("assets/docs/manual.md" to it) }
        layout.projectDirectory
            .dir("src/main/assets/fonts")
            .asFile
            .walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(layout.projectDirectory.dir("src/main/assets/fonts").asFile)
                add("assets/fonts/" + rel.path.replace('\\', '/') to file)
            }
    }.sortedBy { it.first }

fun computeContentSha256(): String =
    MessageDigest.getInstance("SHA-256").let { md ->
        contentEntries.forEach { (name, file) ->
            md.update(name.toByteArray(Charsets.UTF_8))
            md.update(file.readBytes())
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

android {
    namespace = "com.tiantong.spark"
    buildToolsVersion = "36.0.0"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        buildConfigField("String", "CONTENT_SHA256", "\"${computeContentSha256()}\"")
    }

    buildFeatures {
        buildConfig = true
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
    // Xposed API 只在注入进程里存在，编译期可见、不进 APK
    compileOnly(libs.libxposed.api)
    implementation(libs.rhino)
    implementation(libs.webkit)
}

/**
 * 使用文档：**仓库根的 `README.md` 是唯一来源**。
 *
 * README 既给网页看，也是 App 内「使用文档」的内容 —— 一份文件两处用，不会出现
 * 「网页上写了、App 里没更新」这种分叉。构建时复制进 `assets/docs/manual.md`，
 * 那份是构建产物，不入库（见 android/.gitignore）。
 */
val manualSource = layout.projectDirectory.file("../../README.md")

val syncManual =
    tasks.register<Copy>("syncManual") {
        onlyIf { manualSource.asFile.exists() }
        from(manualSource)
        into(layout.projectDirectory.dir("src/main/assets/docs"))
        rename { "manual.md" }
    }

tasks.named("preBuild") { dependsOn(syncManual) }
