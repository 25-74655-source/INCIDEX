package com.example.incidex

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminUserAdapter(
    private var items: List<AppUser>,
    private val onMenu: (View, AppUser) -> Unit
) : RecyclerView.Adapter<AdminUserAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: TextView = v.findViewById(R.id.txtAdminUserAvatar)
        val name: TextView = v.findViewById(R.id.txtAdminUserName)
        val email: TextView = v.findViewById(R.id.txtAdminUserEmail)
        val joined: TextView = v.findViewById(R.id.txtAdminUserJoined)
        val menu: View = v.findViewById(R.id.btnAdminUserMenu)
    }

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.admin_item_user, parent, false)
    )

    override fun onBindViewHolder(h: VH, position: Int) {
        val u = items[position]
        val display = u.name.ifBlank { u.email }

        h.avatar.bindAvatar(display, null, R.drawable.user_bg_avatar)
        h.name.text = display
        h.email.text = u.email
        h.joined.text = u.createdAt?.let { "Joined ${dateFormat.format(Date(it))}" } ?: "Joined just now"
        h.menu.setOnClickListener { onMenu(it, u) }
    }

    override fun getItemCount() = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<AppUser>) {
        items = newItems
        notifyDataSetChanged()
    }
}