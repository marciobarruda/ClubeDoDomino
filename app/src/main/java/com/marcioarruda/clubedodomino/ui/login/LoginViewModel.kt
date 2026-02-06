package com.marcioarruda.clubedodomino.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.data.UserSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: ClubRepository,
    private val sessionManager: UserSessionManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _resetPasswordState = MutableStateFlow<ResetPasswordState>(ResetPasswordState.Idle)
    val resetPasswordState: StateFlow<ResetPasswordState> = _resetPasswordState.asStateFlow()

    fun login(email: String, pass: String) {
        _loginState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val cleanEmail = email.trim()
                val response = repository.login(cleanEmail, pass.trim())

                if (response.status.equals("Login bem sucedido", ignoreCase = true)) {
                    val players = repository.getPlayers()
                    val user = players.find { it.id.equals(cleanEmail, ignoreCase = true) }

                    if (user != null) {
                        // Save the session to DataStore
                        sessionManager.saveSession(userName = user.name, userEmail = user.id)
                        _loginState.value = LoginUiState.Success(user)
                    } else {
                        _loginState.value = LoginUiState.Error("Login autorizado, mas perfil do usuário não encontrado.")
                    }
                } else {
                    _loginState.value = LoginUiState.Error("Email ou senha inválidos.")
                }
            } catch (e: Exception) {
                _loginState.value = LoginUiState.Error("Erro: ${e.message}")
            }
        }
    }

    fun resetPassword(email: String, pass: String) {
        _resetPasswordState.value = ResetPasswordState.Loading
        viewModelScope.launch {
            try {
                repository.updatePassword(email.trim(), pass.trim())
                _resetPasswordState.value = ResetPasswordState.Success
            } catch (e: Exception) {
                _resetPasswordState.value = ResetPasswordState.Error("Erro ao redefinir senha: ${e.message}")
            }
        }
    }
    
    fun clearResetState() {
        _resetPasswordState.value = ResetPasswordState.Idle
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val user: User) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

sealed class ResetPasswordState {
    object Idle : ResetPasswordState()
    object Loading : ResetPasswordState()
    object Success : ResetPasswordState()
    data class Error(val message: String) : ResetPasswordState()
}
