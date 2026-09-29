package com.example.artopiacontrol

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.artopiacontrol.data.AdminRepository
import com.example.artopiacontrol.databinding.FragmentCreateAccountBinding
import com.example.artopiacontrol.utils.FormValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreateAccountFragment : Fragment() {

    @Inject
    lateinit var repository: AdminRepository

    private var _binding: FragmentCreateAccountBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnCreateAccount.setOnClickListener {
            createAccount()
        }

        binding.tvGeneratePassword.setOnClickListener {
            val generatedOtp = generateRandomPassword()
            binding.etPassword.setText(generatedOtp)
            Toast.makeText(requireContext(), "One-time password generated!", Toast.LENGTH_SHORT).show()
        }

        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null }
    }

    private fun createAccount() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val gradeLevel = binding.etGradeLevel.text.toString().trim()
        val section = binding.etSection.text.toString().trim()
        val role = if (binding.tgRole.checkedButtonId == R.id.btnRoleTeacher) "teacher" else "student"
        val nickname = email.split("@").firstOrNull()?.ifEmpty { "User" } ?: "User"

        val validation = FormValidator.validateCreateAccount(email, password)
        binding.tilEmail.error = validation.emailError
        binding.tilPassword.error = validation.passwordError

        if (!validation.isValid) return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnCreateAccount.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!repository.isAdmin()) {
                    throw Exception("Unauthorized: Admin privilege required to create accounts.")
                }

                repository.createUser(
                    email = email,
                    password = password,
                    nickname = nickname,
                    role = role,
                    gradeLevel = gradeLevel,
                    section = section
                )

                if (_binding != null) {
                    Toast.makeText(requireContext(), "Account created! A setup link has been emailed to $email.", Toast.LENGTH_LONG).show()
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                if (_binding != null) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCreateAccount.isEnabled = true
                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun generateRandomPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$"
        return (1..12).map { chars.random() }.joinToString("")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
