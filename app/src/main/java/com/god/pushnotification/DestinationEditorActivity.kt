package com.god.pushnotification

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.god.notifypush.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DestinationEditorActivity : AppCompatActivity() {

    private lateinit var nameField: EditText
    private lateinit var urlField: EditText
    private lateinit var headerNameField: EditText
    private lateinit var headerValueField: EditText
    private lateinit var appsFilterField: EditText

    private var destinationId: String? = null
    private var existing: Destination? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destination_editor)
        destinationId = intent.getStringExtra(EXTRA_DESTINATION_ID)
        nameField = findViewById(R.id.destination_name_field)
        urlField = findViewById(R.id.destination_url_field)
        headerNameField = findViewById(R.id.destination_header_name_field)
        headerValueField = findViewById(R.id.destination_header_value_field)
        appsFilterField = findViewById(R.id.destination_apps_field)
        headerValueField.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        findViewById<Button>(R.id.destination_save).setOnClickListener { save() }
        findViewById<Button>(R.id.destination_delete).setOnClickListener { confirmDelete() }
        loadExisting()
    }

    private fun loadExisting() {
        val id = destinationId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val found = ServiceLocator.destinationStore(this@DestinationEditorActivity)
                .load().firstOrNull { it.id == id }
            withContext(Dispatchers.Main) {
                existing = found
                found?.let { fill(it) }
            }
        }
    }

    private fun fill(destination: Destination) {
        nameField.setText(destination.name)
        urlField.setText(destination.url)
        headerNameField.setText(destination.headerName)
        headerValueField.setText(destination.headerValue)
        appsFilterField.setText(DestinationForm.formatAppsFilter(destination.appsFilter))
    }

    private fun save() {
        val url = urlField.text.toString().trim()
        val error = DestinationForm.urlError(url)
        if (error != null) {
            urlField.error = error
            return
        }
        val destination = Destination(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = nameField.text.toString().trim(),
            url = url,
            headerName = headerNameField.text.toString().trim(),
            headerValue = headerValueField.text.toString(),
            enabled = existing?.enabled ?: true,
            appsFilter = DestinationForm.parseAppsFilter(appsFilterField.text.toString())
        )
        lifecycleScope.launch(Dispatchers.IO) {
            val store = ServiceLocator.destinationStore(this@DestinationEditorActivity)
            val current = store.load()
            val updated = if (current.any { it.id == destination.id }) {
                current.map { if (it.id == destination.id) destination else it }
            } else {
                current + destination
            }
            store.save(updated)
            withContext(Dispatchers.Main) { finish() }
        }
    }

    private fun confirmDelete() {
        val current = existing ?: run {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_destination_confirm, current.name))
            .setPositiveButton(R.string.delete) { _, _ -> delete(current) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun delete(destination: Destination) {
        lifecycleScope.launch(Dispatchers.IO) {
            val store = ServiceLocator.destinationStore(this@DestinationEditorActivity)
            store.save(store.load().filter { it.id != destination.id })
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@DestinationEditorActivity,
                    getString(R.string.destination_deleted),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_DESTINATION_ID = "destination_id"
    }
}
