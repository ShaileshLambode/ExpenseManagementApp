package com.example.expensemanagementapp.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.expensemanagementapp.R

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val prefManager = com.example.expensemanagementapp.data.PreferenceManager.getInstance(this)
            
            val intent = if (prefManager.isFirstLaunch()) {
                Intent(this, com.example.expensemanagementapp.ui.onboarding.OnboardingActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }
            
            startActivity(intent)
            finish()
        }, 2500) // Slightly longer splash for premium feel
    }
}
