package com.worldmargin.autospark.app.config

import java.security.MessageDigest

object Hash {
    fun of(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
