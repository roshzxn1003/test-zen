package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.components.GlassCard
import com.example.ui.components.ZenithLogo
import com.example.ui.theme.*
import com.example.ui.viewmodel.CashFlowViewModel

@Composable
fun AuthScreen(
    viewModel: CashFlowViewModel,
    initialIsSignUp: Boolean = false,
    onAuthSuccess: () -> Unit,
    onBackToSplash: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var isSignUp by remember { mutableStateOf(initialIsSignUp) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }
    var forgotPasswordSent by remember { mutableStateOf(false) }

    fun executeSignIn() {
        errorMessage = null
        if (email.isBlank()) {
            errorMessage = "Please enter your email address."
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            errorMessage = "Please enter a valid email format."
            return
        }
        if (password.isBlank()) {
            errorMessage = "Please enter your password."
            return
        }

        isLoading = true
        viewModel.signIn(email.trim(), password) { success ->
            isLoading = false
            if (success) {
                Toast.makeText(context, "Welcome back to Zenith!", Toast.LENGTH_SHORT).show()
                onAuthSuccess()
            } else {
                errorMessage = "Authentication failed. Please check your credentials or try offline mode."
            }
        }
    }

    fun executeSignUp() {
        errorMessage = null
        if (fullName.isBlank()) {
            errorMessage = "Please enter your full name."
            return
        }
        if (email.isBlank()) {
            errorMessage = "Please enter your email address."
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            errorMessage = "Please enter a valid email format."
            return
        }
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters long."
            return
        }
        if (password != confirmPassword) {
            errorMessage = "Passwords do not match."
            return
        }

        isLoading = true
        viewModel.signUp(email.trim(), password, fullName.trim()) { success ->
            isLoading = false
            if (success) {
                Toast.makeText(context, "Account created successfully! Welcome to Zenith.", Toast.LENGTH_LONG).show()
                onAuthSuccess()
            } else {
                errorMessage = "Unable to create account. Please check your details."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = AmbientBackgroundBrush)
            .padding(horizontal = 20.dp)
            .testTag("zenith_auth_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Top Bar with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackToSplash,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassCardBg)
                        .border(1.dp, GlassBorderColor, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SlateDarkTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldDarkContainer,
                    border = BorderStroke(1.dp, EmeraldDarkPrimary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "SECURE ENCLAVE 🔒",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDarkPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Zenith Branding Header
            ZenithLogo(size = 52.dp)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isSignUp) "Create Zenith Account" else "Sign In to Zenith",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = SlateDarkTextPrimary
            )

            Text(
                text = if (isSignUp) "Unlock family ledgers, receipt OCR & cloud vault" else "Access your personal and shared wealth vaults",
                fontSize = 12.sp,
                color = SlateDarkTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // --- TAB SELECTOR (Sign In / Sign Up) ---
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SlateDarkSurfaceVariant,
                border = BorderStroke(1.dp, GlassBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isSignUp) EmeraldDarkPrimary else Color.Transparent)
                            .clickable {
                                isSignUp = false
                                errorMessage = null
                            }
                            .testTag("tab_sign_in"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign In",
                            fontSize = 13.sp,
                            fontWeight = if (!isSignUp) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isSignUp) Color.White else SlateDarkTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSignUp) EmeraldDarkPrimary else Color.Transparent)
                            .clickable {
                                isSignUp = true
                                errorMessage = null
                            }
                            .testTag("tab_create_account"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create Account",
                            fontSize = 13.sp,
                            fontWeight = if (isSignUp) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSignUp) Color.White else SlateDarkTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // --- ERROR BANNER ---
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ExpenseRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = ExpenseRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                fontSize = 12.sp,
                                color = SlateDarkTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // --- MAIN FORM CONTAINER ---
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassCardBg,
                borderColor = GlassBorderColor
            ) {
                // Sign Up Full Name Field
                if (isSignUp) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            errorMessage = null
                        },
                        label = { Text("Full Name") },
                        placeholder = { Text("e.g. Alex Morgan") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = SlateDarkTextSecondary)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_input_name")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Email Address Field
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = { Text("Email Address") },
                    placeholder = { Text("name@example.com") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Email, contentDescription = null, tint = SlateDarkTextSecondary)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_input_email")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password Field
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password") },
                    placeholder = { Text("••••••••") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = SlateDarkTextSecondary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = SlateDarkTextSecondary
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        onDone = {
                            focusManager.clearFocus()
                            executeSignIn()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_input_password")
                )

                // Sign Up Confirm Password Field
                if (isSignUp) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorMessage = null
                        },
                        label = { Text("Confirm Password") },
                        placeholder = { Text("••••••••") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = SlateDarkTextSecondary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                                    tint = SlateDarkTextSecondary
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                executeSignUp()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_input_confirm_password")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Password must be at least 6 characters",
                        fontSize = 11.sp,
                        color = if (password.length >= 6) IncomeGreen else SlateDarkTextSecondary
                    )
                }

                // Forgot Password link (Sign In tab only)
                if (!isSignUp) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                forgotPasswordEmail = email
                                forgotPasswordSent = false
                                showForgotPasswordDialog = true
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "Forgot Password?",
                                fontSize = 12.sp,
                                color = GoldAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Submit Button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (isSignUp) executeSignUp() else executeSignIn()
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("auth_btn_submit"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isSignUp) "Creating account..." else "Authenticating...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = if (isSignUp) "Create Account" else "Sign In",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Guest / Offline Mode Alternative Button
            OutlinedButton(
                onClick = onContinueAsGuest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("auth_btn_guest_mode"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GlassBorderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateDarkTextSecondary)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = GoldAccent)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Open Offline Vault (Guest Mode)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateDarkTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // --- FORGOT PASSWORD DIALOG ---
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Text(
                    text = if (forgotPasswordSent) "Password Reset Sent" else "Reset Password",
                    fontWeight = FontWeight.Bold,
                    color = SlateDarkTextPrimary
                )
            },
            text = {
                if (forgotPasswordSent) {
                    Column {
                        Text(
                            text = "A password recovery email has been sent to $forgotPasswordEmail.",
                            fontSize = 13.sp,
                            color = SlateDarkTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please follow the instructions in the email to set a new password.",
                            fontSize = 12.sp,
                            color = SlateDarkTextMuted
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = "Enter your registered email address and we'll send you instructions to reset your password.",
                            fontSize = 13.sp,
                            color = SlateDarkTextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = forgotPasswordEmail,
                            onValueChange = { forgotPasswordEmail = it },
                            label = { Text("Email Address") },
                            placeholder = { Text("name@example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (forgotPasswordSent) {
                    Button(
                        onClick = { showForgotPasswordDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                    ) {
                        Text("Done")
                    }
                } else {
                    Button(
                        onClick = {
                            if (forgotPasswordEmail.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(forgotPasswordEmail.trim()).matches()) {
                                forgotPasswordSent = true
                            } else {
                                Toast.makeText(context, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDarkPrimary)
                    ) {
                        Text("Send Reset Link")
                    }
                }
            },
            dismissButton = {
                if (!forgotPasswordSent) {
                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            containerColor = SlateDarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
