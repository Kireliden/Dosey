package com.borealis.dosey.companion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted persistent storage for per-PC Companion credentials.
 *
 * Only one-way token verifiers are persisted; a raw credential is returned once by [issue].
 */
internal object CompanionCredentialStore {
    data class Client(
        val id: String,
        val name: String,
        val createdAt: Long,
        val revokedAt: Long?
    )

    data class IssuedCredential(
        val client: Client,
        val token: String
    )

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "dosey_companion_credentials_v1"
    private const val PREFS_NAME = "dosey_companion_credentials"
    private const val ENCRYPTED_DATA_KEY = "encrypted_clients"
    private const val TOKEN_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private val random = SecureRandom()

    @Synchronized
    fun list(context: Context): List<Client> =
        readRecords(context).map { it.client }.sortedByDescending { it.createdAt }

    @Synchronized
    fun issue(context: Context, clientName: String): IssuedCredential {
        val name = clientName.trim()
        require(name.isNotEmpty() && name.length <= MAX_CLIENT_NAME_LENGTH) {
            "Invalid Companion client name"
        }

        val records = readRecords(context).toMutableList()
        check(records.none { it.client.revokedAt == null && it.client.name == name }) {
            "An active Companion client with this name already exists"
        }

        val tokenBytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        val token = Base64.encodeToString(
            tokenBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        tokenBytes.fill(0)
        val client = Client(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = System.currentTimeMillis(),
            revokedAt = null
        )
        records += StoredRecord(client, sha256(token))
        writeRecords(context, records)
        return IssuedCredential(client, token)
    }

    @Synchronized
    fun revoke(context: Context, clientId: String): Boolean {
        val records = readRecords(context).toMutableList()
        val index = records.indexOfFirst { it.client.id == clientId && it.client.revokedAt == null }
        if (index < 0) return false
        val current = records[index]
        records[index] = current.copy(
            client = current.client.copy(revokedAt = System.currentTimeMillis())
        )
        writeRecords(context, records)
        return true
    }

    @Synchronized
    fun isAuthenticated(context: Context, token: String): Boolean {
        if (token.length !in MIN_TOKEN_LENGTH..MAX_TOKEN_LENGTH) return false
        val candidate = sha256(token)
        return readRecords(context).any { record ->
            record.client.revokedAt == null &&
                MessageDigest.isEqual(
                    record.tokenVerifier.toByteArray(StandardCharsets.US_ASCII),
                    candidate.toByteArray(StandardCharsets.US_ASCII)
                )
        }
    }

    private data class StoredRecord(
        val client: Client,
        val tokenVerifier: String
    )

    private fun readRecords(context: Context): List<StoredRecord> {
        val encoded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ENCRYPTED_DATA_KEY, null) ?: return emptyList()
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > IV_BYTES) { "Companion credential store is corrupt" }
        val iv = packed.copyOfRange(0, IV_BYTES)
        val ciphertext = packed.copyOfRange(IV_BYTES, packed.size)
        val plaintext = decrypt(getOrCreateKey(), iv, ciphertext)
        return try {
            val array = JSONArray(String(plaintext, StandardCharsets.UTF_8))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val revoked = if (item.isNull("revokedAt")) null else item.getLong("revokedAt")
                val client = Client(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    createdAt = item.getLong("createdAt"),
                    revokedAt = revoked
                )
                val verifier = item.getString("tokenVerifier")
                require(verifier.matches(Regex("[a-f0-9]{64}"))) {
                    "Companion credential record is invalid"
                }
                StoredRecord(client, verifier)
            }
        } finally {
            plaintext.fill(0)
        }
    }

    private fun writeRecords(context: Context, records: List<StoredRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.client.id)
                    .put("name", record.client.name)
                    .put("createdAt", record.client.createdAt)
                    .put("revokedAt", record.client.revokedAt ?: JSONObject.NULL)
                    .put("tokenVerifier", record.tokenVerifier)
            )
        }
        val plaintext = array.toString().toByteArray(StandardCharsets.UTF_8)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val ciphertext = try {
            encrypt(getOrCreateKey(), iv, plaintext)
        } finally {
            plaintext.fill(0)
        }
        val packed = iv + ciphertext
        val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
        check(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(ENCRYPTED_DATA_KEY, encoded)
                .commit()
        ) { "Could not persist Companion credentials" }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(key: SecretKey, iv: ByteArray, plaintext: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            doFinal(plaintext)
        }

    private fun decrypt(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            doFinal(ciphertext)
        }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private const val MAX_CLIENT_NAME_LENGTH = 80
    private const val MIN_TOKEN_LENGTH = 40
    private const val MAX_TOKEN_LENGTH = 64
}
