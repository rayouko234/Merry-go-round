package com.example.merrygoround.data.backend.firebase

/**
 * Thin seams over the Firebase SDKs.
 *
 * FirebaseBackend implements every business rule against these interfaces so
 * the logic is unit-testable on the JVM with [InMemoryFirebaseStore] and
 * [InMemoryFirebaseIdentity] (see tests), while [RealFirebaseGateways] binds
 * them to FirebaseAuth + Cloud Firestore on device.
 */

/** A document: logical path like "groups/g1/members/u1" plus its fields. */
data class Doc(val path: String, val data: Map<String, Any?>)

/** One mutation inside a transaction; [data] == null means delete. */
data class TxWrite(val path: String, val data: Map<String, Any?>?)

/** Result of a transaction body: value to return + writes to commit atomically. */
data class TxOutcome<T>(val result: T, val writes: List<TxWrite>)

interface FirebaseIdentity {
    /** Firebase Auth uid of the signed-in user, or null. */
    val currentUid: String?
    suspend fun createUser(email: String, password: String): String
    suspend fun signIn(email: String, password: String): String
    suspend fun signOut()

    /** Deletes the signed-in Auth user (used to roll back a failed registration). */
    suspend fun deleteCurrentUser()
}

interface FirebaseStore {
    suspend fun get(path: String): Map<String, Any?>?

    /** Docs in one collection (or sub-collection path) where [where] field equals value. */
    suspend fun query(path: String, where: Pair<String, Any?>? = null): List<Doc>

    /** collectionGroup query across every doc in [collectionName] sub-collections. */
    suspend fun queryGroup(collectionName: String, where: Pair<String, Any?>): List<Doc>

    suspend fun set(path: String, data: Map<String, Any?>)

    suspend fun update(path: String, data: Map<String, Any?>)

    suspend fun delete(path: String)

    /**
     * Atomically reads [readPaths] (all reads happen before any write), runs
     * [block], then commits the returned writes as one unit. Retries mirror
     * Firestore's transaction semantics. This is what prevents duplicate
     * payouts and lost updates under concurrent clients.
     */
    suspend fun <T> transaction(readPaths: List<String>, block: (Map<String, Map<String, Any?>>) -> TxOutcome<T>): T
}
