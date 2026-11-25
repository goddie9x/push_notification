package com.god.pushnotification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.god.notifypush.Destination

class DestinationAdapter(
    private var destinations: List<Destination>,
    private val onToggle: (Destination, Boolean) -> Unit,
    private val onEdit: (Destination) -> Unit,
    private val onSendTest: (Destination) -> Unit
) : RecyclerView.Adapter<DestinationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.destination_name)
        val url: TextView = view.findViewById(R.id.destination_url)
        val apps: TextView = view.findViewById(R.id.destination_apps)
        val enabled: Switch = view.findViewById(R.id.destination_enabled)
        val sendTest: Button = view.findViewById(R.id.destination_send_test)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_destination, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val destination = destinations[position]
        holder.name.text = destination.name
        holder.url.text = destination.url
        holder.apps.text = holder.itemView.context.getString(
            R.string.destination_apps,
            DestinationForm.formatAppsFilter(destination.appsFilter).ifEmpty {
                holder.itemView.context.getString(R.string.destination_apps_all)
            }
        )
        holder.enabled.setOnCheckedChangeListener(null)
        holder.enabled.isChecked = destination.enabled
        holder.enabled.setOnCheckedChangeListener { _, checked ->
            onToggle(destination, checked)
        }
        holder.itemView.setOnClickListener { onEdit(destination) }
        holder.sendTest.setOnClickListener { onSendTest(destination) }
    }

    override fun getItemCount(): Int = destinations.size

    fun update(destinations: List<Destination>) {
        this.destinations = destinations
        notifyDataSetChanged()
    }
}
