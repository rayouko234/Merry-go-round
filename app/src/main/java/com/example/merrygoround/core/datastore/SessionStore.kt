package com.example.merrygoround.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for the authenticated session on this device.
 * Stores only the session token reference (never credentials). Production remote
 * builds should move the token into platform Keystore-backed storage — see
 * README §Security. Tests substitute an in-memory implementation.
 */
interface SessionStore {
    val isLoggedIn: Flow<Boolean>
    val currentUserId: Flow<String?>
    suspend fun token(): String?
    suspend fun userId(): String?
    suspend fun saveSession(token: String, userId: String)
    suspend fun clear()
}

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "mgr_session")

/** DataStore-preferences backed implementation used by the app. */
class DataStoreSessionStore(private val context: Context) : SessionStore {

    private object Keys {
        val TOKEN = stringPreferencesKey("session_token")
        val USER_ID = stringPreferencesKey("user_id")
    }

    override val isLoggedIn: Flow<Boolean> =
        context.sessionDataStore.data.map { !it[Keys.TOKEN].isNullOrBlank() }

    override val currentUserId: Flow<String?> =
        context.sessionDataStore.data.map { it[Keys.USER_ID] }

    override suspend fun token(): String? =
        context.sessionDataStore.data.first()[Keys.TOKEN]

    override suspend fun userId(): String? =
        context.sessionDataStore.data.first()[Keys.USER_ID]

    override suspend fun saveSession(token: String, userId: String) {
        context.sessionDataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.USER_ID] = userId
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
