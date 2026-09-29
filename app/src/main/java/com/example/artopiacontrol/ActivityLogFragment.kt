package com.example.artopiacontrol

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.artopiacontrol.adapters.ActivityLogAdapter
import com.example.artopiacontrol.data.AdminLog
import com.example.artopiacontrol.data.AdminRepository
import com.example.artopiacontrol.databinding.FragmentActivityLogBinding
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObjects
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ActivityLogFragment : Fragment() {

    @Inject
    lateinit var repository: AdminRepository

    private var _binding: FragmentActivityLogBinding? = null
    private val binding get() = _binding!!
    private var logsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivityLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setOnRefreshListener {
            loadActivityLog()
        }

        loadActivityLog()
    }

    private fun loadActivityLog() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!repository.isAdmin()) {
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyState.text = "Audit logs are accessible to administrators only."
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.rvActivityLog.visibility = View.GONE
                return@launch
            }

            if (!binding.swipeRefresh.isRefreshing) {
                binding.progressBar.visibility = View.VISIBLE
            }

            logsListener?.remove()
            logsListener = repository.getAdminLogs().addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                if (error != null) {
                    binding.tvEmptyState.text = "Failed to load logs."
                    binding.tvEmptyState.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                val logs = snapshot?.toObjects<AdminLog>() ?: emptyList()

                if (logs.isEmpty()) {
                    binding.tvEmptyState.text = "No activity logs found."
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.rvActivityLog.visibility = View.GONE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                    binding.rvActivityLog.visibility = View.VISIBLE

                    var adapter = binding.rvActivityLog.adapter as? ActivityLogAdapter
                    if (adapter == null) {
                        adapter = ActivityLogAdapter { log -> showLogDetail(log) }
                        binding.rvActivityLog.adapter = adapter
                    }
                    adapter.submitList(logs)
                }
            }
        }
    }

    private fun showLogDetail(log: AdminLog) {
        val detailMsg = StringBuilder()
        detailMsg.append("Action: ${log.action}\n")
        detailMsg.append("Message: ${log.message}\n\n")
        detailMsg.append("Actor: ${log.actorEmail}\n")
        detailMsg.append("Actor UID: ${log.actorUid}\n")
        if (log.targetUid.isNotEmpty()) {
            detailMsg.append("Target UID: ${log.targetUid}\n")
        }
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
        logsListener?.remove()
        _binding = null
    }
}
