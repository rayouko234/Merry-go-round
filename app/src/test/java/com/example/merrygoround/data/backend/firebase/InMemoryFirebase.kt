package com.example.merrygoround.data.backend.firebase

import com.example.merrygoround.core.security.Ids

/**
 * In-memory [FirebaseStore] for unit tests: same semantics as Cloud Firestore
 * (path-addressed docs, merge writes, snapshot-read transactions).
 */
class InMemoryFirebaseStore : FirebaseStore {

    /** Logical path → document fields. */
    val docs: MutableMap<String, Map<String, Any?>> = mutableMapOf()

    override suspend fun get(path: String): Map<String, Any?>? = docs[path]

    override suspend fun query(path: String, where: Pair<String, Any?>?): List<Doc> =
        if (where == null) directChildren(path)
        else directChildren(path).filter { it.data[where.first] == where.second }

    override suspend fun queryGroup(collectionName: String, where: Pair<String, Any?>): List<Doc> =
        docs.entries
            .filter { it.key.matches(Regex(".*/$collectionName/[^/]+$")) }
            .map { Doc(it.key, it.value) }
            .filterDocWhere(listOf(where))

    override suspend fun set(path: String, data: Map<String, Any?>) {
        docs[path] = (docs[path] ?: emptyMap()) + data
    }

    override suspend fun update(path: String, data: Map<String, Any?>) {
        check(docs.containsKey(path)) { "No document to update: $path" }
        docs[path] = (docs[path] ?: emptyMap()) + data
    }

    override suspend fun delete(path: String) {
        docs.remove(path)
    }

    override suspend fun <T> transaction(
        readPaths: List<String>,
        block: (Map<String, Map<String, Any?>>) -> TxOutcome<T>
    ): T {
        // All reads first, then writes commit as one unit (like Firestore).
        val reads = readPaths.associateWith { docs[it] ?: emptyMap() }
        val outcome = block(reads)
        outcome.writes.forEach { w ->
            if (w.data == null) docs.remove(w.path)
            else docs[w.path] = (docs[w.path] ?: emptyMap()) + w.data!!
        }
        return outcome.result
    }

    private fun directChildren(path: String): List<Doc> = docs.entries
        .filter { it.key.startsWith("$path/") && !it.key.substring(path.length + 1).contains('/') }
        .map { Doc(it.key, it.value) }

    private fun List<Doc>.filterDocWhere(where: Pair<String, Any?>?): List<Doc> =
        if (where == null) this else filter { it.data[where.first] == where.second }

    private fun List<Doc>.filterDocWhere(wheres: List<Pair<String, Any?>>): List<Doc> =
        filter { doc -> wheres.all { doc.data[it.first] == it.second } }
}

/** In-memory [FirebaseIdentity]: unique emails, password check, current session. */
class InMemoryFirebaseIdentity : FirebaseIdentity {
    private val uidsByEmail = mutableMapOf<String, String>()
    private val passwordsByUid = mutableMapOf<String, String>()
    private var current: String? = null

    override val currentUid: String? get() = current

    override suspend fun createUser(email: String, password: String): String {
        if (uidsByEmail.containsKey(email)) {
            throw IllegalStateException("The email address is already in use by another account.")
        }
        val uid = "uid-${Ids.newId()}"
        uidsByEmail[email] = uid
        passwordsByUid[uid] = password
        current = uid
        return uid
    }

    override suspend fun signIn(email: String, password: String): String {
        val uid = uidsByEmail[email]
            ?: throw IllegalStateException("There is no user record corresponding to the provided identifier.")
        if (passwordsByUid[uid] != password) {
            throw IllegalStateException("The password is invalid or the user does not have a password.")
        }
        current = uid
        return uid
    }

    override suspend fun signOut() {
        current = null
    }

    override suspend fun deleteCurrentUser() {
        val uid = current ?: return
        uidsByEmail.entries.removeAll { it.value == uid }
        passwordsByUid.remove(uid)
        current = null
    }
}
