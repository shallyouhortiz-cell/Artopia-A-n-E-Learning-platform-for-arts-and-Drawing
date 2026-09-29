package com.example.artopiacontrol.utils

import java.util.regex.Pattern

object FormValidator {

    private val EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"
    )

    fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() && EMAIL_PATTERN.matcher(email).matches()
    }

    data class ValidationResult(
        val isValid: Boolean,
        val emailError: String? = null,
        val passwordError: String? = null,
        val nameError: String? = null,
        val confirmPasswordError: String? = null,
        val currentPasswordError: String? = null
    )

    fun validateLogin(email: String, password: String): ValidationResult {
        var emailErr: String? = null
        var passErr: String? = null

        if (email.trim().isEmpty()) {
            emailErr = "Email is required"
        } else if (!isValidEmail(email.trim())) {
            emailErr = "Invalid email address"
        }

        if (password.isEmpty()) {
            passErr = "Password is required"
        }

        val valid = emailErr == null && passErr == null
        return ValidationResult(isValid = valid, emailError = emailErr, passwordError = passErr)
    }

    fun validateSignup(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): ValidationResult {
        var nameErr: String? = null
        var emailErr: String? = null
        var passErr: String? = null
        var confirmErr: String? = null

        if (name.trim().isEmpty()) {
            nameErr = "Name is required"
        }

        if (email.trim().isEmpty()) {
            emailErr = "Email is required"
        } else if (!isValidEmail(email.trim())) {
            emailErr = "Invalid email address"
        }

        if (password.isEmpty()) {
            passErr = "Password is required"
        } else if (password.length < 8) {
            passErr = "Minimum 8 characters"
        }

        if (confirmPassword.isEmpty()) {
            confirmErr = "Please confirm your password"
        } else if (password != confirmPassword) {
            confirmErr = "Passwords do not match"
        }

        val valid = nameErr == null && emailErr == null && passErr == null && confirmErr == null
        return ValidationResult(
            isValid = valid,
            nameError = nameErr,
            emailError = emailErr,
            passwordError = passErr,
            confirmPasswordError = confirmErr
        )
    }

    fun validateCreateAccount(email: String, password: String): ValidationResult {
        var emailErr: String? = null
        var passErr: String? = null

        if (email.trim().isEmpty() || !isValidEmail(email.trim())) {
            emailErr = "Please enter a valid email address."
        }

        if (password.isNotEmpty() && password.length < 8) {
            passErr = "Minimum 8 characters"
        }

        val valid = emailErr == null && passErr == null
        return ValidationResult(isValid = valid, emailError = emailErr, passwordError = passErr)
    }

    fun validateChangePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): ValidationResult {
        var currentErr: String? = null
        var newErr: String? = null
        var confirmErr: String? = null

        if (currentPassword.isEmpty()) {
            currentErr = "Current password is required"
        }

        if (newPassword.isEmpty()) {
            newErr = "New password is required"
        } else if (newPassword.length < 8) {
            newErr = "Password must be at least 8 characters long"
        } else if (currentPassword.isNotEmpty() && newPassword == currentPassword) {
            newErr = "New password must be different from current password"
        }

        if (confirmPassword.isEmpty()) {
            confirmErr = "Please confirm your new password"
        } else if (newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
            confirmErr = "New passwords do not match"
        }

        val valid = currentErr == null && newErr == null && confirmErr == null
        return ValidationResult(
            isValid = valid,
            currentPasswordError = currentErr,
            passwordError = newErr,
            confirmPasswordError = confirmErr
        )
    }
}
