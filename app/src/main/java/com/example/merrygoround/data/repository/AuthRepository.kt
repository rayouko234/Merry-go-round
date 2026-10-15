package com.example.merrygoround.data.repository

import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.datastore.SessionStore
import com.example.merrygoround.data.backend.BackendApi
import com.example.merrygoround.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Session is the single source of truth for "who is signed in" (brief §7).
 * All auth operations go through the backend; the store persists the token.
 */
class AuthRepository(
    private val backend: BackendApi,
    private val sessionStore: SessionStore
) {
    val isLoggedIn: Flow<Boolean> = sessionStore.isLoggedIn
    val currentUserId: Flow<String?> = sessionStore.currentUserId

    suspend fun register(fullName: String, email: String, phone: String, password: String): AppResult<User> =
        backend.register(fullName, email, phone, password)

    suspend fun login(identifier: String, password: String): AppResult<User> =
        backend.login(identifier, password)

    suspend fun logout(): AppResult<Unit> = backend.logout()

    suspend fun me(): AppResult<User> = backend.me()

    suspend fun updateProfile(fullName: String?, phone: String?): AppResult<User> =
        backend.updateProfile(fullName, phone)

    suspend fun hasSession(): Boolean = sessionStore.token() != null
}
