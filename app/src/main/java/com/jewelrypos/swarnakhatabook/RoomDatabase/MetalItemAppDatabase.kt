package com.jewelrypos.swarnakhatabook.RoomDatabase

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jewelrypos.swarnakhatabook.Dao.MetalItemDao
import com.jewelrypos.swarnakhatabook.Entity.MetalItemEntity
import com.jewelrypos.swarnakhatabook.TypeConverter.MetalItemTypeConverter


@Database(entities = [MetalItemEntity::class], version = 1)
@TypeConverters(MetalItemTypeConverter::class)
abstract class MetalItemAppDatabase : RoomDatabase() {
    abstract fun itemDao(): MetalItemDao
}