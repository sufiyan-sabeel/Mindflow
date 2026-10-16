package com.mindflow.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.AppState
import com.mindflow.app.data.GameMode
import com.mindflow.app.domain.DailyEngine
import com.mindflow.app.domain.RatingEngine
import com.mindflow.app.domain.Time
import com.mindflow.app.ui.AppViewModel
import com.mindflow.app.ui.components.Avatar
import com.mindflow.app.ui.components.EmptyState
import com.mindflow.app.ui.components.MfButton
import com.mindflow.app.ui.components.MfCard
import com.mindflow.app.ui.components.MfOutlineButton
import com.mindflow.app.ui.components.ProgressBar
import com.mindflow.app.ui.components.SectionTitle
import com.mindflow.app.ui.components.StatTile
import com.mindflow.app.ui.components.TierChip
import com.mindflow.app.ui.theme.AccentLime
import com.mindflow.app.ui.theme.Background
import com.mindflow.app.ui.theme.BorderColor
import com.mindflow.app.ui.theme.PrimaryPurple
import com.mindflow.app.ui.theme.Surface1
import com.mindflow.app.ui.theme.Surface2
import com.mindflow.app.ui.theme.TextPrimary
import com.mindflow.app.ui.theme.TextSecondary

@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Surface2,
            shape = CircleShape,
            modifier = Modifier.size(40.dp).clickable { onBack() },
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
        ) {
            Box(contentAlignment = Alignment.Center) { Text("←", color = TextPrimary) }
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
    }
}

private data class LbEntry(val rank: Int, val name: String, val score: Int, val seed: Int, val isPlayer: Boolean)

@Composable
fun LeaderboardScreen(vm: AppViewModel, state: AppState, onBack: () -> Unit) {
    val cloud by vm.cloudState.collectAsState()
    val live by vm.leaderboard.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var period by remember { mutableIntStateOf(0) }
    val tabs = listOf("Friends", "Community", "Global")
    val scopes = listOf("friends", "community", "global")
    val periods = listOf("All-time", "Weekly")

    LaunchedEffect(tab, period, cloud.signedIn) {
        if (cloud.signedIn) {
            vm.loadLeaderboard(scopes[tab], "score", "overall", if (period == 1) "weekly" else "all_time")
        }
    }

    val liveRows = live
    val showingLive = cloud.signedIn && liveRows != null

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize()) {
            BackBar("Leaderboard", onBack)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { i, t ->
                    Surface(
                        color = if (tab == i) Surface2 else Surface1,
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (tab == i) AccentLime else BorderColor),
                        modifier = Modifier.weight(1f).clickable { tab = i }
                    ) {
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(t, color = if (tab == i) AccentLime else TextSecondary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (showingLive) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    periods.forEachIndexed { i, p ->
                        Surface(
                            color = if (period == i) Surface2 else Surface1,
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (period == i) AccentLime else BorderColor),
                            modifier = Modifier.clickable { period = i }
                        ) {
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(p, color = if (period == i) AccentLime else TextSecondary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Live — real registered players only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentLime,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                val rows = liveRows ?: emptyList()
                if (rows.isEmpty()) {
                    EmptyState(
                        "No players yet",
                        "Be the first to post a score in this board. Play a game while signed in and sync."
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rows.size) { i ->
                            val row = rows[i]
                            MfCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${row.rank}", style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.width(36.dp))
                                    Avatar(row.avatar_seed, 36)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(row.display_name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        Text(row.username, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    }
                                    Text("${row.score}", style = MaterialTheme.typography.titleMedium, color = PrimaryPurple)
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    "Preview data — sample opponents shown for development. Sign in to see live rankings of real players.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                val playerRating = state.profile.ratings.overall
                val sample = listOf(
                    LbEntry(1, "A. Nair", 1642, 3, false),
                    LbEntry(2, "J. Kumar", 1591, 5, false),
                    LbEntry(3, "L. Vats", 1520, 1, false),
                    LbEntry(4, "K. Shaikh", 1487, 4, false),
                    LbEntry(5, "S. Rao", 1410, 2, false)
                )
                val entries = (sample + LbEntry(0, state.profile.displayName + " (you)", playerRating, state.profile.avatarSeed, true))
                    .sortedByDescending { it.score }
                    .mapIndexed { i, e -> e.copy(rank = i + 1) }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries.size) { i ->
                        val e = entries[i]
                        MfCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${e.rank}", style = MaterialTheme.typography.titleLarge, color = if (e.isPlayer) AccentLime else TextPrimary, modifier = Modifier.width(36.dp))
                                Avatar(e.seed, 36)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(RatingEngine.tier(e.score), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                Text("${e.score}", style = MaterialTheme.typography.titleMedium, color = PrimaryPurple)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel, state: AppState, onBack: () -> Unit) {
    val p = state.profile
    var name by remember { mutableStateOf(p.displayName) }
    val history = p.ratingHistory

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize()) {
            BackBar("Profile", onBack)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.avatarSeed, 64)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.displayName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Text("Level ${p.playerLevel} · ${p.xp} XP", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        TierChip(p.ratings.overall)
                    }
                }
                item {
                    MfCard {
                        Text("Display name", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(24) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MfButton("Save", { vm.setDisplayName(name) }, Modifier.weight(1f))
                            MfOutlineButton("New avatar", { vm.setAvatarSeed(p.avatarSeed + 1) }, Modifier.weight(1f))
                        }
                    }
                }
                item { SectionTitle("STATS OVERVIEW") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("${p.gamesPlayed}", "Games", Modifier.weight(1f))
                        StatTile("${p.bestStreak}", "Best streak", Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("${p.wins}", "Wins", Modifier.weight(1f))
                        StatTile("${p.losses}", "Losses", Modifier.weight(1f))
                    }
                }
                item { SectionTitle("RATINGS") }
                item {
                    MfCard {
                        RatingLine("Math", p.ratings.math)
                        RatingLine("Logic", p.ratings.logic)
                        RatingLine("Memory", p.ratings.memory)
                        RatingLine("Puzzle", p.ratings.puzzle)
                    }
                }
                item { SectionTitle("RATING TREND") }
                item {
                    MfCard {
                        if (history.size < 2) {
                            EmptyState("Not enough data", "Play a few games to see your rating trend.")
                        } else {
                            RatingChart(history.map { it.overall })
                        }
                    }
                }
                item { SectionTitle("RECENT MATCHES") }
                if (state.results.isEmpty()) {
                    item { EmptyState("No matches yet", "Your finished games will appear here.") }
                } else {
                    items(state.results.takeLast(10).reversed().size) { i ->
                        val r = state.results.takeLast(10).reversed()[i]
                        MfCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.mode.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("${r.correct}/${r.total} · ${(r.accuracy * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                Text("+${r.xp} XP", color = AccentLime, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingLine(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, modifier = Modifier.weight(1f))
        Text("$value", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
private fun RatingChart(values: List<Int>) {
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        var prev: Offset? = null
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = size.height - ((v - minV).toFloat() / range) * size.height
            val cur = Offset(x, y)
            prev?.let { p -> drawLine(color = AccentLime, start = p, end = cur, strokeWidth = 4f, cap = StrokeCap.Round) }
            prev = cur
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("min $minV", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text("now ${values.last()}", style = MaterialTheme.typography.bodyMedium, color = AccentLime)
        Text("max $maxV", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, state: AppState, onBack: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize()) {
            BackBar("Settings", onBack)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { SectionTitle("FEEDBACK") }
                item { ToggleRow("Sound effects", state.prefs.soundOn) { vm.updatePrefs(state.prefs.copy(soundOn = it)) } }
                item { ToggleRow("Haptic feedback", state.prefs.hapticsOn) { vm.updatePrefs(state.prefs.copy(hapticsOn = it)) } }
                item { SectionTitle("ACCESSIBILITY") }
                item { ToggleRow("Reduced motion", state.prefs.reducedMotion) { vm.updatePrefs(state.prefs.copy(reducedMotion = it)) } }
                item { ToggleRow("High contrast", state.prefs.highContrast) { vm.updatePrefs(state.prefs.copy(highContrast = it)) } }
                item { SectionTitle("DATA") }
                item {
                    MfCard {
                        Text("Progress is stored only on this device.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        if (!confirmReset) {
                            MfOutlineButton("Reset all progress", { confirmReset = true })
                        } else {
                            Text("This permanently deletes all local progress.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MfButton("Confirm reset", { vm.resetAll(); confirmReset = false }, Modifier.weight(1f))
                                MfOutlineButton("Cancel", { confirmReset = false }, Modifier.weight(1f))
                            }
                        }
                    }
                }
                item { SectionTitle("ABOUT") }
                item {
                    MfCard {
                        Text("Mindflow 1.0.0", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            "Offline-first brain training. Online features (accounts, cloud sync, live duels) require an external backend and are not enabled in this build.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
fun DailyChallengesScreen(state: AppState, onPlay: (GameMode, String) -> Unit, onBack: () -> Unit) {
    val challenges = DailyEngine.challenges(Time.epochDay())
    val done = state.dailyCompletions[Time.todayKey()].orEmpty()

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize()) {
            BackBar("Daily Challenges", onBack)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MfCard {
                        Text("${done.size}/${challenges.size} complete today", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(done.size.toFloat() / challenges.size)
                        Spacer(Modifier.height(8.dp))
                        Text("${state.profile.streakDays} day streak", color = AccentLime, style = MaterialTheme.typography.labelLarge)
                    }
                }
                items(challenges.size) { i ->
                    val c = challenges[i]
                    val complete = c.id in done
                    MfCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(c.mode.title, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            if (complete) {
                                Text("Done ✓", color = AccentLime, style = MaterialTheme.typography.labelLarge)
                            } else {
                                MfOutlineButton("Play", { onPlay(c.mode, c.id) }, Modifier.width(96.dp))
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Daily challenges use a deterministic seed for the day, so the same puzzles are offered until the next reset.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
