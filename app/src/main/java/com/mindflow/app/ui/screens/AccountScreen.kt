package com.mindflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.AppState
import com.mindflow.app.ui.AppViewModel
import com.mindflow.app.ui.components.MfButton
import com.mindflow.app.ui.components.MfCard
import com.mindflow.app.ui.components.MfOutlineButton
import com.mindflow.app.ui.components.SectionTitle
import com.mindflow.app.ui.theme.AccentLime
import com.mindflow.app.ui.theme.Background
import com.mindflow.app.ui.theme.BorderColor
import com.mindflow.app.ui.theme.ErrorRed
import com.mindflow.app.ui.theme.Surface2
import com.mindflow.app.ui.theme.TextPrimary
import com.mindflow.app.ui.theme.TextSecondary

/**
 * Account & cloud. Sign in / sign up / guest, sync status, and sign-out.
 * When the build has no Supabase configuration, this screen explains what to set
 * rather than presenting controls that cannot work.
 */
@Composable
fun AccountScreen(vm: AppViewModel, state: AppState, onBack: () -> Unit) {
    val cloud by vm.cloudState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(state.profile.displayName) }
    var mode by remember { mutableStateOf("signin") } // signin | signup

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Surface2, shape = CircleShape,
                    modifier = Modifier.size(40.dp).clickable { onBack() },
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                ) { Box(contentAlignment = Alignment.Center) { Text("←", color = TextPrimary) } }
                Spacer(Modifier.width(12.dp))
                Text("Account & Cloud", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MfCard {
                        Text(
                            if (cloud.signedIn) "Signed in" else "Not signed in",
                            style = MaterialTheme.typography.titleMedium, color = TextPrimary
                        )
                        Text(
                            when {
                                !cloud.configured -> "This build has no Supabase configuration, so online features are off. Single-player still works fully."
                                cloud.signedIn && cloud.isGuest -> "Guest session — progress syncs, but sign in with an email to keep it on other devices."
                                cloud.signedIn -> cloud.email ?: "Signed in"
                                else -> "Sign in to sync progress across devices, appear on real leaderboards, and play online duels."
                            },
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                        )
                        if (cloud.configured) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "${cloud.pendingUploads} result(s) waiting to sync",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (cloud.pendingUploads == 0) AccentLime else TextSecondary
                            )
                        }
                        cloud.message?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }

                if (!cloud.configured) {
                    item { SectionTitle("HOW TO ENABLE") }
                    item {
                        MfCard {
                            Text(
                                "Build with -Pmindflow.supabaseUrl and -Pmindflow.supabaseAnonKey (see SUPABASE_SETUP.md). " +
                                    "Only the publishable key is used; the service-role key must never ship in the app.",
                                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                            )
                        }
                    }
                }

                if (cloud.configured && !cloud.signedIn) {
                    item { SectionTitle("SIGN IN") }
                    item {
                        MfCard {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it.trim() },
                                label = { Text("Email") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (mode == "signup") {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = displayName,
                                    onValueChange = { displayName = it.take(24) },
                                    label = { Text("Display name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            MfButton(
                                text = if (mode == "signin") "Sign in" else "Create account",
                                enabled = email.isNotBlank() && password.length >= 6 && !cloud.busy,
                                onClick = {
                                    if (mode == "signin") vm.signIn(email, password)
                                    else vm.signUp(email, password, displayName)
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            MfOutlineButton(
                                text = if (mode == "signin") "New here? Create an account" else "Have an account? Sign in",
                                onClick = { mode = if (mode == "signin") "signup" else "signin" }
                            )
                            Spacer(Modifier.height(8.dp))
                            MfOutlineButton(
                                text = "Continue as guest",
                                enabled = !cloud.busy,
                                onClick = { vm.continueAsGuest() }
                            )
                            if (password.isNotEmpty() && password.length < 6) {
                                Spacer(Modifier.height(8.dp))
                                Text("Password must be at least 6 characters.", color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (cloud.signedIn) {
                    item { SectionTitle("SYNC") }
                    item {
                        MfCard {
                            Text(
                                "Sync pushes any unsynced results, claims today's daily challenges, then restores your server snapshot.",
                                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            MfButton("Sync now", { vm.syncNow() }, enabled = !cloud.busy)
                            Spacer(Modifier.height(8.dp))
                            MfOutlineButton("Sign out", { vm.signOutUser() })
                        }
                    }
                    item { SectionTitle("COMMUNITY") }
                    item {
                        MfCard {
                            Text(
                                "Set a community/college tag to appear on the community leaderboard.",
                                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                            )
                            Spacer(Modifier.height(10.dp))
                            CommunityField(vm = vm, state = state)
                        }
                    }
                }

                item {
                    MfCard {
                        Text("Privacy", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            "Only your display name, avatar seed, ratings and scores are stored server-side. " +
                                "Row Level Security means other players cannot read or change anything beyond that.",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityField(vm: AppViewModel, state: AppState) {
    var community by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = community,
            onValueChange = { community = it.take(40) },
            label = { Text("Community / college") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        MfButton("Save", {
            vm.setCommunity(community)
        }, Modifier.width(96.dp))
    }
}
