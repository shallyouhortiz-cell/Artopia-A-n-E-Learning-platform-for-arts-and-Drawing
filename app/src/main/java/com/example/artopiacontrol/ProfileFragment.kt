package com.example.artopiacontrol

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.artopiacontrol.data.AdminRepository
import com.example.artopiacontrol.data.User
import com.example.artopiacontrol.databinding.FragmentProfileBinding
import com.google.firebase.firestore.toObject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    @Inject
    lateinit var repository: AdminRepository

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var profileListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogout.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.logout()
                startActivity(Intent(requireContext(), AuthActivity::class.java))
                requireActivity().finish()
            }
        }

        binding.btnChangePassword.setOnClickListener {
            startActivity(Intent(requireContext(), ChangePasswordActivity::class.java))
        }

        loadProfile()
    }

    private fun loadProfile() {
        val currentUser = repository.getCurrentUser()
        if (currentUser == null) {
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
            return
        }

        profileListener?.remove()
        profileListener = repository.getUser(currentUser.uid).addSnapshotListener { snapshot, error ->
            if (_binding == null) return@addSnapshotListener
            if (error != null) {
                binding.tvEmail.text = currentUser.email
                binding.tvNickname.text = currentUser.displayName?.takeIf { it.isNotEmpty() } ?: currentUser.email?.split("@")?.get(0) ?: "Admin"
                binding.ivProfile.load(R.drawable.logo) {
                    precision(coil.size.Precision.EXACT)
                }
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val user = snapshot.toObject<User>()
                user?.let { populateUI(it) }
            } else {
                binding.tvEmail.text = currentUser.email
                binding.tvNickname.text = currentUser.displayName?.takeIf { it.isNotEmpty() } ?: currentUser.email?.split("@")?.get(0) ?: "Admin"
                binding.tvRole.text = "Admin"
                binding.tvStatus.text = "Active"
                binding.ivProfile.load(R.drawable.logo) {
                    precision(coil.size.Precision.EXACT)
                }
            }
        }
    }

    private fun populateUI(user: User) {
        if (_binding == null) return
        binding.tvNickname.text = user.nickname
        binding.tvEmail.text = user.email
        binding.tvRole.text = user.role.replaceFirstChar { it.uppercase() }
        binding.tvStatus.text = user.status.replaceFirstChar { it.uppercase() }
        binding.ivProfile.load(user.profileImageUrl ?: R.drawable.logo) {
            crossfade(true)
            precision(coil.size.Precision.EXACT)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        profileListener?.remove()
        _binding = null
    }
}
