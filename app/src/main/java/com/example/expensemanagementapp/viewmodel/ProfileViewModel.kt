package com.example.expensemanagementapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensemanagementapp.data.entity.ProfileEntity
import com.example.expensemanagementapp.repository.ExpenseRepository
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val userProfile: LiveData<ProfileEntity?> = repository.getProfile()

    fun saveProfile(name: String, dob: Long?, city: String, state: String, mobile: String, imageUri: String?, gender: String?) {
        val profile = ProfileEntity(
            id = 1,
            name = name,
            dob = dob,
            city = city,
            state = state,
            mobile = mobile,
            imageUri = imageUri,
            gender = gender
        )
        viewModelScope.launch {
            repository.insertProfile(profile)
        }
    }
}
