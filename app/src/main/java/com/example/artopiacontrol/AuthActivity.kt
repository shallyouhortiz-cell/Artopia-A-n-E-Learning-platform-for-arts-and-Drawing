package com.example.artopiacontrol

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.doOnTextChanged
import coil.load
import com.example.artopiacontrol.data.AdminRepository
import com.example.artopiacontrol.databinding.ActivityLoginBinding
import com.example.artopiacontrol.utils.FormValidator
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: AdminRepository

    private lateinit var binding: ActivityLoginBinding
    private var lastResendTimestamp: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivLogo.load(R.drawable.logo) {
            crossfade(true)
            precision(coil.size.Precision.EXACT)
        }

        if (FirebaseAuth.getInstance().currentUser != null) {
            checkAdminAndProceed()
        }

        binding.btnLogin.setOnClickListener {
            login()
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
                    Toast.makeText(this@AuthActivity, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@AuthActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null }

        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun login() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        val validation = FormValidator.validateLogin(email, password)
        binding.tilEmail.error = validation.emailError
        binding.tilPassword.error = validation.passwordError

        if (!validation.isValid) return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    checkAdminAndProceed()
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    binding.tilPassword.error = "Invalid email or password"
                    Toast.makeText(this, "Login Failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkAdminAndProceed() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            binding.progressBar.visibility = View.GONE
            binding.btnLogin.isEnabled = true
            return
        }

        lifecycleScope.launch {
            try {
                // Reload user profile from Firebase Auth server to refresh emailVerified state
                try {
                    user.reload().await()
                } catch (e: Exception) {
                    android.util.Log.w("AuthActivity", "Failed to reload user state", e)
                }

                if (!user.isEmailVerified) {
                    val now = System.currentTimeMillis()
                    if (now - lastResendTimestamp > 60000) {
                        try {
                            user.sendEmailVerification().await()
                            lastResendTimestamp = now
                            repository.logActivity("VERIFICATION_EMAIL_SENT", "Verification link sent to ${user.email}", user.uid)
                            Toast.makeText(this@AuthActivity, "Email not verified. A verification link has been sent to ${user.email}.", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@AuthActivity, "Please verify your email before signing in.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@AuthActivity, "Email not verified. Please check your inbox.", Toast.LENGTH_LONG).show()
                    }
                    repository.logout()
                    return@launch
                }

                if (repository.canAccessControlPanel() && repository.isAccountActive()) {
                    repository.syncLoginState()
                    repository.logActivity("USER_LOGIN", "User logged in: ${user.email}", user.uid)

                    if (repository.mustChangePassword()) {
                        startActivity(Intent(this@AuthActivity, ChangePasswordActivity::class.java))
                    } else {
                        startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                    }
                    finish()
                } else {
                    repository.logActivity("LOGIN_ATTEMPT_DENIED", "Insufficient permissions for ${user.email}", user.uid)
                    repository.logout()
                    Toast.makeText(this@AuthActivity, "Access Denied: Admin or Teacher role required.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                repository.logout()
                Toast.makeText(this@AuthActivity, "Authentication failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true
            }
        }
    }
}
