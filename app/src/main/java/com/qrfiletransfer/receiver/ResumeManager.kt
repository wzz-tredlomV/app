package com.qrfiletransfer.receiver

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.qrfiletransfer.model.FileChunk
import com.qrfiletransfer.model.TransferSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 断点续传管理器
 * 管理传输状态，支持从断点恢复传输
 */
class ResumeManager(context: Context) {
    
    companion object {
        private const val DATABASE_NAME = "transfer_resume.db"
    }
    
    private val database = Room.databaseBuilder(
        context,
        ResumeDatabase::class.java,
        DATABASE_NAME
    ).fallbackToDestructiveMigration().build()
    
    private val sessionDao = database.sessionDao()
    private val chunkDao = database.chunkDao()
    
    /**
     * 保存传输会话
     */
    suspend fun saveSession(session: TransferSession) = withContext(Dispatchers.IO) {
        sessionDao.insertOrUpdate(session)
    }
    
    /**
     * 保存接收到的块
     */
    suspend fun saveChunk(chunk: FileChunk) = withContext(Dispatchers.IO) {
        chunkDao.insertOrUpdate(chunk)
    }
    
    /**
     * 获取会话
     */
    suspend fun getSession(sessionId: String): TransferSession? = withContext(Dispatchers.IO) {
        sessionDao.getById(sessionId)
    }
    
    /**
     * 获取会话的所有块
     */
    suspend fun getChunksForSession(sessionId: String): List<FileChunk> = withContext(Dispatchers.IO) {
        chunkDao.getBySessionId(sessionId)
    }
    
    /**
     * 检查是否已接收过某块
     */
    suspend fun isChunkReceived(sessionId: String, chunkIndex: Int): Boolean = withContext(Dispatchers.IO) {
        chunkDao.getBySessionAndIndex(sessionId, chunkIndex) != null
    }
    
    /**
     * 获取传输进度
     */
    suspend fun getProgress(sessionId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val session = sessionDao.getById(sessionId)
        val chunks = chunkDao.getBySessionId(sessionId)
        
        Pair(chunks.size, session?.totalChunks ?: 0)
    }
    
    /**
     * 清理完成的会话
     */
    suspend fun cleanupCompletedSessions() = withContext(Dispatchers.IO) {
        val sessions = sessionDao.getAll()
        
        for (session in sessions) {
            val chunks = chunkDao.getBySessionId(session.id)
            if (chunks.size >= session.totalChunks) {
                sessionDao.delete(session)
                chunkDao.deleteBySessionId(session.id)
            }
        }
    }
    
    /**
     * 获取需要恢复的会话
     */
    suspend fun getResumableSessions(): List<TransferSession> = withContext(Dispatchers.IO) {
        sessionDao.getIncompleteSessions()
    }
    
    /**
     * 恢复传输
     */
    suspend fun resumeTransfer(sessionId: String): ResumeResult = withContext(Dispatchers.IO) {
        val session = sessionDao.getById(sessionId) ?: return@withContext ResumeResult.Error("会话不存在")
        val chunks = chunkDao.getBySessionId(sessionId)
        
        val receivedIndices = chunks.map { it.chunkIndex }.toSet()
        val missingIndices = mutableListOf<Int>()
        
        for (i in 0 until session.totalChunks) {
            if (!receivedIndices.contains(i)) {
                missingIndices.add(i)
            }
        }
        
        if (missingIndices.isEmpty()) {
            return@withContext ResumeResult.Error("会话已完成，无需恢复")
        }
        
        ResumeResult.Success(
            session = session,
            receivedChunks = chunks,
            missingIndices = missingIndices,
            progress = Pair(chunks.size, session.totalChunks)
        )
    }
    
    /**
     * 删除会话
     */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        sessionDao.deleteById(sessionId)
        chunkDao.deleteBySessionId(sessionId)
    }
    
    sealed class ResumeResult {
        data class Success(
            val session: TransferSession,
            val receivedChunks: List<FileChunk>,
            val missingIndices: List<Int>,
            val progress: Pair<Int, Int>
        ) : ResumeResult()
        
        data class Error(val message: String) : ResumeResult()
    }
}
