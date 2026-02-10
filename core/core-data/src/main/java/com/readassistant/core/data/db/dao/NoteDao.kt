package com.readassistant.core.data.db.dao

import androidx.room.*
import com.readassistant.core.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE highlightId = :highlightId")
    fun getNotesByHighlight(highlightId: Long): Flow<List<NoteEntity>>

    @Transaction
    @Query("SELECT * FROM notes WHERE contentType = :type AND contentId = :id ORDER BY createdAt DESC")
    fun getNotesByContent(type: String, id: Long): Flow<List<com.readassistant.core.data.db.entity.NoteWithHighlight>>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}
