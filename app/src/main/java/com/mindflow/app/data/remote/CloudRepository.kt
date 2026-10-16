package com.mindflow.app.data.remote

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Everything the app needs from Supabase, behind one class.
 *
 * Two deliberate constraints:
 *  - the client only ever calls `auth` and `rpc`; there is no raw table access, so the
 *    RLS policies and SECURITY DEFINER functions are the only way in;
 *  - every call is best-effort. If the device is offline, unconfigured, or the call
 *    fails, the method returns null/false and the caller keeps using local state.
 *    Single-player never depends on the network.
 */
class CloudRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    val isConfigured: Boolean get() = SupabaseProvider.isConfigured

    // ---------------------------------------------------------------- identity

    fun currentUserId(): String? =
        if (!isConfigured) null
        else runCatching { SupabaseProvider.client.auth.currentUserOrNull()?.id }.getOrNull()

    fun currentEmail(): String? =
        if (!isConfigured) null
        else runCatching { SupabaseProvider.client.auth.currentUserOrNull()?.email }.getOrNull()

    fun isSignedIn(): Boolean = currentUserId() != null

    /** Anonymous (guest) sign-in produces a user with no email. */
    fun isGuest(): Boolean = isSignedIn() && currentEmail() == null

    suspend fun signUp(email: String, password: String, displayName: String): Result<Unit> = io {
        runCatching {
            SupabaseProvider.client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = buildJsonObject { put("display_name", JsonPrimitive(displayName)) }
            }
            Unit
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = io {
        runCatching {
            SupabaseProvider.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Unit
        }
    }

    suspend fun signInAsGuest(): Result<Unit> = io {
        runCatching {
            SupabaseProvider.client.auth.signInAnonymously()
            Unit
        }
    }

    suspend fun signOut(): Result<Unit> = io {
        runCatching { SupabaseProvider.client.auth.signOut() }
    }

    suspend fun upsertProfile(displayName: String, avatarSeed: Int, community: String?): Boolean =
        rpcOk("upsert_profile", buildJsonObject {
            put("p_display_name", JsonPrimitive(displayName))
            put("p_avatar_seed", JsonPrimitive(avatarSeed))
            put("p_community", community?.let { JsonPrimitive(it) } ?: JsonNull)
        })

    // ------------------------------------------------------------------ sync

    suspend fun submitResult(
        clientId: String,
        mode: String,
        levelId: Int?,
        correct: Int,
        total: Int,
        bestStreak: Int,
        avgResponseMs: Long
    ): SubmitOutcome? = io {
        val text = rpcText("submit_game_result", buildJsonObject {
            put("p_client_id", JsonPrimitive(clientId))
            put("p_mode", JsonPrimitive(mode))
            put("p_level_id", levelId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_correct", JsonPrimitive(correct))
            put("p_total", JsonPrimitive(total))
            put("p_best_streak", JsonPrimitive(bestStreak))
            put("p_avg_response_ms", JsonPrimitive(avgResponseMs))
        }) ?: return@io null
        runCatching { json.decodeFromString(SubmitOutcome.serializer(), text) }.getOrNull()
    }

    suspend fun claimDaily(epochDay: Long, challengeId: String, score: Int): Boolean =
        rpcOk("claim_daily_challenge", buildJsonObject {
            put("p_epoch_day", JsonPrimitive(epochDay))
            put("p_challenge_id", JsonPrimitive(challengeId))
            put("p_score", JsonPrimitive(score))
        })

    suspend fun ensureDaily(epochDay: Long): Boolean =
        rpcOk("ensure_daily_challenges", buildJsonObject { put("p_epoch_day", JsonPrimitive(epochDay)) })

    suspend fun pullSnapshot(): CloudSnapshot? = io {
        val text = rpcText("get_my_snapshot", buildJsonObject {}) ?: return@io null
        runCatching { parseSnapshot(text) }.getOrNull()
    }

    // ----------------------------------------------------------- leaderboards

    suspend fun leaderboard(
        scope: String,
        kind: String,
        mode: String,
        period: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<LeaderboardRow>? = io {
        val text = rpcText("get_leaderboard", buildJsonObject {
            put("p_scope", JsonPrimitive(scope))
            put("p_kind", JsonPrimitive(kind))
            put("p_mode", JsonPrimitive(mode))
            put("p_period", JsonPrimitive(period))
            put("p_limit", JsonPrimitive(limit))
            put("p_offset", JsonPrimitive(offset))
        }) ?: return@io null
        runCatching {
            json.decodeFromString(ListSerializer(LeaderboardRow.serializer()), text)
        }.getOrNull()
    }

    // ------------------------------------------------------------ multiplayer

    suspend fun createRoom(mode: String): RoomState? = io {
        val text = rpcText("create_room", buildJsonObject { put("p_mode", JsonPrimitive(mode)) })
            ?: return@io null
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@io null
        RoomState(
            id = o["id"]?.jsonPrimitive?.content.orEmpty(),
            code = o["code"]?.jsonPrimitive?.content.orEmpty(),
            mode = o["mode"]?.jsonPrimitive?.content.orEmpty(),
            status = o["status"]?.jsonPrimitive?.content ?: "open"
        )
    }

    suspend fun joinRoom(code: String): String? = io {
        val text = rpcText("join_room", buildJsonObject { put("p_code", JsonPrimitive(code)) })
            ?: return@io null
        runCatching {
            json.parseToJsonElement(text).jsonObject["room_id"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    suspend fun setReady(roomId: String, ready: Boolean): Boolean =
        rpcOk("set_room_ready", buildJsonObject {
            put("p_room_id", JsonPrimitive(roomId))
            put("p_ready", JsonPrimitive(ready))
        })

    suspend fun leaveRoom(roomId: String): Boolean =
        rpcOk("leave_room", buildJsonObject { put("p_room_id", JsonPrimitive(roomId)) })

    suspend fun submitRoomScore(roomId: String, correct: Int, total: Int, bestStreak: Int): Boolean =
        rpcOk("submit_room_score", buildJsonObject {
            put("p_room_id", JsonPrimitive(roomId))
            put("p_correct", JsonPrimitive(correct))
            put("p_total", JsonPrimitive(total))
            put("p_best_streak", JsonPrimitive(bestStreak))
        })

    suspend fun roomState(roomId: String): RoomState? = io {
        val text = rpcText("get_room_state", buildJsonObject { put("p_room_id", JsonPrimitive(roomId)) })
            ?: return@io null
        runCatching {
            json.decodeFromString(RoomState.serializer(), text)
        }.getOrNull()
    }

    suspend fun openRooms(mode: String? = null, limit: Int = 20): List<RoomRow>? = io {
        val text = rpcText("list_open_rooms", buildJsonObject {
            put("p_mode", mode?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_limit", JsonPrimitive(limit))
        }) ?: return@io null
        runCatching { json.decodeFromString(ListSerializer(RoomRow.serializer()), text) }.getOrNull()
    }

    suspend fun matchHistory(limit: Int = 20, offset: Int = 0): List<MatchRow>? = io {
        val text = rpcText("get_match_history", buildJsonObject {
            put("p_limit", JsonPrimitive(limit))
            put("p_offset", JsonPrimitive(offset))
        }) ?: return@io null
        runCatching { json.decodeFromString(ListSerializer(MatchRow.serializer()), text) }.getOrNull()
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    /** Calls an RPC and returns the raw body, or null if unconfigured / the call failed. */
    private suspend fun rpcText(function: String, params: JsonObject): String? {
        if (!isConfigured) return null
        return runCatching {
            SupabaseProvider.client.postgrest.rpc(function, params).data
        }.getOrNull()
    }

    private suspend fun rpcOk(function: String, params: JsonObject): Boolean =
        rpcText(function, params) != null

    private fun parseSnapshot(text: String): CloudSnapshot {
        val root = json.parseToJsonElement(text).jsonObject
        val profile = root["profile"].asObjectOrNull()
        val progress = root["progress"].asObjectOrNull()

        val ratings = root["ratings"].asObjectOrNull()?.mapNotNull { (k, v) ->
            v.jsonPrimitive.content.toIntOrNull()?.let { k to it }
        }?.toMap().orEmpty()

        val levels = root["levels"]?.takeIf { it !is JsonNull }?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["level_id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
            CloudLevel(
                levelId = id,
                stars = o["stars"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                bestScore = o["best_score"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                completed = o["completed"]?.jsonPrimitive?.content?.toBoolean() ?: false
            )
        }.orEmpty()

        val achievements = root["achievements"]?.takeIf { it !is JsonNull }?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() } }.orEmpty()

        val daily = root["daily"]?.takeIf { it !is JsonNull }?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            CloudDaily(
                epochDay = o["epoch_day"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null,
                challengeId = o["challenge_id"]?.jsonPrimitive?.content.orEmpty(),
                score = o["score"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            )
        }.orEmpty()

        return CloudSnapshot(
            displayName = profile?.get("display_name")?.jsonPrimitive?.content,
            avatarSeed = profile?.get("avatar_seed")?.jsonPrimitive?.content?.toIntOrNull(),
            community = profile?.get("community")?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
            xp = progress?.get("xp")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            gamesPlayed = progress?.get("games_played")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            wins = progress?.get("wins")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            losses = progress?.get("losses")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            bestStreak = progress?.get("best_streak")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            streakDays = progress?.get("streak_days")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            ratings = ratings,
            levels = levels,
            achievements = achievements,
            daily = daily
        )
    }

    private fun JsonElement?.asObjectOrNull(): JsonObject? =
        this?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonObject }.getOrNull() }
}
