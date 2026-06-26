package com.marcioarruda.clubedodomino.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.marcioarruda.clubedodomino.ui.theme.*
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
            val encodedId = URLEncoder.encode(user.id, StandardCharsets.UTF_8.toString())
            navController.navigate("dashboard/$encodedId") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF071020), DominoBg, Color(0xFF0D1E1A)))
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background dots
        Canvas(modifier = Modifier.fillMaxSize()) {
            val positions = listOf(
                Offset(size.width * 0.1f, size.height * 0.12f),
                Offset(size.width * 0.9f, size.height * 0.08f),
                Offset(size.width * 0.85f, size.height * 0.88f),
                Offset(size.width * 0.05f, size.height * 0.92f)
            )
            positions.forEach { pos ->
                drawCircle(color = DominoGreen.copy(alpha = 0.07f), radius = 80f, center = pos)
                drawCircle(color = DominoOrange.copy(alpha = 0.05f), radius = 50f, center = pos)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
        ) {
            Spacer(Modifier.height(40.dp))

            // Logo canvas - domino tile
            val logoScale = remember { Animatable(0.6f) }
            LaunchedEffect(Unit) { logoScale.animateTo(1f, tween(600, easing = EaseOutBack)) }

            Canvas(
                modifier = Modifier
                    .size(140.dp, 70.dp)
                    .scale(logoScale.value)
            ) {
                // Shadow
                drawRoundRect(color = Color.Black.copy(0.5f), topLeft = Offset(6f, 6f), size = Size(size.width, size.height), cornerRadius = CornerRadius(size.height * 0.14f))
                // Body
                drawRoundRect(color = Color.White, size = Size(size.width, size.height), cornerRadius = CornerRadius(size.height * 0.14f))
                // Divider
                drawLine(color = Color(0xFF94A3B8).copy(alpha = 0.4f), start = Offset(size.width / 2f, size.height * 0.15f), end = Offset(size.width / 2f, size.height * 0.85f), strokeWidth = 1.5f)
                // Left: 6 dots
                val dR = size.height * 0.09f
                val lCx = size.width * 0.26f
                val rCx = size.width * 0.74f
                val cy = size.height / 2f
                val xOff = size.width * 0.07f
                val yOff = size.height * 0.28f
                listOf(Offset(lCx - xOff, cy - yOff), Offset(lCx + xOff, cy - yOff), Offset(lCx - xOff, cy), Offset(lCx + xOff, cy), Offset(lCx - xOff, cy + yOff), Offset(lCx + xOff, cy + yOff)).forEach { drawCircle(DominoBg, dR, it) }
                // Right: 2 dots
                listOf(Offset(rCx - xOff * 0.7f, cy - yOff * 0.7f), Offset(rCx + xOff * 0.7f, cy + yOff * 0.7f)).forEach { drawCircle(DominoBg, dR, it) }
            }

            Spacer(Modifier.height(20.dp))
            Text("Clube do Dominó", style = MaterialTheme.typography.headlineMedium, color = DominoGreen, fontWeight = FontWeight.Black)
            Text("EMPREL", color = DominoYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Spacer(Modifier.height(36.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DominoSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp)) {
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
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun LoginForm(loginState: LoginUiState, onLogin: (String, String) -> Unit, onForgotPassword: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val emailRegex = Regex("[a-zA-Z0-9@._\\-+]")

    Text("Entrar", style = MaterialTheme.typography.titleLarge, color = DominoLight, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))

    OutlinedTextField(
        value = email,
        onValueChange = { if (it.all { c -> c.toString().matches(emailRegex) }) email = it },
        label = { Text("E-mail") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DominoGreen,
            unfocusedBorderColor = DominoMuted.copy(alpha = 0.4f),
            focusedLabelColor = DominoGreen,
            focusedTextColor = DominoLight,
            unfocusedTextColor = DominoLight,
            cursorColor = DominoGreen
        )
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Senha") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DominoGreen,
            unfocusedBorderColor = DominoMuted.copy(alpha = 0.4f),
            focusedLabelColor = DominoGreen,
            focusedTextColor = DominoLight,
            unfocusedTextColor = DominoLight,
            cursorColor = DominoGreen
        )
    )
    Spacer(Modifier.height(20.dp))

    if (loginState is LoginUiState.Error) {
        Text(loginState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
    }

    Button(
        onClick = { onLogin(email, password) },
        enabled = loginState !is LoginUiState.Loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DominoGreen)
    ) {
        if (loginState is LoginUiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
        } else {
            Text("Jogar!", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth()) {
        Text("Esqueci a senha", color = DominoMuted)
    }
}

@Composable
fun ResetPasswordForm(resetState: ResetPasswordState, onReset: (String, String) -> Unit, onCancel: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val emailRegex = Regex("[a-zA-Z0-9@._\\-+]")

    Text("Redefinir Senha", style = MaterialTheme.typography.titleLarge, color = DominoLight, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DominoGreen,
        unfocusedBorderColor = DominoMuted.copy(alpha = 0.4f),
        focusedLabelColor = DominoGreen,
        focusedTextColor = DominoLight,
        unfocusedTextColor = DominoLight,
        cursorColor = DominoGreen
    )

    OutlinedTextField(value = email, onValueChange = { if (it.all { c -> c.toString().matches(emailRegex) }) email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = fieldColors)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Nova Senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirmar Senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
    Spacer(Modifier.height(20.dp))

    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
    if (resetState is ResetPasswordState.Error) Text(resetState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
    if (resetState is ResetPasswordState.Success) {
        Text("Senha atualizada com sucesso!", color = DominoGreen, modifier = Modifier.padding(bottom = 8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = DominoGreen)) {
            Text("Voltar ao Login", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    } else {
        Button(
            onClick = {
                when {
                    password != confirmPassword -> errorMessage = "As senhas não coincidem."
                    password.isBlank() -> errorMessage = "A senha não pode ser vazia."
                    else -> { errorMessage = null; onReset(email, password) }
                }
            },
            enabled = resetState !is ResetPasswordState.Loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DominoGreen)
        ) {
            if (resetState is ResetPasswordState.Loading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
            else Text("Atualizar Senha", color = Color.Black, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar", color = DominoMuted) }
    }
}
