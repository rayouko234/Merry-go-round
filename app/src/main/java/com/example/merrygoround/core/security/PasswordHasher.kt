package com.example.merrygoround.core.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashing for the local (MVP) backend. Plaintext passwords are never stored.
 * Uses PBKDF2-HMAC-SHA256 with a per-user random salt, as required by the brief (§9).
 * The Django backend uses Django's own hasher for remote mode.
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(password: String, saltHex: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            saltHex.hexToBytes(),
            ITERATIONS,
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return try {
            factory.generateSecret(spec).encoded.toHex()
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(password: String, saltHex: String, expectedHashHex: String): Boolean {
        val actual = hash(password, saltHex)
        // Constant-time-ish comparison.
        if (actual.length != expectedHashHex.length) return false
        var diff = 0
        for (i in actual.indices) {
            diff = diff or (actual[i].code xor expectedHashHex[i].code)
        }
        return diff == 0
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
