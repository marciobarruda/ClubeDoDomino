package com.marcioarruda.clubedodomino.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.UserSession
import com.marcioarruda.clubedodomino.data.UserSessionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AuthState {
    object Loading : AuthState
    data class Authenticated(val session: UserSession) : AuthState
    object Unauthenticated : AuthState
}

class MainViewModel(sessionManager: UserSessionManager) : ViewModel() {

    val authState: StateFlow<AuthState> = sessionManager.getSession
        .map { session ->
            if (session != null) {
                AuthState.Authenticated(session)
            } else {
                AuthState.Unauthenticated
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AuthState.Loading
        )
}
