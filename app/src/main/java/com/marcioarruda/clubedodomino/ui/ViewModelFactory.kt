package com.marcioarruda.clubedodomino.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.marcioarruda.clubedodomino.DominoClubApplication
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.ui.dashboard.DashboardViewModel
import com.marcioarruda.clubedodomino.ui.finance.FinanceViewModel
import com.marcioarruda.clubedodomino.ui.login.LoginViewModel
import com.marcioarruda.clubedodomino.ui.ranking.RankingViewModel
import com.marcioarruda.clubedodomino.ui.register.MatchViewModel

class ViewModelFactory(private val repository: ClubRepository) : ViewModelProvider.Factory {

    private val sessionManager = DominoClubApplication.instance.sessionManager

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) ->
                MainViewModel(sessionManager) as T
            modelClass.isAssignableFrom(LoginViewModel::class.java) ->
                LoginViewModel(repository, sessionManager) as T
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(repository) as T
            modelClass.isAssignableFrom(MatchViewModel::class.java) ->
                MatchViewModel(
                    repository,
                    com.marcioarruda.clubedodomino.data.AdminRepository(DominoClubApplication.instance)
                ) as T
            modelClass.isAssignableFrom(RankingViewModel::class.java) ->
                RankingViewModel(repository) as T
            modelClass.isAssignableFrom(FinanceViewModel::class.java) ->
                FinanceViewModel(
                    repository,
                    com.marcioarruda.clubedodomino.data.AdminRepository(DominoClubApplication.instance)
                ) as T
            modelClass.isAssignableFrom(com.marcioarruda.clubedodomino.ui.admin.AdminViewModel::class.java) ->
                com.marcioarruda.clubedodomino.ui.admin.AdminViewModel(
                    repository, 
                    com.marcioarruda.clubedodomino.data.AdminRepository(DominoClubApplication.instance)
                ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
