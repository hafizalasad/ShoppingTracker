package com.example.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopName: String,
    val amount: Double,
    val date: Long, // timestamp
    val imagePath: String? = null,
    val isPendingAnalysis: Boolean = false,
    val note: String = ""
)
