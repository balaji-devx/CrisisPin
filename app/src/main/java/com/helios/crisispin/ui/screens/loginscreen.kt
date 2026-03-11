package com.helios.crisispin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.crisispin.ui.theme.*
import com.helios.crisispin.ui.components.CpLogo

private enum class AuthMode { LOGIN, REGISTER }

private data class RoleOption(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
)

private val roleOptions = listOf(
    RoleOption(
        id          = "user",
        label       = "User",
        description = "Receive, send & relay emergency alerts",
        icon        = Icons.Rounded.Person,
        accentColor = Color(0xFF42A5F5)
    ),
    RoleOption(
        id          = "authority",
        label       = "Authority",
        description = "Coordinate response, view all alerts",
        icon        = Icons.Rounded.Security,
        accentColor = Color(0xFFFFB300)
    ),
    RoleOption(
        id          = "admin",
        label       = "Admin",
        description = "Manage users & monitor all events",
        icon        = Icons.Rounded.ManageAccounts,
        accentColor = Color(0xFFAB47BC)
    ),
)

@Composable
fun LoginScreen(onLoginSuccess: (role: String) -> Unit) {
    var mode         by remember { mutableStateOf(AuthMode.LOGIN) }
    var email        by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var confirmPass  by remember { mutableStateOf("") }
    var name         by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("user") }
    var showPass     by remember { mutableStateOf(false) }
    var showConfirm  by remember { mutableStateOf(false) }
    var error        by remember { mutableStateOf("") }
    var success      by remember { mutableStateOf("") }
    var loading      by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NavyLight, DarkNavy, Color(0xFF080F18))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(36.dp))

            // ── Logo & Title ──────────────────────────────────────────────
            CpLogo(size = 68.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "CrisisPin",
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp,
                letterSpacing = (-0.5).sp
            )
            Text(
                "DECENTRALIZED SAFETY NETWORK",
                color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(36.dp))

            // ── Login / Register Tab ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AuthMode.values().forEach { m ->
                    val active = mode == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active)
                                    Brush.horizontalGradient(listOf(EmergencyRed, EmergencyRedDark))
                                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable { mode = m; error = ""; success = "" }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (m == AuthMode.LOGIN) "Sign In" else "Create Account",
                            color = if (active) Color.White else TextMuted,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Google Sign-In Button ─────────────────────────────────────
            // NOTE: Requires Google Sign-In dependency in build.gradle:
            // implementation 'com.google.android.gms:play-services-auth:21.0.0'
            // Wire up GoogleSignInClient in MainActivity and pass callback here.
            OutlinedButton(
                onClick = {
                    // TODO: Call onGoogleSignIn() — wire in MainActivity
                    // GoogleSignIn.getClient(context, gso).signIn()
                    error = "Google Sign-In: wire up in MainActivity with play-services-auth"
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SurfaceCard,
                    contentColor = Color.White
                )
            ) {
                // Google "G" drawn as a coloured circle with letter — no external drawable needed
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "G",
                        color = Color(0xFF4285F4),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        lineHeight = 13.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (mode == AuthMode.LOGIN) "Continue with Google" else "Sign up with Google",
                    fontWeight = FontWeight.Medium, fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Divider ───────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Divider(modifier = Modifier.weight(1f), color = SurfaceElevated)
                Text(
                    "  OR  ",
                    color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium
                )
                Divider(modifier = Modifier.weight(1f), color = SurfaceElevated)
            }

            Spacer(Modifier.height(20.dp))

            // ── Register: Full Name ───────────────────────────────────────
            AnimatedVisibility(visible = mode == AuthMode.REGISTER, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    CpTextField(
                        value = name,
                        onValueChange = { name = it; error = "" },
                        label = "Full Name",
                        icon = Icons.Rounded.Person,
                        imeAction = ImeAction.Next,
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            // ── Email ─────────────────────────────────────────────────────
            CpTextField(
                value = email,
                onValueChange = { email = it; error = "" },
                label = "Email Address",
                icon = Icons.Rounded.Email,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
            Spacer(Modifier.height(14.dp))

            // ── Password ──────────────────────────────────────────────────
            CpTextField(
                value = password,
                onValueChange = { password = it; error = "" },
                label = "Password",
                icon = Icons.Rounded.Lock,
                keyboardType = KeyboardType.Password,
                imeAction = if (mode == AuthMode.REGISTER) ImeAction.Next else ImeAction.Done,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { focusManager.clearFocus() },
                visualTransformation = if (showPass) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            // ── Register: Confirm Password ────────────────────────────────
            AnimatedVisibility(visible = mode == AuthMode.REGISTER, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    CpTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it; error = "" },
                        label = "Confirm Password",
                        icon = Icons.Rounded.LockOpen,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onDone = { focusManager.clearFocus() },
                        visualTransformation = if (showConfirm) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showConfirm = !showConfirm }) {
                                Icon(
                                    if (showConfirm) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )
                }
            }

            // ── Forgot Password (login only) ──────────────────────────────
            AnimatedVisibility(visible = mode == AuthMode.LOGIN, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        "Forgot Password?",
                        color = EmergencyRed.copy(0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            if (email.isBlank()) {
                                error = "Enter your email first"
                            } else {
                                // TODO: send password reset email via backend
                                success = "Password reset link sent to $email"
                                error = ""
                            }
                        }
                    )
                }
            }

            // ── Register: Role Selector ───────────────────────────────────
            AnimatedVisibility(visible = mode == AuthMode.REGISTER, enter = fadeIn(), exit = fadeOut()) {
                Column(modifier = Modifier.animateContentSize()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "SELECT YOUR ROLE",
                        color = TextMuted, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    roleOptions.forEach { role ->
                        RoleCard(
                            role = role,
                            selected = selectedRole == role.id,
                            onSelect = { selectedRole = role.id }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    // Admin warning
                    AnimatedVisibility(selectedRole == "admin") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFAB47BC).copy(0.1f))
                                .border(1.dp, Color(0xFFAB47BC).copy(0.3f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Info, null,
                                tint = Color(0xFFAB47BC), modifier = Modifier.size(16.dp))
                            Text(
                                "Admin accounts require manual approval from an existing admin.",
                                color = Color(0xFFAB47BC).copy(0.9f), fontSize = 12.sp, lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // ── Error / Success Messages ──────────────────────────────────
            AnimatedVisibility(error.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmergencyRed.copy(0.12f))
                            .border(1.dp, EmergencyRed.copy(0.3f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = EmergencyRed, modifier = Modifier.size(16.dp))
                        Text(error, color = EmergencyRed, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
            AnimatedVisibility(success.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ActiveGreen.copy(0.1f))
                            .border(1.dp, ActiveGreen.copy(0.4f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = ActiveGreen, modifier = Modifier.size(16.dp))
                        Text(success, color = ActiveGreen, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Primary Action Button ─────────────────────────────────────
            Button(
                onClick = {
                    error = ""; success = ""
                    when {
                        mode == AuthMode.REGISTER && name.isBlank() ->
                            error = "Please enter your full name"
                        email.isBlank() ->
                            error = "Email address is required"
                        !email.contains("@") ->
                            error = "Enter a valid email address"
                        password.length < 6 ->
                            error = "Password must be at least 6 characters"
                        mode == AuthMode.REGISTER && password != confirmPass ->
                            error = "Passwords do not match"
                        else -> {
                            loading = true
                            // ─── Backend auth hook ─────────────────────────
                            // LOGIN:    POST /api/auth/login    { email, password }
                            // REGISTER: POST /api/auth/register { name, email, password, role }
                            // On success: store JWT token, call onLoginSuccess(role)
                            // ─────────────────────────────────────────────
                            val role = if (mode == AuthMode.REGISTER) selectedRole else "user"
                            onLoginSuccess(role)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        if (mode == AuthMode.LOGIN) Icons.Rounded.Login else Icons.Rounded.PersonAdd,
                        contentDescription = null, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (mode == AuthMode.LOGIN) "Sign In with Email" else "Create Account",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Bottom hint ───────────────────────────────────────────────
            Text(
                if (mode == AuthMode.LOGIN)
                    "Don't have an account? Tap \"Create Account\" above."
                else
                    "Already have an account? Tap \"Sign In\" above.",
                color = TextMuted, fontSize = 12.sp,
                textAlign = TextAlign.Center, lineHeight = 17.sp
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── Role Selection Card ────────────────────────────────────────────────────────

@Composable
private fun RoleCard(role: RoleOption, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) role.accentColor.copy(0.1f) else SurfaceCard
            )
            .border(
                width = 1.5.dp,
                color = if (selected) role.accentColor.copy(0.6f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(role.accentColor.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(role.icon, null, tint = role.accentColor, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(role.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(role.description, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (selected) role.accentColor else SurfaceElevated
                )
                .border(
                    width = if (selected) 0.dp else 1.5.dp,
                    color = TextMuted.copy(0.4f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

// ── Reusable Text Field ────────────────────────────────────────────────────────

@Composable
private fun CpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        },
        trailingIcon = trailingIcon,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() }
        ),
        visualTransformation = visualTransformation,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = EmergencyRed,
            unfocusedBorderColor    = SurfaceElevated,
            focusedLabelColor       = EmergencyRed,
            unfocusedLabelColor     = TextMuted,
            focusedTextColor        = Color.White,
            unfocusedTextColor      = Color(0xFFCDD5E0),
            cursorColor             = EmergencyRed,
            focusedContainerColor   = SurfaceCard,
            unfocusedContainerColor = SurfaceCard
        )
    )
}