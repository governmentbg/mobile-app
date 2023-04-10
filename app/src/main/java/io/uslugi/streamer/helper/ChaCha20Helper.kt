package io.uslugi.streamer.helper

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ChaCha20 helper object that provides decryption capabilities
 * using the ChaCha20/Poly1305/NoPadding algorithm.
 */

object ChaCha20Helper {
    private const val ENCRYPT_ALGORITHM = "ChaCha20/Poly1305/NoPadding"
    private const val KEY_ALGORITHM = "ChaCha20"

    /**
     * Decrypts the passed in `cipherText` using passed in key and the nonce.
     * @param cipherText - text to be decrypted
     * @param key - the key to be used for decryption
     * @param nonce - the nonce to be used as part of the decryption
     */
    @Throws(java.lang.Exception::class)
    fun decryptData(cipherText: ByteArray, key: ByteArray, nonce: ByteArray): String? {
        val cipher = Cipher.getInstance(ENCRYPT_ALGORITHM)
        val iv = IvParameterSpec(nonce)
        val secretKey = SecretKeySpec(key, KEY_ALGORITHM)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)

        // decrypted text
        val decryptedData = cipher.doFinal(cipherText)

        return String(decryptedData)
    }

    /**
     * Slices the first 12 bytes from the passed in QR response.
     * These 12 bytes are the nonce
     * @param qrResponse - the data encoded in the QR code
     * @return 12 bytes nonce as [ByteArray]
     */
    fun getNonce(qrResponse: ByteArray): ByteArray =
        qrResponse.sliceArray(0..11)

    /**
     * Slices the cipher text to be decrypted from the passed in QR response.
     * The cipher is the rest of the bytes after slicing out the 12 bytes nonce.
     * @param qrResponse - the data encoded in the QR code
     * @return the cipher text as [ByteArray]
     */
    fun getCipher(qrResponse: ByteArray): ByteArray {
        val size = qrResponse.size - 1

        return qrResponse.sliceArray(12..size)
    }
}