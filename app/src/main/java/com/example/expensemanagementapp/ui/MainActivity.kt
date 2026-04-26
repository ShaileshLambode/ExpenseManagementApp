package com.example.expensemanagementapp.ui

import android.content.Intent
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
        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.navigation_add) {
                com.example.expensemanagementapp.ui.add.AddTransactionBottomSheet.newInstance()
                    .show(supportFragmentManager, com.example.expensemanagementapp.ui.add.AddTransactionBottomSheet.TAG)
                false
            } else {
                androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, navController)
                true
            }
        }

        val tvToolbarPageTitle: android.widget.TextView = findViewById(R.id.tvToolbarPageTitle)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            tvToolbarPageTitle.text = when (destination.id) {
                R.id.navigation_home       -> getString(R.string.app_name)
                R.id.navigation_history    -> "History"
                R.id.navigation_settings   -> "Settings"
                R.id.navigation_help       -> "Help & Docs"
                R.id.navigation_about      -> "About Us"
                R.id.navigation_backup     -> "Backup & Restore"
                R.id.navigation_profile    -> "Profile"
                R.id.navigation_analytics  -> "Analytics"
                R.id.navigation_goals      -> "Goals"
                R.id.nav_plans_dest        -> "Plans"
                R.id.navigation_recycle_bin -> "Recycle Bin"
                R.id.navigation_feedback -> "Review & Feedback"
                R.id.navigation_whats_new -> "What's New"
                else -> getString(R.string.app_name)
            }
            supportActionBar?.title = ""
        }

        // Setup ViewModel
        val db = AppDatabase.getDatabase(application)
        val repository = ExpenseRepository(
            db.expenseDao(),
            db.budgetProfileDao(),
            db.goalDao(),
            db.recurringTransactionDao()
        )
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        // Profile Selector
        val layoutProfileSelector: android.widget.LinearLayout = findViewById(R.id.layoutProfileSelector)
        val tvToolbarProfileName: android.widget.TextView = findViewById(R.id.tvToolbarProfileName)
        
        layoutProfileSelector.setOnClickListener {
            com.example.expensemanagementapp.ui.budget.BudgetProfileBottomSheet.newInstance()
                .show(supportFragmentManager, com.example.expensemanagementapp.ui.budget.BudgetProfileBottomSheet.TAG)
        }

         // Observe active profile to update toolbar
        val profileViewModel = ViewModelProvider(this, factory)[com.example.expensemanagementapp.viewmodel.BudgetProfileViewModel::class.java]
        val viewToolbarProfileColor: View = findViewById(R.id.viewToolbarProfileColor)
        
        profileViewModel.allProfiles.observe(this) { profiles ->
            val activeProfile = profiles.find { it.is_active }
            activeProfile?.let { profile ->
                tvToolbarProfileName.text = "${profile.name} ${profile.icon}"
                
                 // Apply profile color to Theme (Toolbar, Status Bar, Sidebar Header)
                try {
                    val colorInt = android.graphics.Color.parseColor(profile.color)
                    toolbar.setBackgroundColor(colorInt)
                    window.statusBarColor = colorInt
                    viewToolbarProfileColor.background?.setTint(colorInt)
                    viewToolbarProfileColor.visibility = View.GONE 
                    
                    // Update Sidebar Header
                    val headerView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.nav_view_drawer).getHeaderView(0)
                    headerView.findViewById<View>(R.id.navHeaderRoot)?.setBackgroundColor(colorInt)
                } catch (e: Exception) {}
                
                // Update active profile ID in preferences
                com.example.expensemanagementapp.data.PreferenceManager.getInstance(this).setActiveProfileId(profile.id)
            }
        }

        // Toolbar History Button
        val btnToolbarHistory: android.widget.ImageButton = findViewById(R.id.btnToolbarHistory)
        btnToolbarHistory.setOnClickListener {
            val navOptions = androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.navigation_home, false)
                .build()
            navController.navigate(R.id.navigation_history, null, navOptions)
        }

        // Check initialization
        var isSetupDialogShown = false
        viewModel.allBalances.observe(this) { balances ->
            if (balances.isEmpty() && !isSetupDialogShown && viewModel.activeProfileId.value != null) {
                isSetupDialogShown = true
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
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setPopUpTo(R.id.navigation_home, false)
            .build()
        
        when (item.itemId) {
            R.id.nav_history -> {
                navController.navigate(R.id.navigation_history, null, navOptions)
            }
            R.id.navigation_backup -> {
                navController.navigate(R.id.navigation_backup, null, navOptions)
            }
            R.id.nav_settings -> {
                navController.navigate(R.id.navigation_settings, null, navOptions)
            }
            R.id.nav_help -> {
                navController.navigate(R.id.navigation_help, null, navOptions)
            }
            R.id.navigation_recycle_bin -> {
                navController.navigate(R.id.navigation_recycle_bin, null, navOptions)
            }
            R.id.nav_about -> {
                navController.navigate(R.id.navigation_about, null, navOptions)
            }
            R.id.nav_whats_new -> {
                navController.navigate(R.id.navigation_whats_new, null, navOptions)
            }
            R.id.nav_feedback -> {
                navController.navigate(R.id.navigation_feedback, null, navOptions)
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
