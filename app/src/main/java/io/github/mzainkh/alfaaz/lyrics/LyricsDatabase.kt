package io.github.mzainkh.alfaaz.lyrics

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** A row with no lyrics and `instrumental = false` records that LRCLIB had nothing. */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackKey: String,
    val synced: String?,
    val plain: String?,
    val instrumental: Boolean,
    val fetchedAt: Long,
    val offsetMs: Long = 0,
)

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE trackKey = :trackKey")
    suspend fun find(trackKey: String): LyricsEntity?

    @Upsert
    suspend fun upsert(entity: LyricsEntity)

    @Query("UPDATE lyrics SET offsetMs = :offsetMs WHERE trackKey = :trackKey")
    suspend fun setOffset(trackKey: String, offsetMs: Long)
}

@Database(entities = [LyricsEntity::class], version = 1, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyrics(): LyricsDao
}
