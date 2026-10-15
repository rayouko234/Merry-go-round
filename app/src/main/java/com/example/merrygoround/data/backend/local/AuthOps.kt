package com.example.merrygoround.data.backend.local

import com.example.merrygoround.core.common.AppErrorType
import com.example.merrygoround.core.common.AppException
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.Validators
import com.example.merrygoround.core.datastore.SessionStore
import com.example.merrygoround.core.security.Ids
import com.example.merrygoround.core.security.PasswordHasher
import com.example.merrygoround.data.local.AppDatabase
import com.example.merrygoround.data.local.SessionEntity
import com.example.merrygoround.data.local.UserEntity
import com.example.merrygoround.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Authentication against the local authoritative store.
 * Passwords are PBKDF2-hashed; sessions are server-style tokens validated on every call.
 */
internal class AuthOps(
    private val db: AppDatabase,
    private val sessionStore: SessionStore,
    private val now: () -> Long
) {

    companion object {
        const val SESSION_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }

    suspend fun register(
        fullName: String,
        email: String,
        phone: String,
        password: String
    ): AppResult<User> = withContext(Dispatchers.IO) {
        val fieldErrors = mutableMapOf<String, String>()
        Validators.validateFullName(fullName)?.let { fieldErrors["fullName"] = it }
        Validators.validateEmail(email)?.let { fieldErrors["email"] = it }
        Validators.validatePhone(phone)?.let { fieldErrors["phone"] = it }
        Validators.validatePassword(password)?.let { fieldErrors["password"] = it }
        if (fieldErrors.isNotEmpty()) {
            return@withContext AppResult.Failure(
                AppErrorType.VALIDATION, "Please fix the highlighted fields", fieldErrors
            )
        }

        val normEmail = email.trim().lowercase()
        val normPhone = Validators.normalizePhone(phone)

        if (db.userDao().findByEmail(normEmail) != null) {
            return@withContext AppResult.Failure(
                AppErrorType.CONFLICT, "An account with this email already exists",
                mapOf("email" to "This email is already registered")
            )
        }
        if (db.userDao().findByPhone(normPhone) != null) {
            return@withContext AppResult.Failure(
                AppErrorType.CONFLICT, "An account with this phone number already exists",
                mapOf("phone" to "This phone number is already registered")
            )
        }

        val salt = PasswordHasher.generateSalt()
        val user = UserEntity(
            id = Ids.newId(),
            fullName = fullName.trim(),
            email = normEmail,
            phone = normPhone,
            passwordHash = PasswordHasher.hash(password, salt),
            passwordSalt = salt,
            status = "ACTIVE",
            createdAtMillis = now()
        )
        db.userDao().insert(user)
        startSession(user.id)
        AppResult.Success(user.toDomain())
    }

    suspend fun login(identifier: String, password: String): AppResult<User> =
        withContext(Dispatchers.IO) {
            if (identifier.isBlank() || password.isBlank()) {
                return@withContext AppResult.Failure(
                    AppErrorType.VALIDATION, "Enter your email/phone and password",
                    buildMap {
                        if (identifier.isBlank()) put("identifier", "Required")
                        if (password.isBlank()) put("password", "Required")
                    }
                )
            }
            // Accepts email or phone; phones are tried in raw and normalised form.
            val user = db.userDao().findByIdentifier(identifier.trim())
                ?: db.userDao().findByIdentifier(Validators.normalizePhone(identifier.trim()))
                ?: return@withContext AppResult.Failure(
                    AppErrorType.AUTHENTICATION, "Incorrect email/phone or password"
                )
            if (user.status != "ACTIVE") {
                return@withContext AppResult.Failure(
                    AppErrorType.AUTHENTICATION, "This account is suspended. Contact support."
                )
            }
            if (!PasswordHasher.verify(password, user.passwordSalt, user.passwordHash)) {
                return@withContext AppResult.Failure(
                    AppErrorType.AUTHENTICATION, "Incorrect email/phone or password"
                )
            }
            startSession(user.id)
            AppResult.Success(user.toDomain())
        }

    suspend fun logout(): AppResult<Unit> = withContext(Dispatchers.IO) {
        val token = sessionStore.token()
        if (token != null) db.sessionDao().delete(token)
        sessionStore.clear()
        AppResult.Success(Unit)
    }

    suspend fun currentUser(): UserEntity {
        val token = sessionStore.token()
            ?: throw AppException(AppErrorType.AUTHENTICATION, "Please sign in to continue")
        val session = db.sessionDao().findByToken(token)
            ?: throw AppException(AppErrorType.AUTHENTICATION, "Your session has expired. Please sign in again.")
        if (session.expiresAtMillis < now()) {
            db.sessionDao().delete(token)
            sessionStore.clear()
            throw AppException(AppErrorType.AUTHENTICATION, "Your session has expired. Please sign in again.")
        }
        return db.userDao().findById(session.userId)
            ?: throw AppException(AppErrorType.AUTHENTICATION, "Account no longer exists")
    }

    suspend fun updateProfile(fullName: String?, phone: String?): AppResult<User> =
        withContext(Dispatchers.IO) {
            val user = currentUser()
            val newName = fullName?.trim() ?: user.fullName
            val newPhone = Validators.normalizePhone(phone ?: user.phone)
            Validators.validateFullName(newName)?.let {
                return@withContext AppResult.Failure(AppErrorType.VALIDATION, it, mapOf("fullName" to it))
            }
            Validators.validatePhone(newPhone)?.let {
                return@withContext AppResult.Failure(AppErrorType.VALIDATION, it, mapOf("phone" to it))
            }
            val phoneOwner = db.userDao().findByPhone(newPhone)
            if (phoneOwner != null && phoneOwner.id != user.id) {
                return@withContext AppResult.Failure(
                    AppErrorType.CONFLICT, "That phone number is used by another account",
                    mapOf("phone" to "Already in use")
                )
            }
            db.userDao().updateProfile(user.id, newName, newPhone)
            AppResult.Success(user.copy(fullName = newName, phone = newPhone).toDomain())
        }

    private suspend fun startSession(userId: String) {
        val token = Ids.newToken()
        val t = now()
        db.sessionDao().upsert(
            SessionEntity(
                token = token,
                userId = userId,
                createdAtMillis = t,
                expiresAtMillis = t + SESSION_TTL_MILLIS
            )
        )
        sessionStore.saveSession(token, userId)
    }
}

internal fun UserEntity.toDomain() = User(
    id = id,
    fullName = fullName,
    email = email,
    phone = phone,
    status = status,
    createdAtMillis = createdAtMillis
)

