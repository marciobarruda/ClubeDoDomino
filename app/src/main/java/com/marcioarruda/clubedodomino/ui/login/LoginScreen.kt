package com.marcioarruda.clubedodomino.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.ui.theme.RoyalGold
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun LoginScreen(navController: NavController, loginViewModel: LoginViewModel = viewModel()) {
    val loginState by loginViewModel.loginState.collectAsState()
    val resetState by loginViewModel.resetPasswordState.collectAsState()
    
    var isResetPasswordMode by remember { mutableStateOf(false) }

    LaunchedEffect(loginState) {
        if (loginState is LoginUiState.Success) {
            val user = (loginState as LoginUiState.Success).user
            // Encode the ID to ensure safe navigation
            val encodedId = URLEncoder.encode(user.id, StandardCharsets.UTF_8.toString())
            navController.navigate("dashboard/$encodedId") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            Text("Clube do Dominó", style = MaterialTheme.typography.headlineLarge, color = RoyalGold)
            Spacer(modifier = Modifier.height(32.dp))

            if (isResetPasswordMode) {
                ResetPasswordForm(
                    resetState = resetState,
                    onReset = { email, pass -> loginViewModel.resetPassword(email, pass) },
                    onCancel = { 
                        isResetPasswordMode = false 
                        loginViewModel.clearResetState()
                    }
                )
            } else {
                LoginForm(
                    loginState = loginState,
                    onLogin = { email, pass -> loginViewModel.login(email, pass) },
                    onForgotPassword = { isResetPasswordMode = true }
                )
            }
        }
    }
}

@Composable
fun LoginForm(
    loginState: LoginUiState,
    onLogin: (String, String) -> Unit,
    onForgotPassword: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Regex rigorosa: Apenas letras (sem acento), números e @ . _ - +
    val emailRegex = Regex("[a-zA-Z0-9@._\\-+]")

    OutlinedTextField(
        value = email,
        onValueChange = { newValue -> 
            // Filtra caractere por caractere
            if (newValue.all { it.toString().matches(emailRegex) }) {
                email = newValue
            }
        },
        label = { Text("Email") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Senha") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))

    if (loginState is LoginUiState.Error) {
        Text(
            text = loginState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    Button(
        onClick = { onLogin(email, password) },
        enabled = loginState !is LoginUiState.Loading,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = RoyalGold)
    ) {
        if (loginState is LoginUiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
        } else {
            Text("Login", color = Color.Black)
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    TextButton(onClick = onForgotPassword) {
        Text("Esqueci a senha", color = RoyalGold)
    }
}

@Composable
fun ResetPasswordForm(
    resetState: ResetPasswordState,
    onReset: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val emailRegex = Regex("[a-zA-Z0-9@._\\-+]")

    Text("Redefinir Senha", style = MaterialTheme.typography.titleMedium, color = Color.White)
    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = email,
        onValueChange = { newValue -> 
             if (newValue.all { it.toString().matches(emailRegex) }) {
                email = newValue
            }
        },
        label = { Text("Email") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Nova Senha") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = confirmPassword,
        onValueChange = { confirmPassword = it },
        label = { Text("Confirmar Senha") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))

    if (errorMessage != null) {
        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
    }
    
    if (resetState is ResetPasswordState.Error) {
        Text(resetState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
    }

    if (resetState is ResetPasswordState.Success) {
        Text("Senha atualizada com sucesso!", color = Color.Green, modifier = Modifier.padding(bottom = 16.dp))
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = RoyalGold)
        ) {
            Text("Voltar ao Login", color = Color.Black)
        }
    } else {
        Button(
            onClick = {
                if (password != confirmPassword) {
                    errorMessage = "As senhas não coincidem."
                } else if (password.isBlank()) {
                    errorMessage = "A senha não pode ser vazia."
                } else {
                    errorMessage = null
                    onReset(email, password)
                }
            },
            enabled = resetState !is ResetPasswordState.Loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = RoyalGold)
        ) {
            if (resetState is ResetPasswordState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
            } else {
                Text("Atualizar Senha", color = Color.Black)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onCancel) {
            Text("Cancelar", color = Color.Gray)
        }
    }
}
