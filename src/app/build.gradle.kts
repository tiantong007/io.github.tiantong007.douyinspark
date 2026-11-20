import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
}

// ===== 版本号唯一真源：发版只改这两行 =====
val appVersionCode = 20009
val appVersionName = "v2.2.1"

// 签名信息读取自根目录 release-signing.properties（不入库），缺失时不签名
val releaseSigning =
    rootProject.file("release-signing.properties").takeIf { it.exists() }?.let { props ->
        Properties().apply { props.inputStream().use { load(it) } }
    }

/**
 * 内容指纹：把「会原样进 APK 的资源」按固定顺序哈希，构建期注入 `BuildConfig.CONTENT_SHA256`。
 *
 * 运行时 `core/ContentFingerprint.kt` 用同一套规则读 APK 里的条目重算，签名之外再验一层
 * 资源完整性（改了内嵌文档 / 字体而不重编，指纹就对不上）。只取原样复制的条目 ——
 * 编译过的 res 两次算出来的字节不一样，不能进这里；`META-INF/xposed` 会被 R8 按混淆结果改写，
 * 源码与 APK 不一致，同样不算。
 *
 * 参与计算的清单必须与 `ContentFingerprint.PREFIXES` 一致。
 */
val contentEntries: List<Pair<String, File>> =
    buildList {
        // 文档的唯一来源是仓库根的 README.md（见文件末尾 syncManual），
        // 所以这里直接读源文件：配置期就定下来，不受 preBuild 复制顺序影响。
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

val contentSha256: String = computeContentSha256()

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
        // 真源在文件顶部 appVersionCode / appVersionName，这里只引用
        versionCode = appVersionCode
        versionName = appVersionName
        // 内容指纹真源：运行时由 ContentFingerprint 重算后比对（见 core/ContentFingerprint.kt）
        buildConfigField("String", "CONTENT_SHA256", "\"$contentSha256\"")
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
