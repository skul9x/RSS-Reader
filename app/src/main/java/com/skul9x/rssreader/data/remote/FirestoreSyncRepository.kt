package com.skul9x.rssreader.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.skul9x.rssreader.data.model.ReadNewsItem
import com.skul9x.rssreader.data.model.SyncStatus
import kotlinx.coroutines.tasks.await

class FirestoreSyncRepository private constructor(
    private val logRepo: com.skul9x.rssreader.data.repository.FirebaseLogRepository?
) {

    companion object {
        @Volatile
        private var INSTANCE: FirestoreSyncRepository? = null

        fun getInstance(logRepo: com.skul9x.rssreader.data.repository.FirebaseLogRepository? = null): FirestoreSyncRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirestoreSyncRepository(logRepo).also { INSTANCE = it }
            }
        }
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getUserCollection() =
        firestore.collection("users")
            .document(auth.currentUser?.uid ?: throw IllegalStateException("Not signed in"))
            .collection("readNews")

    /**
     * Uploads a batch of read news items to Firestore using a batch write.
     */
    suspend fun uploadBatch(items: List<ReadNewsItem>) {
        if (items.isEmpty()) return
        
        try {
            val batch = firestore.batch()
            val collection = getUserCollection()

            items.forEach { item ->
                val docRef = collection.document(item.newsId)
                val data = hashMapOf(
                    "newsItemId" to item.newsId,
                    "readTimestamp" to item.readAt,
                    "deviceType" to item.deviceType,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, data, SetOptions.merge())
            }

            batch.commit().await()
            logRepo?.logSuccess("Uploaded batch of ${items.size} items", "IDs: ${items.take(5).map { it.newsId }}...")
        } catch (e: Exception) {
            logRepo?.logError("Failed to upload batch of ${items.size} items", e)
            throw e
        }
    }

    /**
     * Downloads read news items updated AFTER the specified timestamp.
     * Uses incremental sync to save bandwidth and battery.
     */
    suspend fun downloadSince(sinceTimestamp: Long): Pair<List<ReadNewsItem>, Long> {
        return try {
            val query = if (sinceTimestamp > 0) {
                getUserCollection().whereGreaterThanOrEqualTo("updatedAt", java.util.Date(sinceTimestamp))
            } else {
                getUserCollection() // Full sync for first run
            }
            
            val snapshot = query.get().await()
            var maxTimestamp = sinceTimestamp
            
            val items = snapshot.documents.mapNotNull { doc ->
                val newsId = doc.getString("newsItemId") ?: doc.id
                val readTimestamp = doc.getLong("readTimestamp") ?: 0L
                val deviceType = doc.getString("deviceType") ?: "unknown"
                val updatedAt = doc.getDate("updatedAt")
                
                if (updatedAt != null && updatedAt.time > maxTimestamp) {
                    maxTimestamp = updatedAt.time
                }

                ReadNewsItem(
                    newsId = newsId,
                    readAt = readTimestamp,
                    deviceType = deviceType,
                    syncStatus = SyncStatus.SYNCED
                )
            }
            
            if (items.isNotEmpty()) {
                logRepo?.logSuccess("Downloaded ${items.size} new items", "Since: $sinceTimestamp")
            }
            
            Pair(items, maxTimestamp)
        } catch (e: Exception) {
            logRepo?.logError("Failed to download items since $sinceTimestamp", e)
            e.printStackTrace()
            Pair(emptyList(), sinceTimestamp)
        }
    }

    /**
     * Deletes items older than the specified timestamp from Firestore.
     * Note: Firestore delete batches are limited to 500 ops.
     * Uses chunked processing to handle large deletions safely.
     */
    suspend fun deleteOldItems(olderThan: Long) {
        try {
            // NOTE: This query requires a Composite Index on 'readTimestamp' (ASC) and 'newsItemId' (ASC/DESC)
            // if used with other filters, or at least an Single Field Index on 'readTimestamp'.
            // If the query fails, check the Logcat for a generated link to create the index in Firebase Console.
            val querySnapshot = getUserCollection()
                .whereLessThan("readTimestamp", olderThan)
                .get()
                .await()

            val total = querySnapshot.size()
            if (total > 0) {
                // Process in chunks of 400 to stay safely under 500 limit
                querySnapshot.documents.chunked(400).forEach { chunk ->
                    val batch = firestore.batch() // Create new batch for each chunk
                    chunk.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
                logRepo?.logInfo("Deleted $total old items from Firestore", "Older than: $olderThan")
            }
        } catch (e: Exception) {
            logRepo?.logError("Failed to delete old items from Firestore", e)
            throw e
        }
    }
}
