package com.god.pushnotification

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.god.notifypush.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServicesActivity : AppCompatActivity() {

    private lateinit var adapter: DestinationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_services)
        adapter = DestinationAdapter(
            destinations = emptyList(),
            onToggle = { destination, enabled -> setEnabled(destination, enabled) },
            onEdit = { destination -> openEditor(destination.id) },
            onSendTest = { destination -> sendTest(destination) }
        )
        findViewById<RecyclerView>(R.id.destination_list).apply {
            layoutManager = LinearLayoutManager(this@ServicesActivity)
            adapter = this@ServicesActivity.adapter
        }
        findViewById<Button>(R.id.add_destination).setOnClickListener { openEditor(null) }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch(Dispatchers.IO) {
            val destinations = ServiceLocator.destinationStore(this@ServicesActivity).load()
            withContext(Dispatchers.Main) { adapter.update(destinations) }
        }
    }

    private fun setEnabled(destination: Destination, enabled: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val store = ServiceLocator.destinationStore(this@ServicesActivity)
            val updated = store.load().map {
                if (it.id == destination.id) it.copy(enabled = enabled) else it
            }
            store.save(updated)
        }
    }

    private fun sendTest(destination: Destination) {
        lifecycleScope.launch {
            val result = ServiceLocator.testSender().sendTest(destination)
            val message = getString(
                R.string.test_result,
                result.destinationName,
                result.status,
                result.httpCode?.toString().orEmpty(),
                result.error.orEmpty()
            )
            Toast.makeText(this@ServicesActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun openEditor(destinationId: String?) {
        val intent = Intent(this, DestinationEditorActivity::class.java)
        intent.putExtra(DestinationEditorActivity.EXTRA_DESTINATION_ID, destinationId)
        startActivity(intent)
    }
}
