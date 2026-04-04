package com.colornote.sync

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.colornote.sync.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var etServerUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnTestConnection: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        etServerUrl = findViewById(R.id.etServerUrl)
        btnSave = findViewById(R.id.btnSave)
        btnTestConnection = findViewById(R.id.btnTestConnection)

        // Load current URL
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val currentUrl = prefs.getString("server_url", "http://10.0.2.2:5001") ?: "http://10.0.2.2:5001"
        etServerUrl.setText(currentUrl)

        btnSave.setOnClickListener {
            val url = etServerUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("server_url", url).apply()
            Toast.makeText(this, "Server URL saved", Toast.LENGTH_SHORT).show()
        }

        btnTestConnection.setOnClickListener {
            testConnection()
        }
    }

    private fun testConnection() {
        val url = etServerUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        btnTestConnection.isEnabled = false
        btnTestConnection.text = "Testing..."

        val baseUrl = if (url.endsWith("/")) url else "$url/"
        val service = RetrofitClient.rebuild(baseUrl)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    service.getNotes()
                }
                if (response.isSuccessful) {
                    Toast.makeText(this@SettingsActivity, "Connection successful!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Server returned: ${response.code()} ${response.message()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Connection failed: ${e.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                btnTestConnection.isEnabled = true
                btnTestConnection.text = "Test Connection"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
