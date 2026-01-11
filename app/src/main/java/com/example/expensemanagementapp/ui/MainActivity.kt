package com.example.expensemanagementapp.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.viewmodel.MainViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var viewModel: MainViewModel
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Restore Theme
        val prefs = com.example.expensemanagementapp.data.PreferenceManager.getInstance(this)
        val isDarkMode = prefs.isDarkMode()
        if (isDarkMode) {
             androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
             androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }

        setContentView(R.layout.activity_main)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        // Setup Drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        val navViewDrawer: NavigationView = findViewById(R.id.nav_view_drawer)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        navViewDrawer.setNavigationItemSelectedListener(this)
        
        // Theme Toggle Logic
        val btnThemeToggle = navViewDrawer.findViewById<android.widget.ImageButton>(R.id.btnThemeToggle)
        btnThemeToggle?.setOnClickListener {
            val currentMode = prefs.isDarkMode()
            val nextMode = !currentMode
            
            prefs.setDarkMode(nextMode)
            
            // Apply (Activity recreation might happen)
            if (nextMode) {
                 androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                 androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            }
            // Close drawer to avoid weird UX during recreation
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Setup Navigation Controller & Bottom Nav
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController: NavController = navHostFragment.navController
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.nav_view)

        bottomNavigationView.setupWithNavController(navController)

        // Setup ViewModel
        val dao = AppDatabase.getDatabase(application).expenseDao()
        val repository = ExpenseRepository(dao)
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        // Check initialization
        viewModel.allBalances.observe(this) { balances ->
            if (balances.isEmpty()) {
                showInitialSetupDialog()
            }
        }
        
        viewModel.checkAndInitData()

        // Sync Profile to Header
        viewModel.userProfile.observe(this) { profile ->
            val headerView = navViewDrawer.getHeaderView(0)
            val ivHeaderProfile = headerView.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.ivHeaderProfile)
            val tvHeaderName = headerView.findViewById<android.widget.TextView>(R.id.tvHeaderName)
            val tvHeaderSubtitle = headerView.findViewById<android.widget.TextView>(R.id.tvHeaderSubtitle)

            if (profile != null) {
                tvHeaderName.text = profile.name
                tvHeaderSubtitle.text = "Personal User" // Or profile.email if available
                if (profile.imageUri != null) {
                    ivHeaderProfile.setImageURI(android.net.Uri.parse(profile.imageUri))

                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        when (item.itemId) {
            R.id.nav_profile -> {
                navController.navigate(R.id.navigation_profile)
            }
            R.id.nav_settings -> {
                navController.navigate(R.id.navigation_settings)
            }
            R.id.nav_backup -> {
                 navController.navigate(R.id.navigation_backup)
            }
            R.id.nav_help -> {
                 navController.navigate(R.id.navigation_help)
            }
            R.id.nav_about -> {
                 navController.navigate(R.id.navigation_about)
            }
            R.id.nav_whats_new -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.whats_new_title))
                    .setMessage(getString(R.string.whats_new_content))
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun showInitialSetupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_initial_setup, null)
        val etInitialBank = dialogView.findViewById<TextInputEditText>(R.id.etInitialBank)
        val etInitialCash = dialogView.findViewById<TextInputEditText>(R.id.etInitialCash)
        val btnStartApp = dialogView.findViewById<View>(R.id.btnStartApp)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnStartApp.setOnClickListener {
            val bankAmount = etInitialBank.text.toString().toDoubleOrNull() ?: 0.0
            val cashAmount = etInitialCash.text.toString().toDoubleOrNull() ?: 0.0
            
            viewModel.setInitialBalance(bankAmount, cashAmount)
            dialog.dismiss()
        }

        dialog.show()
    }
}
