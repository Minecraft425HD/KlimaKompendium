package de.klimakompendium.data

import de.klimakompendium.data.security.AesGcmSecretCipher
import de.klimakompendium.data.security.SecretCipherException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.KeyGenerator

class SecretCipherTest {
    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val key = newKey()
    private val cipher = AesGcmSecretCipher { key }

    @Test fun roundTrip() {
        for (pw in listOf("geheim", "Pässwörd!äöü ß €", "a".repeat(500), " ", "🔑 emoji")) {
            assertEquals(pw, cipher.decrypt(cipher.encrypt(pw)))
        }
    }

    @Test fun ciphertextDoesNotContainPlaintext() {
        val pw = "SuperGeheim123"
        val token = cipher.encrypt(pw)
        assertFalse(token.contains(pw))
        assertFalse(token.contains("SuperGeheim"))
    }

    @Test fun sameInputGivesDifferentCiphertexts() {
        assertNotEquals(cipher.encrypt("x"), cipher.encrypt("x"))
    }

    @Test fun tamperedCiphertextIsRejected() {
        val parts = cipher.encrypt("passwort").split('.').toMutableList()
        val ct = parts[2].toCharArray()
        ct[0] = if (ct[0] == 'A') 'B' else 'A'
        parts[2] = String(ct)
        assertThrows(SecretCipherException::class.java) { cipher.decrypt(parts.joinToString(".")) }
    }

    @Test fun wrongKeyIsRejected() {
        val token = cipher.encrypt("passwort")
        val other = AesGcmSecretCipher { newKey() }
        assertThrows(SecretCipherException::class.java) { other.decrypt(token) }
    }

    @Test fun garbageIsRejected() {
        assertThrows(SecretCipherException::class.java) { cipher.decrypt("kein-chiffrat") }
        assertThrows(SecretCipherException::class.java) { cipher.decrypt("v1.@@@.@@@") }
        assertThrows(SecretCipherException::class.java) { cipher.decrypt("") }
    }
}
