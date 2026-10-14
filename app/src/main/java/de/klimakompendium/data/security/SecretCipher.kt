package de.klimakompendium.data.security

import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretCipherException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Verschlüsselt kurze Geheimnisse (Controller-Passwörter) für die lokale Ablage.
 * Implementierungen dürfen den Klartext niemals loggen.
 */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** @throws SecretCipherException wenn das Chiffrat manipuliert ist oder der Schlüssel fehlt. */
    fun decrypt(token: String): String
}

/**
 * AES-256-GCM (authentifizierte Verschlüsselung). Format: `v1.<base64url(iv)>.<base64url(chiffrat+tag)>`.
 * Pro Verschlüsselung entsteht ein neuer zufälliger 96-Bit-IV, gleiche Passwörter ergeben also verschiedene Chiffrate.
 * Die Schlüsselherkunft ist über [keyProvider] austauschbar: Android Keystore in der App, Software-Schlüssel in Tests.
 */
class AesGcmSecretCipher(private val keyProvider: () -> SecretKey) : SecretCipher {
    private val b64e = Base64.getUrlEncoder().withoutPadding()
    private val b64d = Base64.getUrlDecoder()

    override fun encrypt(plain: String): String = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD(AAD)
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        "$VERSION.${b64e.encodeToString(cipher.iv)}.${b64e.encodeToString(ct)}"
    } catch (e: GeneralSecurityException) {
        throw SecretCipherException("Verschlüsselung fehlgeschlagen", e)
    }

    override fun decrypt(token: String): String = try {
        val parts = token.split('.')
        require(parts.size == 3 && parts[0] == VERSION) { "Unbekanntes Format" }
        val iv = b64d.decode(parts[1])
        val ct = b64d.decode(parts[2])
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(AAD)
        String(cipher.doFinal(ct), Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        throw SecretCipherException("Entschlüsselung fehlgeschlagen (Schlüssel fehlt oder Daten verändert)", e)
    } catch (e: IllegalArgumentException) {
        throw SecretCipherException("Ungültiges Chiffrat", e)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION = "v1"
        private const val TAG_BITS = 128
        private val AAD = "klimakompendium.controller-password.v1".toByteArray(Charsets.UTF_8)
    }
}

/** Hält den AES-Schlüssel im Android Keystore (hardwaregestützt, nicht exportierbar). */
object KeystoreKeyProvider {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "klimakompendium_controller_key_v1"

    @Synchronized
    fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }.generateKey()
    }

    fun cipher(): SecretCipher = AesGcmSecretCipher(::getOrCreateKey)
}
