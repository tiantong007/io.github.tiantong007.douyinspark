package com.worldmargin.autospark.core

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 资源内容指纹：把 APK 里「原样打包」的条目按固定顺序哈希，和构建期注入的
 * `BuildConfig.CONTENT_SHA256` 比对。
 *
 * 用内存 ZIP 解析，刻意不调 `ZipFile(path)` —— 那会重新通过 libc 打开文件，
 * 被 PLT/GOT hook 重定向到原始包就白算了。
 *
 * 参与计算的条目必须与 `src/app/build.gradle.kts` 的 `contentEntries` 完全一致。
 */
object ContentFingerprint {
    // 与 app/build.gradle.kts 的 contentEntries 一致。META-INF/xposed 不算：构建时会按混淆结果改写。
    private val PREFIXES =
        listOf(
            "assets/docs/manual.md",
            "assets/fonts/",
        )

    fun sha256(apkBytes: ByteArray): String =
        runCatching {
            // 先按条目名排序，再依次 update(name + bytes)，与构建期口径一致
            val found = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(apkBytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && PREFIXES.any { entry.name.startsWith(it) }) {
                        found[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val md = MessageDigest.getInstance("SHA-256")
            found.toSortedMap().forEach { (name, bytes) ->
                md.update(name.toByteArray(Charsets.UTF_8))
                md.update(bytes)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
}
