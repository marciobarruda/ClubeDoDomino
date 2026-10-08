package com.marcioarruda.clubedodomino.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.UserSession
import com.marcioarruda.clubedodomino.data.UserSessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface AuthState {
    object Loading : AuthState
    data class Authenticated(val session: UserSession) : AuthState
    object Unauthenticated : AuthState
}

// Tempo mínimo que a tela de carregamento (com a animação das peças de dominó) fica visível,
// mesmo quando a sessão já está em cache e resolveria instantaneamente — sem isso, a animação
// (que leva ~900ms para completar fade-in + escala + rotação) é cortada antes de ser percebida
// sempre que o usuário já está logado, que é o caso mais comum ao abrir o app.
private const val MIN_SPLASH_DURATION_MS = 1200L

class MainViewModel(sessionManager: UserSessionManager) : ViewModel() {

    val authState: StateFlow<AuthState> = sessionManager.getSession
        .map { session ->
            if (session != null) {
                AuthState.Authenticated(session)
            } else {
                AuthState.Unauthenticated
            }
        }
        .onStart { delay(MIN_SPLASH_DURATION_MS) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AuthState.Loading
        )
}
