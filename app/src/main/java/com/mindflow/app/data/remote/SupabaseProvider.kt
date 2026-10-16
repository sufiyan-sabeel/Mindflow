package com.mindflow.app.data.remote

import com.mindflow.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * Owns the Supabase client.
 *
 * Configuration comes from BuildConfig, which is populated at build time from
 * Gradle properties (`mindflow.supabaseUrl`, `mindflow.supabaseAnonKey`). Nothing
 * is committed to source control. Only the *publishable* client key is ever used —
 * never a service-role key or database password.
 *
 * When the values are absent the app is fully playable offline and every online
 * feature reports itself as "not configured" instead of failing.
 */
object SupabaseProvider {

    val url: String = BuildConfig.SUPABASE_URL
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY

    /** True when a usable project URL and publishable key were injected at build time. */
    val isConfigured: Boolean
        get() = url.startsWith("https://") && url.contains(".supabase.") && anonKey.length > 20

    val client: SupabaseClient by lazy {
        createSupabaseClient(url, anonKey) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
}
