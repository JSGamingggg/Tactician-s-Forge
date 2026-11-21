package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FavoriteCompDoc
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferencesDoc
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreUserRepository(
    private val dbProvider: () -> FirebaseFirestore,
    private val authProvider: () -> FirebaseAuth = { Firebase.auth }
) {
    constructor(context: Context) : this(
        dbProvider = {
            FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        }
    )

    private val db: FirebaseFirestore by lazy { dbProvider() }
    private val auth: FirebaseAuth by lazy { authProvider() }

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) { "Google Sign-In required for cloud Firestore sync." }
        return uid
    }

    private fun sanitizeDocId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "item_${System.currentTimeMillis()}" }

    fun observeFavorites(userId: String): Flow<List<FavoriteCompDoc>> = flow {
        check(userId.isNotBlank() && auth.currentUser?.uid == userId) {
            "Refusing unauthenticated Firestore query."
        }
        val path = "users/$userId/favorites"
        emitAll(
            db.collection("users").document(userId).collection("favorites")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FavoriteCompDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }


    fun observePreferences(userId: String): Flow<UserPreferencesDoc?> = flow {
        check(userId.isNotBlank() && auth.currentUser?.uid == userId) {
            "Refusing unauthenticated Firestore query."
        }
        val path = "users/$userId/preferences/settings"
        emitAll(
            db.collection("users").document(userId).collection("preferences").document("settings")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserPreferencesDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveFavoriteComp(comp: MetaComp, pinnedToOverlay: Boolean): Result<Unit> {
        val docId = sanitizeDocId(comp.id)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/favorites/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("favorites").document(docId)
            docPath = docRef.path
            val existing = docRef.get().await()
            val prioritizedPlacements = comp.hexPlacements
                .sortedWith(
                    compareByDescending<com.example.data.model.HexUnitPlacement> { it.isCarry }
                        .thenByDescending { it.items.size }
                        .thenByDescending { it.cost }
                )
                .take(10)
            val prioritizedUnits = (prioritizedPlacements.map { it.unitName } + comp.coreUnits)
                .distinct()
                .take(10)
                .map { it.take(160) }
            val hexStrings = prioritizedPlacements.map { it.encode() }
            if (existing.exists()) {
                val updateMap = mutableMapOf<String, Any>(
                    "environment" to comp.environment.name,
                    "title" to comp.title.trim().take(80),
                    "tier" to comp.tier.name,
                    "playstyle" to comp.playstyle.trim().ifBlank { "Flex" }.take(40),
                    "carryName" to comp.carryName.trim().ifBlank { "Carry" }.take(50),
                    "coreUnits" to prioritizedUnits,
                    "hexPositions" to hexStrings,
                    "pinnedToOverlay" to pinnedToOverlay,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                comp.notes.trim().takeIf { it.isNotEmpty() }?.let {
                    updateMap["notes"] = it.take(300)
                }
                docRef.update(updateMap).await()
            } else {
                val doc = FavoriteCompDoc(
                    userId = uid,
                    compId = docId,
                    environment = comp.environment.name,
                    title = comp.title,
                    tier = comp.tier.name,
                    playstyle = comp.playstyle,
                    carryName = comp.carryName,
                    coreUnits = prioritizedUnits,
                    hexPositions = hexStrings,
                    pinnedToOverlay = pinnedToOverlay,
                    notes = comp.notes.takeIf { it.isNotBlank() }
                )
                docRef.set(doc.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleOverlayPin(compId: String, pinnedToOverlay: Boolean): Result<Unit> {
        val cleanId = sanitizeDocId(compId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/favorites/$cleanId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("favorites").document(cleanId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "pinnedToOverlay" to pinnedToOverlay,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeFavorite(compId: String): Result<Unit> {
        val cleanId = sanitizeDocId(compId)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/favorites/$cleanId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("favorites").document(cleanId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }


    suspend fun upsertPreferences(
        themeMode: ThemeMode,
        selectedEnvironment: TftEnvironment,
        patchAlertsEnabled: Boolean,
        pbeAlertsEnabled: Boolean,
        balanceAlertsEnabled: Boolean,
        trendingAlertsEnabled: Boolean,
        lastSeenLivePatch: String,
        lastSeenPbePatch: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/preferences/settings"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("preferences").document("settings")
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "themeMode" to themeMode.name,
                        "selectedEnvironment" to selectedEnvironment.name,
                        "patchAlertsEnabled" to patchAlertsEnabled,
                        "pbeAlertsEnabled" to pbeAlertsEnabled,
                        "balanceAlertsEnabled" to balanceAlertsEnabled,
                        "trendingAlertsEnabled" to trendingAlertsEnabled,
                        "lastSeenLivePatch" to lastSeenLivePatch.take(32),
                        "lastSeenPbePatch" to lastSeenPbePatch.take(32),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val doc = UserPreferencesDoc(
                    userId = uid,
                    themeMode = themeMode.name,
                    selectedEnvironment = selectedEnvironment.name,
                    patchAlertsEnabled = patchAlertsEnabled,
                    pbeAlertsEnabled = pbeAlertsEnabled,
                    balanceAlertsEnabled = balanceAlertsEnabled,
                    trendingAlertsEnabled = trendingAlertsEnabled,
                    lastSeenLivePatch = lastSeenLivePatch,
                    lastSeenPbePatch = lastSeenPbePatch
                )
                docRef.set(doc.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes the signed-in user's synced Firestore favorites, preferences,
     * and Firebase Authentication account (required by Google Play Account Deletion & Apple 5.1.1(v)).
     */
    suspend fun deleteAllUserDataAndAccount(): Result<Unit> {
        var activePath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in."))
            val uid = user.uid
            val userDoc = db.collection("users").document(uid)

            activePath = "users/$uid/favorites"
            val favDocs = userDoc.collection("favorites").get().await()
            for (doc in favDocs.documents) {
                doc.reference.delete().await()
            }

            activePath = "users/$uid/preferences/settings"
            runCatching {
                userDoc.collection("preferences").document("settings").delete().await()
            }

            activePath = "auth/users/$uid"
            user.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, activePath)
            Result.failure(e)
        }
    }
}
