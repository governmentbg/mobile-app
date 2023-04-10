package io.uslugi.streamer.helper

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val SIK_SHARED_PREFERENCES = "SIK_SHARED_PREFERENCES"
private const val UDI_SHARED_PREFERENCES = "UDI_SHARED_PREFERENCES"
private const val RTMP_URL_SHARED_PREFERENCES = "RTMP_URL_SHARED_PREFERENCES"
private const val ELECTION_SHARED_PREFERENCES = "ELECTION_SHARED_PREFERENCES"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val MASTER_KEY_SIZE = 256
private const val KEYENC = "app_keyenc"

object SharedPreferencesHelper {

    // PUBLIC METHODS ⤵

    /**
     * Returns instance of EncryptedSharedPreferences
     *
     * @param fileName shared preferences file's name
     * @param context the context
     *
     * @return instance of EncryptedSharedPreferences
     */
    fun getEncryptedSharedPreferences(
        fileName: String,
        context: Context
    ): EncryptedSharedPreferences {
        val masterKey: MasterKey = getMasterKey(context)

        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    /**
     * Save or update uid in UDI shared preference
     * @param [udi] the uid to be added
     * @param [context] the context
     */
    fun storeUdi(udi: String, context: Context) {
        val preferences = getSharedPreferences(context)
        val preferencesEditor = preferences?.edit()

        preferencesEditor?.putString(UDI_SHARED_PREFERENCES, udi)
        preferencesEditor?.apply()
    }

    /**
     * Get UDI from shared preferences
     * @param [context] the context
     *
     * @return[String] - return the UDI, that needs
     */
    fun getUdi(context: Context): String {
        val preferences = getSharedPreferences(context)

        return preferences?.getString(UDI_SHARED_PREFERENCES, Constants.StringPlaceholders.EMPTY)
            ?: Constants.StringPlaceholders.EMPTY
    }

    /**
     * Save or update in shared preference the RTMP URL address for streaming
     * @param [url] the url to be added or updated
     * @param [context] the context
     */
    fun storeRtmpUrl(url: String?, context: Context) {
        val preferences = getSharedPreferences(context)
        val preferencesEditor = preferences?.edit()

        preferencesEditor?.putString(RTMP_URL_SHARED_PREFERENCES, url)
        preferencesEditor?.apply()
    }

    /**
     * Get the RTMP URL from shared preferences
     * @param [context] the context
     *
     * @return[String] - return the URL for streaming
     */
    fun getRtmpUrl(context: Context): String {
        val preferences = getSharedPreferences(context)
        return preferences?.getString(
            RTMP_URL_SHARED_PREFERENCES,
            Constants.StringPlaceholders.EMPTY
        )
            ?: Constants.StringPlaceholders.EMPTY
    }

    /**
     * Get the election name
     */
    fun getElection(context: Context): String {
        val preferences = getSharedPreferences(context)
        return preferences?.getString(
            ELECTION_SHARED_PREFERENCES,
            Constants.StringPlaceholders.EMPTY
        ) ?: Constants.StringPlaceholders.EMPTY
    }

    /**
     * Save the election name
     * @param election is the stored value - the name of the election
     */
    fun storeElection(election: String?, context: Context) {
        val preferences = getSharedPreferences(context)
        val preferencesEditor = preferences?.edit()

        preferencesEditor?.putString(
            ELECTION_SHARED_PREFERENCES,
            election
        )
        preferencesEditor?.apply()
    }

    /**
     * Stores keyenc
     *
     * @param keyenc key, that we need to decrypt the further QR codes
     * @param sharedPref shared preferences instance
     */
    fun storeKeyenc(keyenc: String, sharedPref: SharedPreferences) {
        with(sharedPref.edit()) {
            putString(KEYENC, keyenc)
            commit()
        }

        sharedPref.edit().apply {
            putString(KEYENC, keyenc)
            apply()
        }
    }

    /**
     * Extracts keyenc
     *
     * @param sharedPref shared preferences instance
     *
     * @return instance of keyenc
     */
    fun getKeyenc(sharedPref: SharedPreferences): String? = sharedPref.getString(KEYENC, null)

    // PRIVATE METHODS ⤵

    /**
     * Get Section shared preferences shared preferences
     * @param context the context
     *
     * @return `SharedPreferences`
     */
    private fun getSharedPreferences(context: Context?): SharedPreferences? =
        context?.getSharedPreferences(SIK_SHARED_PREFERENCES, Context.MODE_PRIVATE)

    /**
     * Returns the master key which encrypts/decrypts EncryptedSharedPreferences keySet
     * If the master key does not exist - it is created,
     * otherwise it is extracted from the keystore
     *
     * @param context the context
     *
     * @return the master key
     */
    private fun getMasterKey(context: Context): MasterKey {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            ANDROID_KEY_STORE,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(MASTER_KEY_SIZE)
            .build()

        val masteryKeyBuilder = MasterKey.Builder(context, ANDROID_KEY_STORE)
        masteryKeyBuilder.setKeyGenParameterSpec(keyGenParameterSpec)

        return masteryKeyBuilder.build()
    }
}