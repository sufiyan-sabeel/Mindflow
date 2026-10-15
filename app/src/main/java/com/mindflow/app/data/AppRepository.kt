package com.mindflow.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.mindflowDataStore: DataStore<Preferences> by preferencesDataStore(name = "mindflow")

/**
 * Single source of truth for local (offline) player state.
 *
 * The whole [AppState] is serialised to JSON and stored in one Preferences entry. This keeps the
 * app fully functional offline with no database and no network. A Room-backed implementation can
 * replace this class behind the same surface without touching the UI layer.
 */
class AppRepository(private val context: Context) {

    private val key = stringPreferencesKey("app_state")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val state: Flow<AppState> = context.mindflowDataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString(AppState.serializer(), raw) }.getOrNull()
        } ?: AppState()
    }

    /** Reads the current state synchronously-ish for use inside a write. */
    private fun decode(raw: String?): AppState =
        raw?.let { runCatching { json.decodeFromString(AppState.serializer(), it) }.getOrNull() } ?: AppState()

    suspend fun update(transform: (AppState) -> AppState) {
        context.mindflowDataStore.edit { prefs ->
            val current = decode(prefs[key])
            prefs[key] = json.encodeToString(AppState.serializer(), transform(current))
        }
    }

    suspend fun reset() {
        context.mindflowDataStore.edit { prefs -> prefs.remove(key) }
    }
}
