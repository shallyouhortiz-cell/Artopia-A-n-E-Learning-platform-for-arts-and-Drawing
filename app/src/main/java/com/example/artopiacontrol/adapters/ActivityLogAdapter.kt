package com.example.artopiacontrol.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.artopiacontrol.data.AdminLog
import com.example.artopiacontrol.databinding.ItemActivityLogBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityLogAdapter(private val onLogClick: (AdminLog) -> Unit) : ListAdapter<AdminLog, ActivityLogAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm", Locale.getDefault())

    class ViewHolder(val binding: ItemActivityLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActivityLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = getItem(position)
        holder.binding.apply {
            // Professional Action Mapping
            tvLogAction.text = formatAction(log.action)
            
            // Clean Description logic
            val description = log.message.ifEmpty { 
                if (log.actorEmail != null) "Performed by ${log.actorEmail}" else "System automatic task"
            }
            
            // Source & Actor Info
            val sourceLabel = if (log.source == "cloud-functions") "[Cloud] " else "[Device] "
            tvLogDescription.text = "$sourceLabel$description"
            
            tvLogDate.text = log.timestamp?.toDate()?.let { dateFormat.format(it) } ?: "Pending sync..."

            // Dynamic Styling based on Action
            styleIcon(this, log.action)
            
            root.setOnClickListener { onLogClick(log) }
        }
    }

    private fun formatAction(action: String): String {
        return when (action) {
            "USER_REGISTERED" -> "Account Registration"
            "VERIFICATION_EMAIL_SENT" -> "Verification Link Sent"
            "EMAIL_VERIFIED" -> "Account Verified"
            "USER_LOGIN" -> "Secure Login"
            "USER_LOGOUT" -> "User Logout"
            "PROFILE_UPDATED" -> "Profile Updated"
            "ACCOUNT_DELETED" -> "Account Permanently Deleted"
            "ADMIN_ACTION" -> "Administrative Action"
            "LOGIN_ATTEMPT_DENIED" -> "Unauthorized Access Blocked"
            "UPDATE_ROLE" -> "Role Privilege Changed"
            else -> action.replace("_", " ").lowercase()
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    private fun styleIcon(binding: ItemActivityLogBinding, action: String) {
        val context = binding.root.context
        val iconRes = when {
            action.contains("REGISTER") -> com.example.artopiacontrol.R.drawable.user
            action.contains("LOGIN") -> com.example.artopiacontrol.R.drawable.lock
            action.contains("LOGOUT") -> com.example.artopiacontrol.R.drawable.arrow_right
            action.contains("DELETE") -> com.example.artopiacontrol.R.drawable.ic_back 
            action.contains("VERIF") -> com.example.artopiacontrol.R.drawable.envelope
            action.contains("PROFILE") || action.contains("UPDATE") -> com.example.artopiacontrol.R.drawable.settings
            action.contains("DENIED") -> com.example.artopiacontrol.R.drawable.lock
            else -> com.example.artopiacontrol.R.drawable.ic_check_circle
        }
        
        val colorRes = when {
            action.contains("DELETE") || action.contains("DENIED") || action.contains("BANNED") -> android.graphics.Color.RED
            action.contains("REGISTER") || action.contains("VERIF") -> context.getColor(com.example.artopiacontrol.R.color.primary)
            action.contains("LOGIN") || action.contains("SUCCESS") -> context.getColor(com.example.artopiacontrol.R.color.success_text)
            else -> context.getColor(com.example.artopiacontrol.R.color.text_secondary)
        }

        binding.ivCheck.setImageResource(iconRes)
        binding.ivCheck.imageTintList = android.content.res.ColorStateList.valueOf(colorRes)
    }

    object DiffCallback : DiffUtil.ItemCallback<AdminLog>() {
        override fun areItemsTheSame(oldItem: AdminLog, newItem: AdminLog) = 
            oldItem.logId == newItem.logId
        
        override fun areContentsTheSame(oldItem: AdminLog, newItem: AdminLog) = oldItem == newItem
    }
}
