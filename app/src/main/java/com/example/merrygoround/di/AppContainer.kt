package com.example.merrygoround.di

import android.content.Context
import com.example.merrygoround.BuildConfig
import com.example.merrygoround.core.datastore.DataStoreSessionStore
import com.example.merrygoround.core.datastore.SessionStore
import com.example.merrygoround.data.backend.BackendApi
import com.example.merrygoround.data.backend.firebase.FirebaseBackend
import com.example.merrygoround.data.backend.firebase.RealFirebaseIdentity
import com.example.merrygoround.data.backend.firebase.RealFirebaseStore
import com.example.merrygoround.data.backend.local.LocalBackend
import com.example.merrygoround.data.local.AppDatabase
import com.example.merrygoround.data.repository.ActivityRepository
import com.example.merrygoround.data.repository.AuthRepository
import com.example.merrygoround.data.repository.ContributionRepository
import com.example.merrygoround.data.repository.GroupRepository

/**
 * Manual dependency container (no Hilt) — the single wiring point for the app.
 *
 * Backend selection (BuildConfig.BACKEND_MODE, default "firebase"):
 *  - "firebase" → Firebase Auth + Cloud Firestore via [FirebaseBackend]
 *                 (project mt-project-ce4c0, app/google-services.json).
 *                 Firestore rules in firestore.rules re-enforce authorization.
 *  - "local"    → [LocalBackend]: Room-backed authoritative store for
 *                 development/demo without Firebase.
 * Anything else fails fast — never silently falls back to fake data.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.get(appContext)
    val sessionStore: SessionStore = DataStoreSessionStore(appContext)

    /**
     * Constructed lazily so processes without Firebase initialization (unit
     * tests, early startup) never touch the SDK until a backend call happens.
     * The real app auto-initializes Firebase via the google-services plugin's
     * ContentProvider before the first backend call.
     */
    val backend: BackendApi by lazy {
        when (BuildConfig.BACKEND_MODE) {
            "firebase" -> try {
                FirebaseBackend(
                    identity = RealFirebaseIdentity(),
                    store = RealFirebaseStore(),
                    session = sessionStore
                )
            } catch (e: Exception) {
                error(
                    "Firebase is not initialized. Check app/google-services.json and that " +
                        "Firebase Auth + Firestore are enabled in the console. (${e.message})"
                )
            }
            "local" -> LocalBackend.create(database, sessionStore)
            else -> error(
                "Unknown mgr.backendMode '${BuildConfig.BACKEND_MODE}'. Use \"firebase\" (default) or \"local\"."
            )
        }
    }

    val authRepository by lazy { AuthRepository(backend, sessionStore) }
    val groupRepository by lazy { GroupRepository(backend) }
    val contributionRepository by lazy { ContributionRepository(backend) }
    val activityRepository by lazy { ActivityRepository(backend) }
}
