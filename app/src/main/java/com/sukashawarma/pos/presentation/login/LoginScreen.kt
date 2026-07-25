package com.sukashawarma.pos.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sukashawarma.pos.domain.model.UserSession
import com.sukashawarma.pos.presentation.theme.*

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (UserSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val username by viewModel.usernameInput.collectAsState()
    val password by viewModel.passwordInput.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = SlateSurface,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Branding
                Text(
                    text = "SUKA SHAWARMA",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary
                )
                Text(
                    text = "Login Kasir",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = SlateBorder)
                Spacer(modifier = Modifier.height(20.dp))

                // Error Banner
                errorMessage?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = StatusPending.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusPending)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = StatusPending)
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusPending,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 1. Username Input
                OutlinedTextField(
                    value = username,
                    onValueChange = { viewModel.usernameInput.value = it },
                    label = { Text("Username Staff") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Password Input
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.passwordInput.value = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Submit Login Button
                Button(
                    onClick = { viewModel.login(onLoginSuccess) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = SlateBackground, modifier = Modifier.size(24.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SlateBackground)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MASUK",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SlateBackground
                            )
                        }
                    }
                }
            }
        }
    }
}
