package com.example.artopiacontrol

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.artopiacontrol.data.AdminRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: AdminRepository

    private var presenceRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        // Hide bottom navigation on certain screens if needed
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.dashboardFragment,
                R.id.userListFragment,
                R.id.activityLogFragment,
                R.id.profileFragment -> {
                    bottomNav.visibility = View.VISIBLE
                }
                else -> {
                    bottomNav.visibility = View.GONE
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        evaluateSessionGate()
    }

    private fun evaluateSessionGate() {
        val user = repository.getCurrentUser()
        if (user == null) {
            redirectToLogin()
            return
        }

        lifecycleScope.launch {
            try {
                user.reload().await()
            } catch (e: Exception) {
                // Ignore reload error
            }

            if (!user.isEmailVerified) {
                repository.logout()
                redirectToLogin()
                return@launch
            }

            if (!repository.canAccessControlPanel() || !repository.isAccountActive()) {
                repository.logout()
                redirectToLogin()
                return@launch
            }

            if (repository.mustChangePassword()) {
                startActivity(Intent(this@MainActivity, ChangePasswordActivity::class.java))
                finish()
                return@launch
            }

            if (!presenceRegistered) {
                repository.setUserPresence(user.uid)
                presenceRegistered = true
            }
        }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
}
