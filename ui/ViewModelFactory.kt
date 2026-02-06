package com.marcioarruda.clubedodomino.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.ui.register.MatchViewModel

class ViewModelFactory(private val repository: ClubRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MatchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MatchViewModel(repository) as T
        }
        // Adicionar outros ViewModels aqui
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
