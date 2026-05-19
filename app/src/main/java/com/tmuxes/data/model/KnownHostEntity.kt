package com.tmuxes.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "known_hosts",
    indices = [
        Index(value = ["hostname", "port", "key_type"], unique = true)
    ]
)
data class KnownHostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val hostname: String,

    val port: Int,

    @ColumnInfo(name = "key_type")
    val keyType: String,

    val fingerprint: String,

    @ColumnInfo(name = "added_at")
    val addedAt: Long,

    val trusted: Boolean
)
