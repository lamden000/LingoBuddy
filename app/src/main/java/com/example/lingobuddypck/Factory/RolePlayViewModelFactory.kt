package com.example.lingobuddypck.Factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.lingobuddypck.ViewModel.RolePlayChatViewModel

class RolePlayViewModelFactory(
    private val userRole: String,
    private val aiRole: String,
    private val context: String
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RolePlayChatViewModel::class.java)) {
            return RolePlayChatViewModel(userRole,aiRole, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}