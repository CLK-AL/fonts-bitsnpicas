package com.kreative.bitsnpicas.ui

import java.security.MessageDigest

actual fun sha256Hex(data: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(data).joinToString("") { "%02x".format(it) }
}
