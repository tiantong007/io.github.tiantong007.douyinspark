package com.worldmargin.autospark.core

import android.content.Context
import com.worldmargin.autospark.BuildConfig
import java.io.File

/**
 * 安装包自校验：签名证书指纹 + 内嵌资源指纹。
 *
 * **只记日志，不阻断运行。** 这是个人分发（侧载）的模块，误判一次就是用户打不开界面，
 * 成本远高于收益；真正的加固靠「模块不申请 INTERNET、配置只在私有目录、跨进程走
 * ConfigProvider」这几条，自校验只是留痕 —— 出问题时能一眼看出「装的是不是原包」。
 *
 * 数据来源刻意用 `applicationInfo.sourceDir` 读字节，不用 `PackageManager.getPackageInfo()
 * .signatures`：后者拿到的是框架对象，被 hook 的进程可以随便改，拿它自证清白没意义。
 */
object SelfCheck {
    /**
     * 发布签名证书的 SHA-256（小写十六进制）。
     *
     * 取自 `apksigner verify --print-certs release/douyinspark-arm64-v8a.apk`：
     * `CN=tiantong007, O=DouyinSpark, C=CN`。换 keystore 时必须同步改这里，否则每次启动
     * 都会记一条「签名不匹配」——反正不阻断，但日志会变吵。
     */
    private const val RELEASE_CERT_SHA256 = "4cb51a34bd636ae60ed2bf518be4b30db937b94cc31b6b4d1b19fd971ccf156b"

    /** APK 上限：超过这个大小说明读到的不是本模块的包，直接跳过，别把内存吃满。 */
    private const val MAX_APK_BYTES = 64L * 1024 * 1024

    @Volatile
    private var done = false

    /** 跑一次校验（幂等，重复调用直接返回）。启动路径调用，不要放在界面绘制里。 */
    fun runOnce(context: Context) {
        if (done) return
        done = true
        Thread({ verify(context) }, "spark-selfcheck").apply { isDaemon = true }.start()
    }

    private fun verify(context: Context) {
        // 模块代码也会跑在抖音进程里（Xposed 注入），那里的 sourceDir 是抖音的 APK，
        // 校验它毫无意义（签名当然不匹配），所以只在模块自己的进程里跑。
        if (context.packageName != BuildConfig.APPLICATION_ID) return

        val path = runCatching { context.applicationInfo.sourceDir }.getOrNull()
        if (path.isNullOrBlank()) {
            ModLog.w("自校验：拿不到 APK 路径，跳过")
            return
        }
        val file = File(path)
        if (!file.isFile || file.length() <= 0 || file.length() > MAX_APK_BYTES) {
            ModLog.w("自校验：APK 大小异常（${file.length()} B），跳过")
            return
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
        if (bytes == null) {
            ModLog.w("自校验：读不出 APK 字节，跳过")
            return
        }

        // 1. 签名证书
        val certs = ApkFingerprint.sha256Certificates(bytes)
        val hex = certs.map { der -> der.joinToString("") { b -> "%02x".format(b) } }
        when {
            hex.isEmpty() -> ModLog.w("自校验：APK 没有 v2/v3 签名块（只签了 v1？）")
            RELEASE_CERT_SHA256 in hex -> ModLog.i("自校验：签名 OK")
            else -> ModLog.w("自校验：签名不匹配，实际 ${hex.joinToString(",")}")
        }

        // 2. 内嵌资源（README → assets/docs/manual.md、assets/fonts/）
        val actual = ContentFingerprint.sha256(bytes)
        val expected = BuildConfig.CONTENT_SHA256
        when {
            expected.isBlank() -> ModLog.w("自校验：构建期没注入 CONTENT_SHA256")
            actual == expected -> ModLog.i("自校验：资源指纹 OK（${expected.take(8)}）")
            actual.isBlank() -> ModLog.w("自校验：APK 里找不到待校验资源")
            else ->
                ModLog.w(
                    "自校验：资源指纹不匹配（期望 ${expected.take(8)}…，实际 ${actual.take(8)}…）" +
                        "—— 内嵌文档/字体被改过，或构建期与运行时口径不一致",
                )
        }
    }
}
