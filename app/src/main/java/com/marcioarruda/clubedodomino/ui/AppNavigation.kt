package com.marcioarruda.clubedodomino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.marcioarruda.clubedodomino.data.ClubRepository
import com.marcioarruda.clubedodomino.ui.dashboard.DashboardScreen
import com.marcioarruda.clubedodomino.ui.finance.FinanceScreen
import com.marcioarruda.clubedodomino.ui.login.LoginScreen
import com.marcioarruda.clubedodomino.ui.ranking.RankingScreen
import com.marcioarruda.clubedodomino.ui.register.RegisterMatchScreen
import com.marcioarruda.clubedodomino.ui.theme.DominoGold
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNavigation() {
    val clubRepository = remember { ClubRepository() }
    val viewModelFactory = remember { ViewModelFactory(clubRepository) }
    val mainViewModel: MainViewModel = viewModel(factory = viewModelFactory)
    val authState by mainViewModel.authState.collectAsState()
    val navController = rememberNavController()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = authState) {
            is AuthState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = DominoGold
                )
            }
            is AuthState.Authenticated -> {
                AppNavHost(navController, startDestination = "dashboard/{userId}", session = state.session)
            }
            is AuthState.Unauthenticated -> {
                AppNavHost(navController, startDestination = "login", session = null)
            }
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    session: com.marcioarruda.clubedodomino.data.UserSession?
) {
    val clubRepository = remember { ClubRepository() }
    val viewModelFactory = remember { ViewModelFactory(clubRepository) }

    LaunchedEffect(startDestination) {
        val finalDestination = if (session != null) {
            val encodedId = URLEncoder.encode(session.userEmail, StandardCharsets.UTF_8.toString())
            "dashboard/$encodedId"
        } else {
            "login"
        }

        navController.navigate(finalDestination) {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(navController = navController, loginViewModel = viewModel(factory = viewModelFactory))
        }
        composable(
            route = "dashboard/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            backStackEntry.arguments?.getString("userId")?.let { userId ->
                val decodedId = URLDecoder.decode(userId, StandardCharsets.UTF_8.toString())
                DashboardScreen(navController = navController, userId = decodedId, viewModel = viewModel(factory = viewModelFactory))
            }
        }
        composable(
            route = "register_match?matchId={matchId}",
            arguments = listOf(navArgument("matchId") { 
                type = NavType.StringType 
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString("matchId")
            RegisterMatchScreen(navController = navController, viewModel = viewModel(factory = viewModelFactory), matchId = matchId, session = session)
        }
        composable("ranking") {
            RankingScreen(navController = navController, rankingViewModel = viewModel(factory = viewModelFactory))
        }
        composable(
            route = "finance/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            backStackEntry.arguments?.getString("userId")?.let { userId ->
                val decodedId = URLDecoder.decode(userId, StandardCharsets.UTF_8.toString())
                FinanceScreen(navController = navController, userId = decodedId, viewModel = viewModel(factory = viewModelFactory))
            }
        }
        composable("admin") {
            com.marcioarruda.clubedodomino.ui.admin.AdminScreen(
                factory = viewModelFactory, 
                onBack = { navController.popBackStack() },
                onEditMatch = { matchId ->
                    navController.navigate("register_match?matchId=$matchId")
                },
                session = run {
                    val authState = (viewModel(factory = viewModelFactory) as MainViewModel).authState.collectAsState().value
                    if (authState is AuthState.Authenticated) authState.session else null
                }
            )
        }
    }
}
