package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.GoalEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch

class GoalsViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val allProfiles = repository.allProfiles
    
    private val activeProfile = allProfiles.map { profiles ->
        profiles.find { it.is_active }
    }

    val activeProfileId: LiveData<Int?> = activeProfile.map { it?.id }

    val goals: LiveData<List<GoalEntity>> = activeProfileId.switchMap { profileId ->
        if (profileId != null) {
            repository.getGoalsByProfile(profileId)
        } else {
            MutableLiveData(emptyList())
        }
    }

    fun addGoal(name: String, target: Double, deadline: Long?, icon: String, color: String) {
        val profileId = activeProfileId.value ?: return
        viewModelScope.launch {
            repository.insertGoal(GoalEntity(
                budget_profile_id = profileId,
                name = name,
                target_amount = target,
                deadline = deadline,
                icon = icon,
                color = color
            ))
        }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
        }
    }

    fun contribute(goal: GoalEntity, amount: Double, mode: String) {
        val profileId = activeProfileId.value ?: return
        viewModelScope.launch {
            repository.contributeToGoal(profileId, goal.id, amount, mode, goal.name)
        }
    }
}
