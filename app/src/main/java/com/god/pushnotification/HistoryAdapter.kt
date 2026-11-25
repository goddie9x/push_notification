package com.god.pushnotification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.god.notifypush.HistoryEntry

class HistoryAdapter(
    private var entries: List<HistoryEntry>,
    private val onClick: (HistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sourceApp: TextView = view.findViewById(R.id.history_source_app)
        val title: TextView = view.findViewById(R.id.history_title)
        val body: TextView = view.findViewById(R.id.history_body)
        val receivedAt: TextView = view.findViewById(R.id.history_received_at)
        val statuses: TextView = view.findViewById(R.id.history_statuses)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.sourceApp.text = entry.payload.sourceApp
        holder.title.text = entry.payload.title
        holder.body.text = entry.payload.body
        holder.receivedAt.text = entry.payload.receivedAt
        holder.statuses.text = entry.results.joinToString("\n") { result ->
            val code = result.httpCode?.toString() ?: ""
            val error = result.error?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            "${result.destinationName}: ${result.status} $code$error".trim()
        }
        holder.itemView.setOnClickListener { onClick(entry) }
    }

    override fun getItemCount(): Int = entries.size

    fun update(entries: List<HistoryEntry>) {
        this.entries = entries
        notifyDataSetChanged()
    }
}
