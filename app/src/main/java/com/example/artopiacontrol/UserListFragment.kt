package com.example.artopiacontrol

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.artopiacontrol.data.AdminRepository
import com.example.artopiacontrol.data.User
import com.example.artopiacontrol.databinding.FragmentUserListBinding
import com.example.artopiacontrol.ui.UserAdapter
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class UserListFragment : Fragment() {

    @Inject
    lateinit var repository: AdminRepository

    private var _binding: FragmentUserListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UserAdapter
    private var allUsers = listOf<User>()
    private var currentSearchQuery = ""
    private var currentStatusFilter = "all"
    private var usersListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = UserAdapter { user ->
            val bundle = Bundle().apply {
                putString("userId", user.uid)
            }
            findNavController().navigate(R.id.userDetailFragment, bundle)
        }

        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = adapter

        binding.ivToolbarProfile.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }

        binding.fabAddUser.setOnClickListener {
            findNavController().navigate(R.id.createAccountFragment)
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadUsers()
        }

        loadUsers()

        binding.tabsContainer.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentStatusFilter = when(tab?.position) {
                    1 -> "active"
                    2 -> "offline"
                    else -> "all"
                }
                applyFilters()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadUsers() {
        if (!binding.swipeRefresh.isRefreshing) {
            binding.progressBar.visibility = View.VISIBLE
        }

        usersListener?.let { repository.getAllUsers().removeEventListener(it) }

        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                val usersList = mutableListOf<User>()
                for (child in snapshot.children) {
                    val user = child.getValue(User::class.java)?.copy(uid = child.key ?: "")
                    user?.let { usersList.add(it) }
                }

                allUsers = usersList.sortedBy { it.nickname.lowercase() }

                _binding?.let {
                    if (allUsers.isEmpty()) {
                        it.tvEmptyState.text = "No users found"
                        it.llEmptyState.visibility = View.VISIBLE
                        it.rvUsers.visibility = View.GONE
                    } else {
                        it.llEmptyState.visibility = View.GONE
                        it.rvUsers.visibility = View.VISIBLE
                    }

                    it.ivToolbarProfile.load(R.drawable.logo)

                    val totalCount = allUsers.size
                    val activeCount = allUsers.count { u -> u.presence == "online" }
                    val offlineCount = totalCount - activeCount

                    it.tabsContainer.getTabAt(0)?.text = getString(R.string.tab_all_format, totalCount)
                    it.tabsContainer.getTabAt(1)?.text = getString(R.string.tab_active_format, activeCount)
                    it.tabsContainer.getTabAt(2)?.text = getString(R.string.tab_offline_format, offlineCount)

                    applyFilters()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyState.text = "Failed to load users"
                binding.llEmptyState.visibility = View.VISIBLE
            }
        }

        repository.getAllUsers().addValueEventListener(usersListener as ValueEventListener)
    }

    private fun applyFilters() {
        var filtered = allUsers

        if (currentStatusFilter != "all") {
            filtered = filtered.filter {
                if (currentStatusFilter == "active") it.presence == "online"
                else it.presence != "online"
            }
        }

        if (currentSearchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                val displayName = it.nickname.ifEmpty { it.email.split("@").firstOrNull() ?: "" }
                displayName.contains(currentSearchQuery, ignoreCase = true) ||
                it.email.contains(currentSearchQuery, ignoreCase = true) ||
                it.role.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        // Categorize & sort by Role precedence (Admin -> Teacher -> Student) and then A-Z by Name/Email
        val roleOrder = mapOf("admin" to 1, "teacher" to 2, "student" to 3)
        filtered = filtered.sortedWith(
            compareBy<User> { roleOrder[it.role.lowercase()] ?: 4 }
                .thenBy { (it.nickname.ifEmpty { it.email }).lowercase() }
        )

        adapter.submitList(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        usersListener?.let { repository.getAllUsers().removeEventListener(it) }
        _binding = null
    }
}
