package com.marcioarruda.clubedodomino.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.Match
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MatchDetailsUiState {
    object Loading : MatchDetailsUiState()
    data class Success(val match: Match) : MatchDetailsUiState()
    data class Error(val message: String) : MatchDetailsUiState()
}

class MatchDetailsViewModel(private val repository: ClubRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<MatchDetailsUiState>(MatchDetailsUiState.Loading)
    val uiState: StateFlow<MatchDetailsUiState> = _uiState.asStateFlow()

    fun loadMatch(matchId: String) {
        viewModelScope.launch {
            _uiState.value = MatchDetailsUiState.Loading
            try {
                val match = repository.getMatch(matchId)
                if (match != null) {
                    _uiState.value = MatchDetailsUiState.Success(match)
                } else {
                    _uiState.value = MatchDetailsUiState.Error("Partida não encontrada.")
                }
            } catch (e: Exception) {
                _uiState.value = MatchDetailsUiState.Error("Erro ao carregar partida: ${e.message}")
            }
        }
    }
}
