package com.example.scannota

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Account(val phone: String, val name: String)

/**
 * Login versi demo: akun tersimpan di HP ini saja, tanpa server.
 * PIN tidak disimpan apa adanya, hanya hasil hash-nya.
 */
class Accounts(context: Context) {
    private val prefs = context.getSharedPreferences("akun", Context.MODE_PRIVATE)

    fun current(): Account? = prefs.getString(SESSION, null)?.let { find(it) }

    fun hasAny() = prefs.all.keys.any { it.startsWith(PREFIX) }

    fun lastPhone(): String = prefs.getString(LAST, "") ?: ""

    /** Mengembalikan false kalau nomor sudah terdaftar. */
    fun register(name: String, phone: String, pin: String): Boolean {
        if (find(phone) != null) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val data = JSONObject()
            .put("name", name)
            .put("salt", encode(salt))
            .put("hash", hash(pin, salt))
        prefs.edit().putString(PREFIX + phone, data.toString()).apply()
        startSession(phone)
        return true
    }

    fun login(phone: String, pin: String): Boolean {
        val raw = prefs.getString(PREFIX + phone, null) ?: return false
        val data = JSONObject(raw)
        val salt = Base64.decode(data.getString("salt"), Base64.NO_WRAP)
        if (hash(pin, salt) != data.getString("hash")) return false
        startSession(phone)
        return true
    }

    fun logout() = prefs.edit().remove(SESSION).apply()

    private fun startSession(phone: String) =
        prefs.edit().putString(SESSION, phone).putString(LAST, phone).apply()

    private fun find(phone: String): Account? = prefs.getString(PREFIX + phone, null)
        ?.let { Account(phone, JSONObject(it).getString("name")) }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 20_000, 256)
        return encode(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded)
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val PREFIX = "akun_"
        const val SESSION = "sesi"
        const val LAST = "terakhir"
    }
}
