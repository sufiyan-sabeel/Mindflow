package com.mindflow.app.data

/** An unlockable achievement. [check] is evaluated against the current persisted state. */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val check: (AppState) -> Boolean
)
