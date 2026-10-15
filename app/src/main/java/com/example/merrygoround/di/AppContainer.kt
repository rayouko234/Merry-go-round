package com.example.merrygoround.di

import android.content.Context
import com.example.merrygoround.BuildConfig
import com.example.merrygoround.core.datastore.DataStoreSessionStore
import com.example.merrygoround.core.datastore.SessionStore
import com.example.merrygoround.data.backend.BackendApi
import com.example.merrygoround.data.backend.local.LocalBackend
import com.example.merrygoround.data.local.AppDatabase
import com.example.merrygoround.data.repository.ActivityRepository
import com.example.merrygoround.data.repository.AuthRepository
import com.example.merrygoround.data.repository.ContributionRepository
import com.example.merrygoround.data.repository.GroupRepository

/**
 * Manual dependency container (no Hilt) — the single wiring point for the app.
 *
 * Backend selection is the INTEGRATION BOUNDARY:
 *  - BuildConfig.BACKEND_MODE == "local"  (default) -> LocalBackend (Room, authoritative)
 *  - BuildConfig.BACKEND_MODE == "remote"            -> RemoteBackend (REST, docs/API.md)
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.get(appContext)
    val sessionStore: SessionStore = DataStoreSessionStore(appContext)

    val backend: BackendApi = when (BuildConfig.BACKEND_MODE) {
        "remote" -> createRemoteBackend()
        else -> LocalBackend.create(database, sessionStore)
    }

    private fun createRemoteBackend(): BackendApi =
        // Remote wiring lives in data.backend.remote.RemoteBackend; when BACKEND_MODE
        // is "remote" but the class is unavailable the build fails loudly rather than
        // silently falling back — never fake data (README §Backend modes).
        com.example.merrygoround.data.backend.remote.RemoteBackend(
            baseUrl = BuildConfig.API_BASE_URL,
            sessionStore = sessionStore
        )

    val authRepository = AuthRepository(backend, sessionStore)
    val groupRepository = GroupRepository(backend)
    val contributionRepository = ContributionRepository(backend)
    val activityRepository = ActivityRepository(backend)
}
