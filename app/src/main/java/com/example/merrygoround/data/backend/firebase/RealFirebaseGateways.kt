package com.example.merrygoround.data.backend.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Callback/Task → suspend bridges. */
private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
    suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }
        task.addOnFailureListener { cont.resumeWithException(it) }
    }

/** FirebaseAuth binding (email/password auth; session handled by the SDK). */
class RealFirebaseIdentity(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : FirebaseIdentity {

    override val currentUid: String? get() = auth.currentUser?.uid

    override suspend fun createUser(email: String, password: String): String =
        await(auth.createUserWithEmailAndPassword(email, password)).user?.uid
            ?: error("Firebase Auth returned no user")

    override suspend fun signIn(email: String, password: String): String =
        await(auth.signInWithEmailAndPassword(email, password)).user?.uid
            ?: error("Firebase Auth returned no user")

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun deleteCurrentUser() {
        auth.currentUser?.let { await(it.delete()) }
    }
}

/**
 * Cloud Firestore binding. Offline persistence is enabled so previously read
 * group/activity data stays readable without a connection (brief §9).
 */
class RealFirebaseStore(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : FirebaseStore {

    init {
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }

    override suspend fun get(path: String): Map<String, Any?>? =
        await(db.document(path).get()).data

    override suspend fun query(path: String, where: Pair<String, Any?>?): List<Doc> {
        var q = db.collection(path) as com.google.firebase.firestore.Query
        if (where != null) q = q.whereEqualTo(where.first, where.second)
        return await(q.get()).documents.mapNotNull { snap -> snap.data?.let { Doc(snap.reference.path, it) } }
    }

    override suspend fun queryGroup(collectionName: String, where: Pair<String, Any?>): List<Doc> {
        val q = db.collectionGroup(collectionName).whereEqualTo(where.first, where.second)
        return await(q.get()).documents.mapNotNull { snap -> snap.data?.let { Doc(snap.reference.path, it) } }
    }

    override suspend fun set(path: String, data: Map<String, Any?>) {
        await(db.document(path).set(data, SetOptions.merge()))
    }

    override suspend fun update(path: String, data: Map<String, Any?>) {
        await(db.document(path).update(data))
    }

    override suspend fun delete(path: String) {
        await(db.document(path).delete())
    }

    override suspend fun <T> transaction(
        readPaths: List<String>,
        block: (Map<String, Map<String, Any?>>) -> TxOutcome<T>
    ): T = suspendCancellableCoroutine { cont ->
        db.runTransaction { txn: Transaction ->
            val reads = readPaths.associateWith { path -> txn.get(db.document(path)).data ?: emptyMap() }
            val outcome = block(reads)
            outcome.writes.forEach { w ->
                val doc = db.document(w.path)
                if (w.data == null) txn.delete(doc) else txn.set(doc, w.data!!, SetOptions.merge())
            }
            outcome.result
        }.addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
