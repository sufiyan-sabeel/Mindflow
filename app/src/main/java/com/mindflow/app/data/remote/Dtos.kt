package com.mindflow.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the Supabase RPC surface. Field names mirror the JSON keys the
 * server returns (snake_case), so no renaming layer is needed.
 */

@Serializable
data class LeaderboardRow(
    val rank: Long = 0,
    val user_id: String = "",
    val username: String = "",
    val display_name: String = "",
    val avatar_seed: Int = 0,
    val score: Long = 0,
    val tier: String? = null
)

@Serializable
data class RoomRow(
    val id: String = "",
    val code: String = "",
    val mode: String = "",
    val host_name: String = "",
    val players: Int = 0,
    val created_at: String = ""
)

@Serializable
data class RoomPlayer(
    val user_id: String = "",
    val username: String = "",
    val display_name: String = "",
    val avatar_seed: Int = 0,
    val score: Int = 0,
    val ready: Boolean = false,
    val submitted: Boolean = false,
    val is_host: Boolean = false,
    val is_me: Boolean = false
)

@Serializable
data class RoomState(
    val id: String = "",
    val code: String = "",
    val mode: String = "",
    val status: String = "open",
    val host_id: String = "",
    val is_host: Boolean = false,
    val players: List<RoomPlayer> = emptyList()
)

@Serializable
data class MatchRow(
    val id: String = "",
    val mode: String = "",
    val opponent: String = "",
    val my_score: Int = 0,
    val their_score: Int = 0,
    val won: Boolean = false,
    val drawn: Boolean = false,
    val created_at: String = ""
)

@Serializable
data class SubmitOutcome(
    val duplicate: Boolean = false,
    val stars: Int = 0,
    val xp: Int = 0,
    val score: Int = 0,
    val rating: Int = 0,
    @SerialName("rating_delta") val ratingDelta: Int = 0
)

/** A snapshot of the signed-in player's server-side state, used to restore on a new device. */
data class CloudSnapshot(
    val displayName: String? = null,
    val avatarSeed: Int? = null,
    val community: String? = null,
    val xp: Int = 0,
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val bestStreak: Int = 0,
    val streakDays: Int = 0,
    val ratings: Map<String, Int> = emptyMap(),
    val levels: List<CloudLevel> = emptyList(),
    val achievements: List<String> = emptyList(),
    val daily: List<CloudDaily> = emptyList()
)

data class CloudLevel(val levelId: Int, val stars: Int, val bestScore: Int, val completed: Boolean)

data class CloudDaily(val epochDay: Long, val challengeId: String, val score: Int)
