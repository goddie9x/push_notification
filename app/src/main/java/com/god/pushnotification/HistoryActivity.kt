package com.god.pushnotification

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.god.notifypush.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        adapter = HistoryAdapter(emptyList()) { entry -> showDetail(entry) }
        findViewById<RecyclerView>(R.id.history_list).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
        }
        findViewById<Button>(R.id.clear_history).setOnClickListener { confirmClear() }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch(Dispatchers.IO) {
            val entries = ServiceLocator.historyStore(this@HistoryActivity).recent()
                .sortedByDescending { it.payload.receivedAt }
            withContext(Dispatchers.Main) { adapter.update(entries) }
        }
    }

    private fun showDetail(entry: HistoryEntry) {
        val results = entry.results.joinToString("\n") { result ->
            val code = result.httpCode?.toString() ?: ""
            val error = result.error?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            "${result.destinationName}: ${result.status} $code$error".trim()
        }
        val detail = buildString {
            append(entry.payload.rawText)
            append("\n\n")
            append(results)
        }
        AlertDialog.Builder(this)
            .setTitle(entry.payload.title.ifEmpty { entry.payload.sourceApp })
            .setMessage(detail)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage(R.string.clear_history_confirm)
            .setPositiveButton(R.string.clear) { _, _ -> clearHistory() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            ServiceLocator.historyStore(this@HistoryActivity).clear()
            withContext(Dispatchers.Main) {
                adapter.update(emptyList())
                Toast.makeText(
                    this@HistoryActivity,
                    getString(R.string.history_cleared),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
