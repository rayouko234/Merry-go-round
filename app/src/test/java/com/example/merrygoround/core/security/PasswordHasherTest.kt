package com.example.merrygoround.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `hash verifies correct password`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("s3curePass", salt)
        assertTrue(PasswordHasher.verify("s3curePass", salt, hash))
    }

    @Test
    fun `hash rejects wrong password`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("s3curePass", salt)
        assertFalse(PasswordHasher.verify("wrongPass1", salt, hash))
    }

    @Test
    fun `same password different salts produce different hashes`() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()
        assertNotEquals(PasswordHasher.hash("s3curePass", salt1), PasswordHasher.hash("s3curePass", salt2))
    }

    @Test
    fun `plaintext password never appears in hash or salt`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("s3curePass", salt)
        assertFalse(hash.contains("s3curePass"))
        assertFalse(salt.contains("s3curePass"))
    }
}
