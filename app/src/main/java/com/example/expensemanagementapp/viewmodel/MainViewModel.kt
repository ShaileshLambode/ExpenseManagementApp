package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.BalanceEntity
import com.example.expensemanagementapp.data.entity.CategoryEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val allProfiles: LiveData<List<com.example.expensemanagementapp.data.entity.BudgetProfileEntity>> = repository.allProfiles
    
    private val activeProfile = allProfiles.map { profiles: List<com.example.expensemanagementapp.data.entity.BudgetProfileEntity> ->
        profiles.find { it.is_active }
    }

    val activeProfileId: LiveData<Int?> = activeProfile.map { it?.id }

    val allBalances: LiveData<List<BalanceEntity>> = activeProfileId.switchMap { profileId: Int? ->
        if (profileId != null) {
            repository.getBalancesByProfile(profileId)
        } else {
            androidx.lifecycle.MutableLiveData()
        }
    }
    
    val userProfile = repository.getProfile()

    fun checkAndInitData() {
        viewModelScope.launch {
            val profiles = repository.getProfilesSync()
            if (profiles.isEmpty()) {
                repository.insertBudgetProfile(com.example.expensemanagementapp.data.entity.BudgetProfileEntity(
                    name = "Primary Budget",
                    icon = "💰",
                    is_active = true
                ))
            } else {
                if (profiles.none { it.is_active }) {
                    repository.switchProfile(profiles[0].id)
                }
            }
        }
    }

    fun initCategories() {
        viewModelScope.launch {
            val existing = repository.getAllCategoriesSync()
            if (existing.isEmpty()) {
                val categories = listOf(
                    CategoryEntity(name = "Food", icon = "ic_food"),
                    CategoryEntity(name = "Transport", icon = "ic_transport"),
                    CategoryEntity(name = "Shopping", icon = "ic_shopping"),
                    CategoryEntity(name = "Bills", icon = "ic_bills"),
                    CategoryEntity(name = "Entertainment", icon = "ic_entertainment"),
                    CategoryEntity(name = "Health", icon = "ic_health"),
                    CategoryEntity(name = "Other", icon = "ic_other")
                )
                categories.forEach { repository.insertCategory(it) }
            }
        }
    }

    fun setInitialBalance(bankAmount: Double, cashAmount: Double) {
        viewModelScope.launch {
            // Ensure we have an active profile
            var profileId = activeProfileId.value
            if (profileId == null) {
                val profiles = repository.getProfilesSync()
                if (profiles.isEmpty()) {
                    repository.insertBudgetProfile(com.example.expensemanagementapp.data.entity.BudgetProfileEntity(
                        name = "Primary Budget",
                        icon = "💰",
                        is_active = true
                    ))
                    profileId = repository.getProfilesSync().firstOrNull { it.is_active }?.id
                } else {
                    profileId = profiles.firstOrNull { it.is_active }?.id ?: profiles.first().id
                }
            }
            
            repository.insertBalance(BalanceEntity(budget_profile_id = profileId!!, type = "BANK", amount = bankAmount, last_updated = System.currentTimeMillis()))
            repository.insertBalance(BalanceEntity(budget_profile_id = profileId, type = "CASH", amount = cashAmount, last_updated = System.currentTimeMillis()))
            initCategories()
        }
    }
}
