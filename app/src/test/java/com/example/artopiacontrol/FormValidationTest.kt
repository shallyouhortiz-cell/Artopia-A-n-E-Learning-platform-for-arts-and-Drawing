package com.example.artopiacontrol

import com.example.artopiacontrol.utils.FormValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormValidationTest {

    // =========================================================================
    // 1. LOGIN FORM VALIDATION - 5 FULL CYCLES
    // =========================================================================

    @Test
    fun loginForm_Cycle1_bothFieldsEmpty_setsBothErrors() {
        val result = FormValidator.validateLogin("", "")
        assertFalse(result.isValid)
        assertEquals("Email is required", result.emailError)
        assertEquals("Password is required", result.passwordError)
    }

    @Test
    fun loginForm_Cycle2_invalidEmail_setsEmailFormatError() {
        val result = FormValidator.validateLogin("invalid-email-format", "password123")
        assertFalse(result.isValid)
        assertEquals("Invalid email address", result.emailError)
        assertNull(result.passwordError)
    }

    @Test
    fun loginForm_Cycle3_missingPassword_setsPasswordError() {
        val result = FormValidator.validateLogin("admin@artopia.com", "")
        assertFalse(result.isValid)
        assertNull(result.emailError)
        assertEquals("Password is required", result.passwordError)
    }

    @Test
    fun loginForm_Cycle4_whitespaceEmail_setsEmailError() {
        val result = FormValidator.validateLogin("   ", "password123")
        assertFalse(result.isValid)
        assertEquals("Email is required", result.emailError)
        assertNull(result.passwordError)
    }

    @Test
    fun loginForm_Cycle5_validCredentials_passesValidation() {
        val result = FormValidator.validateLogin("admin@artopia.com", "SecurePass123!")
        assertTrue(result.isValid)
        assertNull(result.emailError)
        assertNull(result.passwordError)
    }

    // =========================================================================
    // 2. SIGNUP FORM VALIDATION - 5 FULL CYCLES
    // =========================================================================

    @Test
    fun signupForm_Cycle1_allFieldsEmpty_setsAllErrors() {
        val result = FormValidator.validateSignup("", "", "", "")
        assertFalse(result.isValid)
        assertEquals("Name is required", result.nameError)
        assertEquals("Email is required", result.emailError)
        assertEquals("Password is required", result.passwordError)
        assertEquals("Please confirm your password", result.confirmPasswordError)
    }

    @Test
    fun signupForm_Cycle2_invalidEmail_setsEmailError() {
        val result = FormValidator.validateSignup("John Doe", "bad-email", "pass12345", "pass12345")
        assertFalse(result.isValid)
        assertNull(result.nameError)
        assertEquals("Invalid email address", result.emailError)
        assertNull(result.passwordError)
        assertNull(result.confirmPasswordError)
    }

    @Test
    fun signupForm_Cycle3_shortPassword_setsLengthError() {
        val result = FormValidator.validateSignup("John Doe", "john@artopia.com", "12345", "12345")
        assertFalse(result.isValid)
        assertEquals("Minimum 8 characters", result.passwordError)
    }

    @Test
    fun signupForm_Cycle4_mismatchedPasswords_setsMismatchError() {
        val result = FormValidator.validateSignup("John Doe", "john@artopia.com", "Password123", "Password456")
        assertFalse(result.isValid)
        assertEquals("Passwords do not match", result.confirmPasswordError)
    }

    @Test
    fun signupForm_Cycle5_validInput_passesValidation() {
        val result = FormValidator.validateSignup("John Doe", "john@artopia.com", "Password123!", "Password123!")
        assertTrue(result.isValid)
        assertNull(result.nameError)
        assertNull(result.emailError)
        assertNull(result.passwordError)
        assertNull(result.confirmPasswordError)
    }

    // =========================================================================
    // 3. CREATE ACCOUNT FORM VALIDATION - 5 FULL CYCLES
    // =========================================================================

    @Test
    fun createAccountForm_Cycle1_emptyEmail_setsError() {
        val result = FormValidator.validateCreateAccount("", "")
        assertFalse(result.isValid)
        assertEquals("Please enter a valid email address.", result.emailError)
    }

    @Test
    fun createAccountForm_Cycle2_invalidEmailFormat_setsError() {
        val result = FormValidator.validateCreateAccount("student.artopia", "")
        assertFalse(result.isValid)
        assertEquals("Please enter a valid email address.", result.emailError)
    }

    @Test
    fun createAccountForm_Cycle3_shortCustomPassword_setsPasswordError() {
        val result = FormValidator.validateCreateAccount("student@artopia.com", "123")
        assertFalse(result.isValid)
        assertEquals("Minimum 8 characters", result.passwordError)
    }

    @Test
    fun createAccountForm_Cycle4_validStudentEmail_passesValidation() {
        val result = FormValidator.validateCreateAccount("student1@artopia.com", "")
        assertTrue(result.isValid)
        assertNull(result.emailError)
    }

    @Test
    fun createAccountForm_Cycle5_validTeacherWithPassword_passesValidation() {
        val result = FormValidator.validateCreateAccount("teacher1@artopia.com", "TeacherPass2026!")
        assertTrue(result.isValid)
        assertNull(result.emailError)
        assertNull(result.passwordError)
    }

    // =========================================================================
    // 4. CHANGE PASSWORD FORM VALIDATION - 5 FULL CYCLES
    // =========================================================================

    @Test
    fun changePasswordForm_Cycle1_allFieldsEmpty_setsAllErrors() {
        val result = FormValidator.validateChangePassword("", "", "")
        assertFalse(result.isValid)
        assertEquals("Current password is required", result.currentPasswordError)
        assertEquals("New password is required", result.passwordError)
        assertEquals("Please confirm your new password", result.confirmPasswordError)
    }

    @Test
    fun changePasswordForm_Cycle2_shortNewPassword_setsLengthError() {
        val result = FormValidator.validateChangePassword("OldPass123!", "12345", "12345")
        assertFalse(result.isValid)
        assertEquals("Password must be at least 8 characters long", result.passwordError)
    }

    @Test
    fun changePasswordForm_Cycle3_sameNewPassword_setsDifferenceError() {
        val result = FormValidator.validateChangePassword("OldPass123!", "OldPass123!", "OldPass123!")
        assertFalse(result.isValid)
        assertEquals("New password must be different from current password", result.passwordError)
    }

    @Test
    fun changePasswordForm_Cycle4_mismatchedConfirmPassword_setsMismatchError() {
        val result = FormValidator.validateChangePassword("OldPass123!", "NewPass123!", "DifferentPass123!")
        assertFalse(result.isValid)
        assertEquals("New passwords do not match", result.confirmPasswordError)
    }

    @Test
    fun changePasswordForm_Cycle5_validPasswordChange_passesValidation() {
        val result = FormValidator.validateChangePassword("OldPass123!", "NewPass123!", "NewPass123!")
        assertTrue(result.isValid)
        assertNull(result.currentPasswordError)
        assertNull(result.passwordError)
        assertNull(result.confirmPasswordError)
    }
}
