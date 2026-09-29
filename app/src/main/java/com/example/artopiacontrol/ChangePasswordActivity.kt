package com.example.artopiacontrol

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.artopiacontrol.data.AdminRepository
import com.example.artopiacontrol.databinding.ActivityChangePasswordBinding
import com.example.artopiacontrol.utils.FormValidator
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class ChangePasswordActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: AdminRepository

    private lateinit var binding: ActivityChangePasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@ChangePasswordActivity, "Password update is required before proceeding.", Toast.LENGTH_SHORT).show()
            }
        })

        binding.ivLogo.load(R.drawable.logo) {
            precision(coil.size.Precision.EXACT)
        }

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnChangePassword.setOnClickListener {
            updatePassword()
        }

        binding.etCurrentPassword.doOnTextChanged { _, _, _, _ -> binding.tilCurrentPassword.error = null }
        binding.etNewPassword.doOnTextChanged { _, _, _, _ -> binding.tilNewPassword.error = null }
        binding.etConfirmPassword.doOnTextChanged { _, _, _, _ -> binding.tilConfirmPassword.error = null }
    }

    private fun updatePassword() {
        val currentPassword = binding.etCurrentPassword.text.toString()
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        val validation = FormValidator.validateChangePassword(currentPassword, newPassword, confirmPassword)
        binding.tilCurrentPassword.error = validation.currentPasswordError
        binding.tilNewPassword.error = validation.passwordError
        binding.tilConfirmPassword.error = validation.confirmPasswordError

        if (!validation.isValid) return

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.email == null) {
            Toast.makeText(this, "Session expired. Please sign in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnChangePassword.isEnabled = false

        lifecycleScope.launch {
            try {
                val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                user.reauthenticate(credential).await()

                user.updatePassword(newPassword).await()

                repository.completePasswordChange()

                Toast.makeText(this@ChangePasswordActivity, "Password updated successfully!", Toast.LENGTH_SHORT).show()

                startActivity(Intent(this@ChangePasswordActivity, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnChangePassword.isEnabled = true
                Toast.makeText(this@ChangePasswordActivity, "Failed to update password: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
