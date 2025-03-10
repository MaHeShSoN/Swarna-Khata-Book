package com.jewelrypos.swarnakhatabook.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jewelrypos.swarnakhatabook.Entity.MetalItemEntity

@Dao
interface MetalItemDao {
    @Insert
    suspend fun insertItem(item: MetalItemEntity)

    @Insert
    suspend fun insertItems(items: List<MetalItemEntity>)

    @Query("SELECT * FROM items")
    suspend fun getAllItems(): List<MetalItemEntity>

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()
}