package com.example.scannota

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private val accounts by lazy { Accounts(this) }
    private var registering = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (accounts.current() != null) return openMain()
        setContentView(R.layout.activity_login)

        registering = savedInstanceState?.getBoolean("daftar") ?: !accounts.hasAny()
        if (savedInstanceState == null) input(R.id.inputPhone).setText(accounts.lastPhone())

        findViewById<View>(R.id.btnSubmit).setOnClickListener { submit() }
        findViewById<View>(R.id.btnSwitch).setOnClickListener {
            registering = !registering
            render()
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("daftar", registering)
    }

    private fun render() {
        text(R.id.txtHeading, if (registering) "Buat akun" else "Masuk")
        text(R.id.btnSubmit, if (registering) "Daftar" else "Masuk")
        text(R.id.btnSwitch, if (registering) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar")
        val extra = if (registering) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutName).visibility = extra
        findViewById<View>(R.id.layoutConfirm).visibility = extra
        showError("")
    }

    private fun submit() {
        val name = input(R.id.inputName).text.toString().trim()
        val phone = input(R.id.inputPhone).text.toString().filter { it.isDigit() }
        val pin = input(R.id.inputPin).text.toString()

        val error = when {
            registering && name.isEmpty() -> "Isi nama dulu"
            phone.length !in 9..15 -> "Nomor HP tidak valid"
            pin.length != 6 -> "PIN harus 6 angka"
            registering && pin != input(R.id.inputConfirm).text.toString() -> "Ulangi PIN belum sama"
            registering && !accounts.register(name, phone, pin) -> "Nomor ini sudah terdaftar"
            !registering && !accounts.login(phone, pin) -> "Nomor HP atau PIN salah"
            else -> null
        }
        if (error == null) openMain() else showError(error)
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        text(R.id.txtError, message)
        findViewById<View>(R.id.txtError).visibility =
            if (message.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun input(id: Int) = findViewById<EditText>(id)

    private fun text(id: Int, value: String) {
        findViewById<TextView>(id).text = value
    }
}
