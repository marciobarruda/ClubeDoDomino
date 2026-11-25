package com.marcioarruda.clubedodomino.ui.torneio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.data.User
import com.marcioarruda.clubedodomino.data.network.SalvarTorneioRequest
import com.marcioarruda.clubedodomino.data.network.TorneioDto
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TorneioUiState(
    val isLoading: Boolean = false,
    val torneios: List<TorneioDto> = emptyList(),
    val players: List<User> = emptyList(),
    val detalhe: TorneioDto? = null,
    val isLoadingDetalhe: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class TorneioViewModel(private val repository: ClubRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TorneioUiState())
    val uiState: StateFlow<TorneioUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.torneios.isEmpty(), error = null) }
            try {
                val playersDeferred = async { runCatching { repository.getPlayers() }.getOrDefault(emptyList()) }
                val torneios = repository.getTorneios()
                _uiState.update { it.copy(isLoading = false, torneios = torneios, players = playersDeferred.await()) }
                _uiState.value.detalhe?.let { abrirDetalhe(it.id) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao carregar torneios: ${e.message}") }
            }
        }
    }

    fun abrirDetalhe(id: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetalhe = true) }
            try {
                val detalhe = repository.getTorneio(id)
                _uiState.update { it.copy(isLoadingDetalhe = false, detalhe = detalhe) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingDetalhe = false, message = "Erro ao abrir torneio: ${e.message}") }
            }
        }
    }

    fun fecharDetalhe() = _uiState.update { it.copy(detalhe = null) }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun salvar(id: Int?, nome: String, data: String, horaInicio: String, horaFim: String, corteTitulos: String, criadoPor: String?, onSuccess: () -> Unit) {
        acao(sucesso = if (id == null) "Torneio cadastrado!" else "Torneio atualizado!", onSuccess = onSuccess) {
            repository.salvarTorneio(id, SalvarTorneioRequest(nome.trim(), data, horaInicio, horaFim, corteTitulos.ifBlank { null }, criadoPor))
        }
    }

    fun excluir(id: Int) = acao(sucesso = "Torneio excluído.", fecharDetalhe = true) { repository.deleteTorneio(id) }

    fun encerrar(id: Int) = acao(sucesso = "Torneio encerrado! Títulos definidos. 🏆") { repository.encerrarTorneio(id) }

    fun reabrir(id: Int) = acao(sucesso = "Torneio reaberto.") { repository.reabrirTorneio(id) }

    fun excluirPartida(torneioId: Int, partidaId: Long) =
        acao(sucesso = "Partida excluída.") { repository.deleteTorneioMatch(torneioId, partidaId) }

    // Executa uma ação de escrita, mostra a mensagem e recarrega lista + detalhe aberto.
    private fun acao(
        sucesso: String,
        fecharDetalhe: Boolean = false,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit
    ) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                block()
                _uiState.update { it.copy(isSaving = false, message = sucesso, detalhe = if (fecharDetalhe) null else it.detalhe) }
                onSuccess()
                load()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, message = e.message ?: "Erro inesperado.") }
            }
        }
    }
}
