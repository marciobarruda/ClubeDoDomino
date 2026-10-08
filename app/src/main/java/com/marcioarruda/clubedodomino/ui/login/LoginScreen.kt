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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.marcioarruda.clubedodomino.ui.theme.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private enum class LoginScreenMode { LOGIN, RESET_PASSWORD, DB_RECOVERY }

@Composable
fun LoginScreen(navController: NavController, loginViewModel: LoginViewModel = viewModel()) {
    val loginState by loginViewModel.loginState.collectAsState()
    val resetState by loginViewModel.resetPasswordState.collectAsState()
    val dbRecoveryState by loginViewModel.dbRecoveryState.collectAsState()
    var screenMode by remember { mutableStateOf(LoginScreenMode.LOGIN) }

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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Deep green "mesa de dominó" background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DominoGreen)
        )
        // Subtle radial texture — two soft highlight points
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(DominoGreenAlt.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(0.15f, 0.08f),
                        radius = 900f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(DominoGreenAlt.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(0.85f, 0.95f),
                        radius = 900f
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
        ) {
            Spacer(Modifier.height(48.dp))

            // Domino pieces artwork
            val logoScale = remember { Animatable(0.6f) }
            LaunchedEffect(Unit) { logoScale.animateTo(1f, tween(600, easing = EaseOutBack)) }

            Image(
                painter = painterResource(id = com.marcioarruda.clubedodomino.R.drawable.domino_pieces),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(140.dp)
                    .scale(logoScale.value)
                    .shadow(elevation = 16.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f), clip = false)
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "Clube do Dominó",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                color = DominoOnDark,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Entre para ver o craque do dia",
                color = DominoOnDarkMuted,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(36.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DominoSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(24.dp)) {
                    when (screenMode) {
                        LoginScreenMode.RESET_PASSWORD -> ResetPasswordForm(
                            resetState = resetState,
                            onReset = { email, pass -> loginViewModel.resetPassword(email, pass) },
                            onCancel = {
                                screenMode = LoginScreenMode.LOGIN
                                loginViewModel.clearResetState()
                            }
                        )
                        LoginScreenMode.DB_RECOVERY -> DbRecoveryForm(
                            recoveryState = dbRecoveryState,
                            onRecover = { adminKey, novaSenha -> loginViewModel.emergencyUpdateDbPassword(adminKey, novaSenha) },
                            onCancel = {
                                screenMode = LoginScreenMode.LOGIN
                                loginViewModel.clearDbRecoveryState()
                            }
                        )
                        LoginScreenMode.LOGIN -> LoginForm(
                            loginState = loginState,
                            onLogin = { email, pass -> loginViewModel.login(email, pass) },
                            onForgotPassword = { screenMode = LoginScreenMode.RESET_PASSWORD },
                            onServerIssue = { screenMode = LoginScreenMode.DB_RECOVERY }
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun dominoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DominoGreen,
    unfocusedBorderColor = Color(0xFFE6DAB8),
    focusedLabelColor = DominoGreen,
    unfocusedLabelColor = DominoMuted,
    focusedTextColor = DominoLight,
    unfocusedTextColor = DominoLight,
    cursorColor = DominoGreen,
    focusedContainerColor = DominoSurface,
    unfocusedContainerColor = DominoSurface
)

@Composable
fun LoginForm(
    loginState: LoginUiState,
    onLogin: (String, String) -> Unit,
    onForgotPassword: () -> Unit,
    onServerIssue: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val emailRegex = Regex("[a-zA-Z0-9@._\\-+]")
    val fieldColors = dominoFieldColors()

    Text("Entrar", style = MaterialTheme.typography.titleLarge, color = DominoLight, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))

    OutlinedTextField(
        value = email,
        onValueChange = { if (it.all { c -> c.toString().matches(emailRegex) }) email = it },
        label = { Text("E-mail") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        colors = fieldColors
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Senha") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors
    )
    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onForgotPassword, contentPadding = PaddingValues(0.dp)) {
            Text("Esqueci minha senha", color = DominoGreenAlt, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(12.dp))

    if (loginState is LoginUiState.Error) {
        Text(loginState.message, color = DominoError, modifier = Modifier.padding(bottom = 12.dp))
    }

    Button(
        onClick = { onLogin(email, password) },
        enabled = loginState !is LoginUiState.Loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DominoYellow)
    ) {
        if (loginState is LoginUiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DominoGreen)
        } else {
            Text("Entrar", color = DominoGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onServerIssue, modifier = Modifier.fillMaxWidth()) {
        Text("Não consigo entrar / erro do servidor", color = DominoMuted, fontSize = 12.sp)
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

    val fieldColors = dominoFieldColors()
    val fieldShape = RoundedCornerShape(14.dp)

    OutlinedTextField(value = email, onValueChange = { if (it.all { c -> c.toString().matches(emailRegex) }) email = it }, label = { Text("E-mail") }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), colors = fieldColors)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Nova Senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirmar Senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
    Spacer(Modifier.height(20.dp))

    errorMessage?.let { Text(it, color = DominoError, modifier = Modifier.padding(bottom = 8.dp)) }
    if (resetState is ResetPasswordState.Error) Text(resetState.message, color = DominoError, modifier = Modifier.padding(bottom = 8.dp))
    if (resetState is ResetPasswordState.Success) {
        Text("Senha atualizada com sucesso!", color = DominoGreenAlt, modifier = Modifier.padding(bottom = 8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = DominoYellow)) {
            Text("Voltar ao Login", color = DominoGreen, fontWeight = FontWeight.Bold)
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
            colors = ButtonDefaults.buttonColors(containerColor = DominoYellow)
        ) {
            if (resetState is ResetPasswordState.Loading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DominoGreen)
            else Text("Atualizar Senha", color = DominoGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar", color = DominoMuted) }
    }
}

@Composable
fun DbRecoveryForm(
    recoveryState: DbRecoveryState,
    onRecover: (adminKey: String, novaSenha: String) -> Unit,
    onCancel: () -> Unit
) {
    var adminKey by remember { mutableStateOf("") }
    var novaSenha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fieldColors = dominoFieldColors()
    val fieldShape = RoundedCornerShape(14.dp)

    Text("Corrigir Senha do Banco de Dados", style = MaterialTheme.typography.titleLarge, color = DominoLight, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text(
        "Use esta opção apenas se o login estiver falhando por erro do servidor (senha do banco de " +
        "dados desalinhada). Exige a chave de administração do servidor — não é a senha do seu login.",
        color = DominoMuted,
        fontSize = 12.sp
    )
    Spacer(Modifier.height(20.dp))

    OutlinedTextField(
        value = adminKey,
        onValueChange = { adminKey = it },
        label = { Text("Chave de administração do servidor") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        shape = fieldShape,
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = novaSenha,
        onValueChange = { novaSenha = it },
        label = { Text("Senha correta do banco de dados") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        shape = fieldShape,
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = confirmarSenha,
        onValueChange = { confirmarSenha = it },
        label = { Text("Confirmar senha do banco de dados") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        shape = fieldShape,
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors
    )
    Spacer(Modifier.height(20.dp))

    errorMessage?.let { Text(it, color = DominoError, modifier = Modifier.padding(bottom = 8.dp)) }
    if (recoveryState is DbRecoveryState.Error) Text(recoveryState.message, color = DominoError, modifier = Modifier.padding(bottom = 8.dp))

    if (recoveryState is DbRecoveryState.Success) {
        Text("Senha do banco corrigida com sucesso! Já pode tentar fazer login normalmente.", color = DominoGreenAlt, modifier = Modifier.padding(bottom = 8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = DominoYellow)) {
            Text("Voltar ao Login", color = DominoGreen, fontWeight = FontWeight.Bold)
        }
    } else {
        Button(
            onClick = {
                when {
                    adminKey.isBlank() -> errorMessage = "Informe a chave de administração."
                    novaSenha != confirmarSenha -> errorMessage = "As senhas não coincidem."
                    novaSenha.isBlank() -> errorMessage = "A senha não pode ser vazia."
                    else -> { errorMessage = null; onRecover(adminKey, novaSenha) }
                }
            },
            enabled = recoveryState !is DbRecoveryState.Loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DominoYellow)
        ) {
            if (recoveryState is DbRecoveryState.Loading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DominoGreen)
            else Text("Corrigir Senha", color = DominoGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar", color = DominoMuted) }
    }
}
