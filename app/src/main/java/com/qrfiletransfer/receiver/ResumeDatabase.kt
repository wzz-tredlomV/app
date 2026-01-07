package com.qrfiletransfer.receiver

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.qrfiletransfer.model.FileChunk
import com.qrfiletransfer.model.TransferSession
import android.content.Context

@Database(
    entities = [TransferSession::class, FileChunk::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ResumeDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun chunkDao(): ChunkDao
    
    companion object {
        @Volatile
        private var INSTANCE: ResumeDatabase? = null
        
        fun getDatabase(context: Context): ResumeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ResumeDatabase::class.java,
                    "transfer_resume.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
