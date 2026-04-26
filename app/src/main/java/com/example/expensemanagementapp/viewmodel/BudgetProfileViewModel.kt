package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.BudgetProfileEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch

class BudgetProfileViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val allProfiles: LiveData<List<BudgetProfileEntity>> = repository.allProfiles

    fun addProfile(name: String, icon: String) {
        viewModelScope.launch {
            val profile = com.example.expensemanagementapp.data.entity.BudgetProfileEntity(
                name = name,
                icon = icon
            )
            repository.insertBudgetProfile(profile)
        }
    }

    fun updateProfile(profile: BudgetProfileEntity) {
        viewModelScope.launch {
            repository.updateBudgetProfile(profile)
        }
    }

    fun deleteProfile(profile: BudgetProfileEntity) {
        viewModelScope.launch {
            repository.softDeleteBudgetProfile(profile.id, System.currentTimeMillis())
        }
    }

    fun switchProfile(profileId: Int) {
        viewModelScope.launch {
            repository.switchProfile(profileId)
        }
    }
}
