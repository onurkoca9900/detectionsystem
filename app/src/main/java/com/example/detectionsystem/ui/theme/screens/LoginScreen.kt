package com.example.detectionsystem.ui.theme.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

// ==========================================
// 1. GİRİŞ EKRANI (LOGIN)
// ==========================================
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(AuthConstants.BgColor).padding(AuthConstants.PaddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuthHeader(title = AuthConstants.AppTitle, subtitle = AuthConstants.AppSubtitle)

        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "E-Posta",
            icon = Icons.Default.Email,
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(AuthConstants.SpacingMedium))

        AuthTextField(
            value = password,
            onValueChange = { password = it },
            label = "Şifre",
            icon = Icons.Default.Lock,
            isPassword = true
        )

        Spacer(modifier = Modifier.height(AuthConstants.SpacingLarge))

        AuthButton(
            text = "Giriş Yap",
            isLoading = isLoading,
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    isLoading = true
                    auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                        isLoading = false
                        if (task.isSuccessful) {
                            Toast.makeText(context, "Hoş Geldiniz!", Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                        } else {
                            Toast.makeText(context, "Hata: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
                }
            }
        )

        Spacer(modifier = Modifier.height(AuthConstants.SpacingMedium))

        TextButton(onClick = onNavigateToSignUp) {
            Text("Hesabın yok mu? Hemen Kayıt Ol", color = Color.LightGray)
        }
    }
}

// ==========================================
// 2. YENİ KAYIT OL EKRANI (SIGN UP)
// ==========================================
@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(AuthConstants.BgColor).padding(AuthConstants.PaddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White) }
        }

        AuthHeader(title = "Yeni Hesap Oluştur", subtitle = "Bilgilerinizi girerek sisteme katılın")

        AuthTextField(value = email, onValueChange = { email = it }, label = "E-Posta", icon = Icons.Default.Email, keyboardType = KeyboardType.Email)
        Spacer(modifier = Modifier.height(AuthConstants.SpacingMedium))

        AuthTextField(value = password, onValueChange = { password = it }, label = "Şifre", icon = Icons.Default.Lock, isPassword = true)
        Spacer(modifier = Modifier.height(AuthConstants.SpacingMedium))

        AuthTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = "Şifre (Tekrar)", icon = Icons.Default.Lock, isPassword = true)
        Spacer(modifier = Modifier.height(AuthConstants.SpacingLarge))

        AuthButton(
            text = "Kayıt Ol",
            isLoading = isLoading,
            onClick = {
                when {
                    email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> Toast.makeText(context, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
                    password != confirmPassword -> Toast.makeText(context, "Şifreler uyuşmuyor!", Toast.LENGTH_SHORT).show()
                    password.length < 6 -> Toast.makeText(context, "Şifre en az 6 karakter olmalı", Toast.LENGTH_SHORT).show()
                    else -> {
                        isLoading = true
                        auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Kayıt Başarılı! İçeri alınıyorsunuz.", Toast.LENGTH_SHORT).show()
                                onSignUpSuccess()
                            } else {
                                Toast.makeText(context, "Hata: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
    }
}

// ==========================================
// 3. ORTAK UI BİLEŞENLERİ (Clean Code - DRY)
// ==========================================

@Composable
private fun AuthHeader(title: String, subtitle: String) {
    Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
    Text(subtitle, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = AuthConstants.PaddingLarge))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (isPassword) {
            {
                val visibilityIcon = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(visibilityIcon, contentDescription = "Şifreyi Göster") }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AuthConstants.AccentColor,
            focusedLabelColor = AuthConstants.AccentColor,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}

@Composable
private fun AuthButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(55.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AuthConstants.AccentColor),
        shape = RoundedCornerShape(16.dp),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Text(text = text, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 4. SABİTLER (Magic Numbers)
// ==========================================
private object AuthConstants {
    const val AppTitle = "Smart DMS"
    const val AppSubtitle = "Yapay Zeka Destekli Sürüş Asistanı"

    val BgColor = Color(0xFF121212)
    val AccentColor = Color(0xFF00E676)

    val PaddingLarge = 24.dp
    val SpacingMedium = 16.dp
    val SpacingLarge = 32.dp
}
