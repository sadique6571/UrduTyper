package me.nomi.urdutyper.domain.entity

data class StorageUsage(
    val usedBytes: Long,
    val totalBytes: Long,
    val limitBytes: Long
)
