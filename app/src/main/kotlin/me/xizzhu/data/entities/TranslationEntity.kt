package me.xizzhu.android.joshua.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey val shortName: String,
    val name: String,
    val language: String,
    val size: Long
)