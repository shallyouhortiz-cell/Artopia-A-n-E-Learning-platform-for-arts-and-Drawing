package com.example.artopiacontrol

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.artopiacontrol.data.AdminRepository
import com.example.artopiacontrol.data.User
import com.example.artopiacontrol.databinding.FragmentUserDetailBinding
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class UserDetailFragment : Fragment() {

    @Inject
    lateinit var repository: AdminRepository

    private var _binding: FragmentUserDetailBinding? = null
    private val binding get() = _binding!!
    private var userId: String? = null
    private var userListener: ListenerRegistration? = null
    private var metaListener: ListenerRegistration? = null

    private var currentUserData = User()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = arguments?.getString("userId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivProfile.load(R.drawable.logo) {
            precision(coil.size.Precision.EXACT)
        }

        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvPassword.visibility = View.GONE

        userId?.let { loadUserDetails(it) }
    }

    private fun loadUserDetails(uid: String) {
        userListener?.remove()
        metaListener?.remove()

        userListener = repository.getUser(uid).addSnapshotListener { snapshot, error ->
            if (_binding == null || error != null) return@addSnapshotListener
            val user = snapshot?.toObject<User>()
            user?.let {
                currentUserData = currentUserData.copy(
                    uid = it.uid,
                    email = it.email,
                    nickname = it.nickname,
                    age = it.age,
                    profileImageUrl = it.profileImageUrl
                )
                populateUI(currentUserData)
            }
        }

        metaListener = repository.getUserMeta(uid).addSnapshotListener { snapshot, error ->
            if (_binding == null || error != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists()) {
                val role = snapshot.getString("role") ?: "student"
                val status = snapshot.getString("status") ?: "active"
                val presence = snapshot.getString("presence") ?: "offline"
                val isVerified = snapshot.getBoolean("isVerified") ?: false
                val createdAt = snapshot.getTimestamp("createdAt")
                val lastLogin = snapshot.getTimestamp("lastLogin")

                currentUserData = currentUserData.copy(
                    role = role,
                    status = status,
                    presence = presence,
                    isVerified = isVerified,
                    createdAt = createdAt,
                    lastLogin = lastLogin
                )
                populateUI(currentUserData)
            }
        }
    }

    private fun populateUI(user: User) {
        _binding?.let { b ->
            b.ivProfile.load(user.profileImageUrl ?: R.drawable.logo)
            b.tvNickname.text = user.nickname
            b.tvEmail.text = user.email
            b.tvAge.text = user.age.toString()
            b.tvStatus.text = "${user.status.replaceFirstChar { it.uppercase() }} (${user.presence})"
            b.tvRole.text = user.role.replaceFirstChar { it.uppercase() }

            val sectionLabel = listOf(user.gradeLevel, user.section)
                .filter { it.isNotEmpty() }
                .joinToString(" - ")
            b.tvSectionDetail.text = sectionLabel.ifEmpty { "Not assigned" }

            b.tvUidDetail.text = user.uid

            b.tvVerifiedStatus.text = if (user.isVerified) "Verified" else "Not Verified"

            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            b.tvCreatedAt.text = user.createdAt?.toDate()?.let { sdf.format(it) } ?: "Not available"
            b.tvLastLoginDetail.text = user.lastLogin?.toDate()?.let { sdf.format(it) } ?: "Never"

            viewLifecycleOwner.lifecycleScope.launch {
                val currentUid = repository.getCurrentUser()?.uid
                val isAdmin = repository.isAdmin()
                val isSelf = currentUid == user.uid

                b.btnDeleteAccount.visibility = if (isAdmin && !isSelf) View.VISIBLE else View.GONE
                b.btnToggleStatus.isEnabled = !isSelf
                b.btnEdit.isEnabled = isAdmin || isSelf || user.role == "student"

                b.btnToggleStatus.text = if (user.status == "banned" || user.status == "suspended") "Activate User" else "Suspend User"
                b.btnToggleStatus.setOnClickListener {
                    val targetStatus = if (user.status == "active") "suspended" else "active"
                    showConfirmationDialog(
                        "Change Status",
                        "Are you sure you want to set ${user.nickname}'s status to $targetStatus?",
                        onConfirm = {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    b.progressBar.visibility = View.VISIBLE
                                    b.btnToggleStatus.isEnabled = false
                                    repository.toggleUserStatus(user.uid, targetStatus)
                                    Toast.makeText(requireContext(), "Status updated to $targetStatus", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                } finally {
                                    b.progressBar.visibility = View.GONE
                                    b.btnToggleStatus.isEnabled = true
                                }
                            }
                        }
                    )
                }

                b.btnEdit.setOnClickListener {
                    showEditDialog(user)
                }

                b.btnDeleteAccount.setOnClickListener {
                    showConfirmationDialog(
                        "Delete Account",
                        "Are you sure you want to PERMANENTLY delete ${user.nickname}'s account?",
                        onConfirm = {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    b.progressBar.visibility = View.VISIBLE
                                    b.btnDeleteAccount.isEnabled = false
                                    repository.deleteUser(user.uid)
                                    Toast.makeText(requireContext(), "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                                    findNavController().navigateUp()
                                } catch (e: Exception) {
                                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                } finally {
                                    b.progressBar.visibility = View.GONE
                                    b.btnDeleteAccount.isEnabled = true
                                }
                            }
                        }
                    )
                }

                b.btnResetPassword.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            b.progressBar.visibility = View.VISIBLE
                            b.btnResetPassword.isEnabled = false
                            repository.resetPasswordClientSide(user.email)
                            Toast.makeText(requireContext(), "Reset email sent to ${user.email}.", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        } finally {
                            b.progressBar.visibility = View.GONE
                            b.btnResetPassword.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun showEditDialog(user: User) {
        val context = context ?: return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 20)
        }

        val etNickname = EditText(context).apply {
            hint = "Nickname"
            setText(user.nickname)
        }
        val etAge = EditText(context).apply {
            hint = "Age"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(user.age.toString())
        }
        val etGradeLevel = EditText(context).apply {
            hint = "Grade Level (e.g. Grade 7)"
            setText(user.gradeLevel)
        }
        val etSection = EditText(context).apply {
            hint = "Section (e.g. Sampaguita)"
            setText(user.section)
        }

        layout.addView(etNickname)
        layout.addView(etAge)
        layout.addView(etGradeLevel)
        layout.addView(etSection)

        AlertDialog.Builder(context)
            .setTitle("Edit User Profile")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newNickname = etNickname.text.toString().trim()
                val newAge = etAge.text.toString().toIntOrNull() ?: user.age
                val newGrade = etGradeLevel.text.toString().trim()
                val newSection = etSection.text.toString().trim()

                lifecycleScope.launch {
                    try {
                        repository.updateUserProfile(
                            user.uid,
                            nickname = newNickname,
                            age = newAge,
                            gradeLevel = newGrade,
                            section = newSection
                        )
                        Toast.makeText(requireContext(), "Profile updated.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        metaListener?.remove()
        _binding = null
    }
}
