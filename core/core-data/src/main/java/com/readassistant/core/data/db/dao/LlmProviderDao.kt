package com.readassistant.core.data.db.dao

import androidx.room.*
import com.readassistant.core.data.db.entity.LlmProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LlmProviderDao {
    @Query("SELECT * FROM llm_providers ORDER BY createdAt ASC")
    fun getAllProviders(): Flow<List<LlmProviderEntity>>

    @Query("SELECT * FROM llm_providers WHERE id = :id")
    suspend fun getProviderById(id: Long): LlmProviderEntity?

    @Query("SELECT * FROM llm_providers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProvider(): LlmProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: LlmProviderEntity): Long

    @Update
    suspend fun update(provider: LlmProviderEntity)

    @Delete
    suspend fun delete(provider: LlmProviderEntity)

    @Query("UPDATE llm_providers SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE llm_providers SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)
}
