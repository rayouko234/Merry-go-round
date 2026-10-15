package com.example.merrygoround.core.security

import java.util.UUID

object Ids {

    /** Non-guessable identifier (UUID v4) as required by the data-integrity rules. */
    fun newId(): String = UUID.randomUUID().toString()

    /** Session token: high-entropy random UUID. */
    fun newToken(): String = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")

    // Ambiguous characters (I, O, 0, 1) excluded so codes are easy to read aloud/type.
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Invitation code: 8 chars, ~41 bits of entropy, unique-checked against the DB. */
    fun newInviteCode(): String {
        val rnd = java.security.SecureRandom()
        return (1..8).map { CODE_ALPHABET[rnd.nextInt(CODE_ALPHABET.length)] }.joinToString("")
    }

    /** Human-friendly transaction reference, e.g. "MGR-7F3KQ2XA". */
    fun newReference(prefix: String): String {
        val rnd = java.security.SecureRandom()
        val body = (1..8).map { CODE_ALPHABET[rnd.nextInt(CODE_ALPHABET.length)] }.joinToString("")
        return "$prefix-$body"
    }
}
