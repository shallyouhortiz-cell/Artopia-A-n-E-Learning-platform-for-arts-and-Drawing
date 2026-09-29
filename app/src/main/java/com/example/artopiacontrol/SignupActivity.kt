package com.example.artopiacontrol

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.artopiacontrol.data.AdminRepository
import com.example.artopiacontrol.databinding.ActivitySignupBinding
import com.example.artopiacontrol.utils.FormValidator
import androidx.core.widget.doOnTextChanged
import coil.load
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class SignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    
    @Inject
    lateinit var repository: AdminRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivLogo.load(R.drawable.logo) {
            crossfade(true)
            precision(coil.size.Precision.EXACT)
        }

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnSignup.setOnClickListener {
            signup()
        }

        // Password visibility is now handled by TextInputLayout internally

        binding.etName.doOnTextChanged { _, _, _, _ -> binding.tilName.error = null }
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null }
        binding.etConfirmPassword.doOnTextChanged { _, _, _, _ -> binding.tilConfirmPassword.error = null }

        binding.tvAlreadyHaveAccount.setOnClickListener {
            finish() // Go back to Login
        }
    }

    private fun signup() {
        val name = binding.etName.text.toString()
        val email = binding.etEmail.text.toString()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        val validation = FormValidator.validateSignup(name, email, password, confirmPassword)
        binding.tilName.error = validation.nameError
        binding.tilEmail.error = validation.emailError
        binding.tilPassword.error = validation.passwordError
        binding.tilConfirmPassword.error = validation.confirmPasswordError

        if (!validation.isValid) return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSignup.isEnabled = false

        lifecycleScope.launch {
            try {
                val userData = mapOf(
                    "nickname" to name,
                    "email" to email,
                    "password" to password
                )
                // 1. Create Admin account via Secure Cloud Function
                // This sets Custom Claims and triggers the Firestore Email Extension
                repository.registerAdmin(userData)

                Toast.makeText(this@SignupActivity, "Admin account created! Verify your email. Check your Primary inbox!", Toast.LENGTH_LONG).show()

                // 2. Go back to login (user must verify then sign in manually)
                finish()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnSignup.isEnabled = true
                android.util.Log.e("SignupActivity", "Signup failed", e)
                val errorMessage = e.localizedMessage ?: e.message ?: "Unknown error"
                Toast.makeText(this@SignupActivity, "Failed: $errorMessage", Toast.LENGTH_LONG).show()
            }
        }
    }
}
