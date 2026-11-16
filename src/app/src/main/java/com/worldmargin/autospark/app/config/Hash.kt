package com.worldmargin.autospark.app.config

import java.security.MessageDigest

object Hash {
    fun of(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** 模板操作：继承 / 重置 / 同步。均为纯函数，便于在 UI 与宿主两侧一致地推理。 */
