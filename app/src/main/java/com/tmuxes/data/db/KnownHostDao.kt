package com.tmuxes.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.tmuxes.data.model.KnownHostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownHostDao {

    @Query("SELECT * FROM known_hosts ORDER BY added_at DESC")
    fun getAll(): Flow<List<KnownHostEntity>>

    @Query("SELECT * FROM known_hosts WHERE hostname = :hostname AND port = :port")
    suspend fun findByHostPort(hostname: String, port: Int): List<KnownHostEntity>

    @Upsert
    suspend fun upsert(knownHost: KnownHostEntity): Long

    @Delete
    suspend fun delete(knownHost: KnownHostEntity)

    @Query("DELETE FROM known_hosts WHERE hostname = :hostname AND port = :port")
    suspend fun deleteByHostPort(hostname: String, port: Int)
}
