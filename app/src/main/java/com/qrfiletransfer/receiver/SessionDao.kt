package com.qrfiletransfer.receiver

import androidx.room.*
import com.qrfiletransfer.model.TransferSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM transfer_sessions WHERE id = :id")
    suspend fun getById(id: String): TransferSession?
    
    @Query("SELECT * FROM transfer_sessions WHERE status != 'COMPLETED' AND status != 'CANCELLED'")
    suspend fun getIncompleteSessions(): List<TransferSession>
    
    @Query("SELECT * FROM transfer_sessions WHERE file_id = :fileId")
    suspend fun getByFileId(fileId: String): List<TransferSession>
    
    @Query("SELECT * FROM transfer_sessions")
    suspend fun getAll(): List<TransferSession>
    
    @Query("SELECT * FROM transfer_sessions ORDER BY start_time DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int = 50): List<TransferSession>
    
    @Query("SELECT COUNT(*) FROM transfer_sessions WHERE status = 'COMPLETED'")
    suspend fun getCompletedCount(): Int
    
    @Query("SELECT COUNT(*) FROM transfer_sessions WHERE status = 'FAILED'")
    suspend fun getFailedCount(): Int
    
    @Query("SELECT SUM(file_size) FROM transfer_sessions WHERE status = 'COMPLETED'")
    suspend fun getTotalTransferredSize(): Long?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(session: TransferSession)
    
    @Update
    suspend fun update(session: TransferSession)
    
    @Delete
    suspend fun delete(session: TransferSession)
    
    @Query("DELETE FROM transfer_sessions WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM transfer_sessions WHERE status = 'COMPLETED' AND end_time < :cutoffTime")
    suspend fun deleteOldCompletedSessions(cutoffTime: Long)
    
    @Query("DELETE FROM transfer_sessions WHERE status = 'FAILED' AND end_time < :cutoffTime")
    suspend fun deleteOldFailedSessions(cutoffTime: Long)
    
    @Query("UPDATE transfer_sessions SET chunks_transmitted = :count WHERE id = :sessionId")
    suspend fun updateTransmittedChunks(sessionId: String, count: Int)
    
    @Query("UPDATE transfer_sessions SET chunks_received = :count WHERE id = :sessionId")
    suspend fun updateReceivedChunks(sessionId: String, count: Int)
    
    @Query("UPDATE transfer_sessions SET status = :status WHERE id = :sessionId")
    suspend fun updateStatus(sessionId: String, status: String)
    
    @Query("UPDATE transfer_sessions SET end_time = :endTime WHERE id = :sessionId")
    suspend fun updateEndTime(sessionId: String, endTime: Long)
    
    @Query("UPDATE transfer_sessions SET error_message = :errorMessage WHERE id = :sessionId")
    suspend fun updateErrorMessage(sessionId: String, errorMessage: String)
    
    @Query("UPDATE transfer_sessions SET retry_count = retry_count + 1 WHERE id = :sessionId")
    suspend fun incrementRetryCount(sessionId: String)
    
    @Query("SELECT COUNT(*) FROM transfer_sessions WHERE status = :status")
    suspend fun countByStatus(status: String): Int
    
    @Query("SELECT * FROM transfer_sessions WHERE resume_token = :resumeToken")
    suspend fun getByResumeToken(resumeToken: String): TransferSession?
}
