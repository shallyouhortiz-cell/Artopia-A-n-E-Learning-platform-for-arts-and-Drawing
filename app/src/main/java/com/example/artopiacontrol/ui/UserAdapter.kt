package com.example.artopiacontrol.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.artopiacontrol.R
import com.example.artopiacontrol.data.User
import com.example.artopiacontrol.databinding.ItemUserBinding

class UserAdapter(private val onClick: (User) -> Unit) : ListAdapter<User, UserAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        
        // Derive clean display name if nickname is empty
        val displayName = when {
            user.nickname.isNotEmpty() -> user.nickname
            user.email.isNotEmpty() -> user.email.split("@").firstOrNull() ?: "User"
            user.uid.isNotEmpty() -> "User ${user.uid.take(6)}"
            else -> "User"
        }
        holder.binding.tvName.text = displayName
        
        // Show clean email
        if (user.email.isNotEmpty()) {
            holder.binding.tvEmail.text = user.email
        } else {
            holder.binding.tvEmail.text = "No email registered"
        }

        // Display Grade Level & Section (e.g. Grade 7 - Sampaguita)
        if (user.section.isNotEmpty() || user.gradeLevel.isNotEmpty()) {
            val sectionLabel = listOf(user.gradeLevel, user.section)
                .filter { it.isNotEmpty() }
                .joinToString(" - ")
            holder.binding.tvSection.text = sectionLabel
            holder.binding.tvSection.visibility = android.view.View.VISIBLE
        } else {
            holder.binding.tvSection.visibility = android.view.View.GONE
        }
        
        holder.binding.tvUid.text = "UID: ${user.uid}"

        // Role Category Badge
        val context = holder.binding.root.context
        val roleTitle = user.role.replaceFirstChar { it.uppercase() }
        holder.binding.tvRoleBadge.text = roleTitle
        when (user.role.lowercase()) {
            "admin" -> {
                holder.binding.tvRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.primary))
                holder.binding.tvRoleBadge.setBackgroundResource(R.drawable.bg_status_active)
            }
            "teacher" -> {
                holder.binding.tvRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.blue_secondary))
                holder.binding.tvRoleBadge.setBackgroundResource(R.drawable.bg_status_active)
            }
            else -> {
                holder.binding.tvRoleBadge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.binding.tvRoleBadge.setBackgroundResource(R.drawable.bg_rounded_input)
            }
        }

        holder.binding.ivUserIcon.load(user.profileImageUrl ?: R.drawable.ic_person_24) {
            crossfade(true)
            placeholder(R.drawable.ic_person_24)
            error(R.drawable.ic_person_24)
            precision(coil.size.Precision.EXACT)
        }
        
        // Change status badge style based on presence
        if (user.presence == "online") {
            holder.binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.success_text))
            holder.binding.tvStatus.text = "Active"
        } else {
            holder.binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.text_hint))
            holder.binding.tvStatus.text = "Offline"
        }
        
        holder.itemView.setOnClickListener { onClick(user) }
    }

    object DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}
