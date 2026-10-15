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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.AppState
import com.mindflow.app.data.GameMode
import com.mindflow.app.domain.DailyEngine
import com.mindflow.app.domain.LevelEngine
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
import com.mindflow.app.ui.components.StarRow
import com.mindflow.app.ui.components.StatTile
import com.mindflow.app.ui.components.TierChip
import com.mindflow.app.ui.theme.AccentLime
import com.mindflow.app.ui.theme.Background
import com.mindflow.app.ui.theme.BorderColor
import com.mindflow.app.ui.theme.PrimaryPurple
import com.mindflow.app.ui.theme.SecondaryPurple
import com.mindflow.app.ui.theme.Surface1
import com.mindflow.app.ui.theme.TextPrimary
import com.mindflow.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(900)
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Background, Surface1))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MINDFLOW", style = MaterialTheme.typography.displayLarge, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Think fast. Win louder.",
                style = MaterialTheme.typography.bodyLarge,
                color = AccentLime
            )
        }
    }
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        "Sharpen your mind" to "Race through arithmetic, logic, memory and speed challenges that make your brain fire fast.",
        "Progress you can feel" to "Watch your stats grow, see patterns in your play, and turn weak spots into strengths.",
        "Compete and climb" to "Face practice opponents, track an Elo-style rating, and climb the leaderboard."
    )
    var page by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Background)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("MINDFLOW", style = MaterialTheme.typography.labelLarge, color = PrimaryPurple)
            Spacer(Modifier.height(24.dp))
            Text(pages[page].first, style = MaterialTheme.typography.displayLarge, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            Text(pages[page].second, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier
                            .width(if (i == page) 24.dp else 8.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == page) AccentLime else BorderColor)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            MfButton(
                text = if (page < pages.lastIndex) "Next" else "Get started",
                onClick = { if (page < pages.lastIndex) page++ else onFinish() }
            )
            Spacer(Modifier.height(10.dp))
            MfOutlineButton(text = "Skip", onClick = onFinish)
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenPlay: (GameMode, Int, String?) -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDaily: () -> Unit
) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Tab("Arena", Icons.Filled.Home),
        Tab("Puzzles", Icons.Filled.Star),
        Tab("Compete", Icons.Filled.PlayArrow),
        Tab("Feed", Icons.Filled.Notifications),
        Tab("More", Icons.Filled.Person)
    )

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelLarge) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentLime,
                            selectedTextColor = AccentLime,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Surface1
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> ArenaTab(vm, state, onOpenPlay, onOpenDaily, onOpenLeaderboard, onOpenProfile)
                1 -> PuzzlesTab(state, onOpenPlay, onOpenDaily)
                2 -> CompeteTab(state, onOpenPlay, onOpenLeaderboard)
                3 -> FeedTab(state)
                else -> MoreTab(state, onOpenProfile, onOpenLeaderboard, onOpenSettings)
            }
        }
    }
}

@Composable
private fun ArenaTab(
    vm: AppViewModel,
    state: AppState,
    onOpenPlay: (GameMode, Int, String?) -> Unit,
    onOpenDaily: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val profile = state.profile
    val todayKey = Time.todayKey()
    val dailyDone = state.dailyCompletions[todayKey].orEmpty()
    val daily = DailyEngine.challenges(Time.epochDay())

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile.avatarSeed, 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(
                        "Level ${profile.playerLevel}  ·  ${profile.xp} XP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                TierChip(profile.ratings.overall)
            }
        }
        item {
            MfCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Level ${profile.playerLevel}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("${profile.xpIntoLevel}/500 XP", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(profile.xpIntoLevel / 500f)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = profile.ratings.math.toString(), label = "Math",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = profile.ratings.memory.toString(), label = "Memory",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item { SectionTitle("TODAY'S CHALLENGES") }
        item {
            MfCard(onClick = onOpenDaily) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily Challenges", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            "${dailyDone.size}/${daily.size} complete",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Text("${profile.streakDays} day streak", color = AccentLime, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(dailyDone.size.toFloat() / daily.size)
            }
        }
        item { SectionTitle("QUICK PLAY") }
        item {
            val modes = GameMode.entries.toList()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                modes.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { mode ->
                            Box(Modifier.weight(1f)) {
                                ModeCard(mode) { onOpenPlay(mode, -1, null) }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionTitle("CAMPAIGN") }
        item {
            MfCard(onClick = { onOpenPlay(LevelEngine.byId(state.highestUnlockedLevel).mode, state.highestUnlockedLevel, null) }) {
                Text("Continue Campaign", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Next up: Level ${state.highestUnlockedLevel} — ${LevelEngine.byId(state.highestUnlockedLevel).chapter}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MfOutlineButton("Leaderboard", { onOpenLeaderboard() }, Modifier.weight(1f))
                MfOutlineButton("Profile", { onOpenProfile() }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeCard(mode: GameMode, onClick: () -> Unit) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(mode.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(mode.skill.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = AccentLime)
        }
    }
}

@Composable
private fun PuzzlesTab(
    state: AppState,
    onOpenPlay: (GameMode, Int, String?) -> Unit,
    onOpenDaily: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Puzzles", style = MaterialTheme.typography.displayLarge, color = TextPrimary) }
        item {
            MfCard(onClick = onOpenDaily) {
                Text("Daily Challenges", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Five fresh challenges every day.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        LevelEngine.chapters.forEachIndexed { idx, chapter ->
            item { SectionTitle("${chapter.uppercase()}  ·  ${idx * 20 + 1}-${idx * 20 + 20}") }
            val levels = LevelEngine.levels.filter { it.chapter == chapter }
            items(levels.size) { i ->
                val level = levels[i]
                val progress = state.progressFor(level.id)
                val unlocked = level.id <= state.highestUnlockedLevel
                LevelRow(level.id, level.mode.title, progress.stars, unlocked, progress.bestScore) {
                    if (unlocked) onOpenPlay(level.mode, level.id, null)
                }
            }
        }
    }
}

@Composable
private fun LevelRow(
    id: Int,
    modeTitle: String,
    stars: Int,
    unlocked: Boolean,
    best: Int,
    onClick: () -> Unit
) {
    MfCard(onClick = { if (unlocked) onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (unlocked) "$id" else "🔒",
                style = MaterialTheme.typography.titleLarge,
                color = if (unlocked) TextPrimary else TextSecondary,
                modifier = Modifier.width(44.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(modeTitle, style = MaterialTheme.typography.titleMedium, color = if (unlocked) TextPrimary else TextSecondary)
                if (best > 0) Text("Best $best", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            StarRow(stars)
        }
    }
}

@Composable
private fun CompeteTab(
    state: AppState,
    onOpenPlay: (GameMode, Int, String?) -> Unit,
    onOpenLeaderboard: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Compete", style = MaterialTheme.typography.displayLarge, color = TextPrimary) }
        item {
            MfCard {
                Text("Practice Duel", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Race a clearly-labelled practice opponent. This is an offline bot, not a live player.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                MfButton("Start practice duel", onClick = { onOpenPlay(GameMode.MATH_SPRINT, -1, null) })
            }
        }
        item {
            MfCard {
                Text("Online Duels", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Live matchmaking, private rooms and friend invites require a configured realtime backend (see README). Not enabled in this build.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                MfOutlineButton("Unavailable — backend not configured", {}, enabled = false)
            }
        }
        item { SectionTitle("YOUR RANKING") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("${state.profile.wins}", "Wins", Modifier.weight(1f))
                StatTile("${state.profile.losses}", "Losses", Modifier.weight(1f))
            }
        }
        item {
            MfCard(onClick = onOpenLeaderboard) {
                Text("View leaderboard", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Friends · Community · Global", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun FeedTab(state: AppState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Feed", style = MaterialTheme.typography.displayLarge, color = TextPrimary) }
        item { SectionTitle("ACHIEVEMENTS (${state.unlockedAchievements.size})") }
        item {
            MfCard {
                com.mindflow.app.domain.Achievements.all.forEach { a ->
                    val got = a.id in state.unlockedAchievements
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (got) "★" else "☆", color = if (got) AccentLime else BorderColor, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(a.title, style = MaterialTheme.typography.titleMedium, color = if (got) TextPrimary else TextSecondary)
                            Text(a.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
            }
        }
        item { SectionTitle("RECENT ACTIVITY") }
        if (state.results.isEmpty()) {
            item { EmptyState("No games yet", "Play a mode to see your activity here.") }
        } else {
            items(state.results.takeLast(12).reversed().size) { i ->
                val r = state.results.takeLast(12).reversed()[i]
                MfCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.mode.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "${r.correct}/${r.total} correct · ${(r.accuracy * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Text("+${r.xp} XP", color = AccentLime, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreTab(
    state: AppState,
    onOpenProfile: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("More", style = MaterialTheme.typography.displayLarge, color = TextPrimary) }
        item {
            MfCard(onClick = onOpenProfile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(state.profile.avatarSeed, 48)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.profile.displayName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(RatingEngine.tier(state.profile.ratings.overall), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
        item { MfCard(onClick = onOpenLeaderboard) { Text("Leaderboards", style = MaterialTheme.typography.titleMedium, color = TextPrimary) } }
        item { MfCard(onClick = onOpenSettings) { Text("Settings", style = MaterialTheme.typography.titleMedium, color = TextPrimary) } }
        item {
            MfCard {
                Text("About Mindflow", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Offline-first brain training. Your progress is stored on this device. Online accounts, cloud sync and live duels need an external backend and are not enabled in this build.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
