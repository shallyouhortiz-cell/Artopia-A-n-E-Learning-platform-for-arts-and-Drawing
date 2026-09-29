package com.example.artopiacontrol

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.artopiacontrol.adapters.ActivityLogAdapter
import com.example.artopiacontrol.data.AdminLog
import com.example.artopiacontrol.data.AdminRepository
import com.example.artopiacontrol.databinding.FragmentDashboardBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObjects
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    @Inject
    lateinit var repository: AdminRepository

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val logsAdapter = ActivityLogAdapter { log -> showLogDetail(log) }
    private var statsListener: ValueEventListener? = null
    private var logsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            if (!repository.canAccessControlPanel()) {
                repository.logout()
                context?.let { ctx ->
                    startActivity(Intent(ctx, AuthActivity::class.java))
                }
                activity?.finish()
            } else if (repository.mustChangePassword()) {
                context?.let { ctx ->
                    startActivity(Intent(ctx, ChangePasswordActivity::class.java))
                }
                activity?.finish()
            }
        }

        setupUI()
        loadStats()
        loadActivityLog()
    }

    private fun setupUI() {
        val admin = repository.getCurrentUser()
        binding.ivProfile.load(R.drawable.logo)
        binding.ivToolbarProfile.load(R.drawable.logo)
        binding.ivToolbarLogo.load(R.drawable.logo)
        binding.tvEmail.text = admin?.email ?: "Admin"

        binding.rvActivityLog.adapter = logsAdapter

        binding.cardAccounts.setOnClickListener {
            findNavController().navigate(R.id.userListFragment)
        }

        binding.cardProfileOverview.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }

        binding.ivToolbarProfile.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }

        // logout moved to profile fragment

        binding.ivNotifications.setOnClickListener {
            Toast.makeText(context, "You have no new notifications", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadStats() {
        binding.pbStats.visibility = View.VISIBLE
        statsListener?.let { repository.getAllUsers().removeEventListener(it) }

        statsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                binding.pbStats.visibility = View.GONE

                var onlineCount = 0
                for (child in snapshot.children) {
                    val presence = child.child("presence").getValue(String::class.java)
                    if (presence == "online") {
                        onlineCount++
                    }
                }
                binding.tvActiveUsersCount.text = onlineCount.toString()

                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val greeting = when (hour) {
                    in 0..11 -> "Good Morning"
                    in 12..16 -> "Good Afternoon"
                    else -> "Good Evening"
                }
                binding.tvWelcome.text = "$greeting, ${repository.getCurrentUser()?.displayName ?: "User"}"
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return
                binding.pbStats.visibility = View.GONE
            }
        }

        repository.getAllUsers().addValueEventListener(statsListener as ValueEventListener)
    }

    private fun loadActivityLog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isAdmin = repository.isAdmin()
            if (!isAdmin) {
                binding.rvActivityLog.visibility = View.GONE
                return@launch
            }

            logsListener?.remove()
            logsListener = repository.getAdminLogs().limit(10).addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener
                if (error != null) {
                    return@addSnapshotListener
                }
                val logs = snapshot?.toObjects<AdminLog>() ?: emptyList()
                logsAdapter.submitList(logs)
            }
        }
    }

    private fun showLogDetail(log: AdminLog) {
        val detailMsg = StringBuilder()
        detailMsg.append("Action: ${log.action}\n")
        detailMsg.append("Message: ${log.message}\n\n")
        detailMsg.append("Actor: ${log.actorEmail}\n")
        detailMsg.append("Source: ${log.source}\n")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        detailMsg.append("Time: ${log.timestamp?.toDate()?.let { sdf.format(it) } ?: "Syncing..."}")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Activity Detail")
            .setMessage(detailMsg.toString())
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statsListener?.let { repository.getAllUsers().removeEventListener(it) }
        logsListener?.remove()
        _binding = null
    }
}
