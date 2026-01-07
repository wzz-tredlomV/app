package com.qrfiletransfer.receiver

import androidx.room.*
import com.qrfiletransfer.model.FileChunk
import kotlinx.coroutines.flow.Flow

@Dao
interface ChunkDao {
    @Query("SELECT * FROM file_chunks WHERE session_id = :sessionId ORDER BY chunk_index")
    suspend fun getBySessionId(sessionId: String): List<FileChunk>
    
    @Query("SELECT * FROM file_chunks WHERE session_id = :sessionId AND chunk_index = :index")
    suspend fun getBySessionAndIndex(sessionId: String, index: Int): FileChunk?
    
    @Query("SELECT * FROM file_chunks WHERE file_id = :fileId ORDER BY chunk_index")
    suspend fun getByFileId(fileId: String): List<FileChunk>
    
    @Query("SELECT COUNT(*) FROM file_chunks WHERE session_id = :sessionId")
    suspend fun countBySessionId(sessionId: String): Int
    
    @Query("SELECT COUNT(*) FROM file_chunks WHERE session_id = :sessionId AND is_received = 1")
    suspend fun countReceivedBySessionId(sessionId: String): Int
    
    @Query("SELECT COUNT(*) FROM file_chunks WHERE session_id = :sessionId AND is_verified = 1")
    suspend fun countVerifiedBySessionId(sessionId: String): Int
    
    @Query("SELECT COUNT(*) FROM file_chunks WHERE session_id = :sessionId AND is_transmitted = 1")
    suspend fun countTransmittedBySessionId(sessionId: String): Int
    
    @Query("SELECT * FROM file_chunks WHERE session_id = :sessionId AND is_received = 0 ORDER BY chunk_index")
    suspend fun getMissingChunks(sessionId: String): List<FileChunk>
    
    @Query("SELECT chunk_index FROM file_chunks WHERE session_id = :sessionId AND is_received = 1 ORDER BY chunk_index")
    suspend fun getReceivedIndices(sessionId: String): List<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(chunk: FileChunk)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(chunks: List<FileChunk>)
    
    @Update
    suspend fun update(chunk: FileChunk)
    
    @Query("UPDATE file_chunks SET is_received = 1 WHERE id = :chunkId")
    suspend fun markAsReceived(chunkId: String)
    
    @Query("UPDATE file_chunks SET is_verified = 1 WHERE id = :chunkId")
    suspend fun markAsVerified(chunkId: String)
    
    @Query("UPDATE file_chunks SET is_transmitted = 1 WHERE id = :chunkId")
    suspend fun markAsTransmitted(chunkId: String)
    
    @Query("UPDATE file_chunks SET is_received = 1 WHERE session_id = :sessionId AND chunk_index = :index")
    suspend fun markChunkAsReceived(sessionId: String, index: Int)
    
    @Query("UPDATE file_chunks SET is_verified = 1 WHERE session_id = :sessionId AND chunk_index = :index")
    suspend fun markChunkAsVerified(sessionId: String, index: Int)
    
    @Delete
    suspend fun delete(chunk: FileChunk)
    
    @Query("DELETE FROM file_chunks WHERE session_id = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)
    
    @Query("DELETE FROM file_chunks WHERE file_id = :fileId")
    suspend fun deleteByFileId(fileId: String)
    
    @Query("DELETE FROM file_chunks WHERE timestamp < :cutoffTime")
    suspend fun deleteOldChunks(cutoffTime: Long)
    
    @Query("SELECT DISTINCT file_id FROM file_chunks WHERE is_received = 1")
    suspend fun getReceivedFileIds(): List<String>
    
    @Query("SELECT * FROM file_chunks WHERE is_received = 0 AND session_id = :sessionId ORDER BY chunk_index LIMIT 1")
    suspend fun getNextMissingChunk(sessionId: String): FileChunk?
    
    @Query("SELECT MAX(chunk_index) FROM file_chunks WHERE session_id = :sessionId AND is_received = 1")
    suspend fun getLastReceivedIndex(sessionId: String): Int?
    
    @Query("SELECT MIN(chunk_index) FROM file_chunks WHERE session_id = :sessionId AND is_received = 0")
    suspend fun getFirstMissingIndex(sessionId: String): Int?
    
    @Transaction
    suspend fun updateChunkStatus(chunkId: String, received: Boolean, verified: Boolean) {
        if (received) {
            markAsReceived(chunkId)
        }
        if (verified) {
            markAsVerified(chunkId)
        }
    }
}
