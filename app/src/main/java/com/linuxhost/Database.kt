package com.linuxhost

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class InstanceStatus {
    NOT_INSTALLED, INSTALLING, INSTALLED, RUNNING, STOPPED, ERROR, INTERRUPTED
}

enum class BackupType { FULL, SNAPSHOT }

enum class DownloadStatus { DOWNLOADING, COMPLETED, FAILED }

enum class EventType { INFO, WARNING, ERROR, REPAIR }

@Entity
data class UbuntuInstance(
    @PrimaryKey val id: String = "default",
    val name: String = "Ubuntu 26.04",
    val status: InstanceStatus = InstanceStatus.NOT_INSTALLED,
    val rootfsPath: String = "",
    val sizeBytes: Long = 0,
    val version: String = "26.04",
    val installedAt: Long? = null,
    val lastLaunchedAt: Long? = null,
)

@Entity
data class SystemEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EventType = EventType.INFO,
    val message: String,
    val details: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val resolved: Boolean = false,
)

@Entity
data class StorageSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rootfsBytes: Long = 0,
    val aptCacheBytes: Long = 0,
    val logsBytes: Long = 0,
    val tempBytes: Long = 0,
    val pythonPackagesBytes: Long = 0,
    val nodeModulesBytes: Long = 0,
    val gitProjectsBytes: Long = 0,
)

@Entity
data class Backup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0,
    val type: BackupType = BackupType.SNAPSHOT,
    val compressionMethod: String = "zstd",
    val filePath: String = "",
    val ubuntuVersion: String = "26.04",
)

@Entity
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tool: String = "",
    val url: String = "",
    val destinationPath: String = "",
    val sizeBytes: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
)

class Converters {
    @TypeConverter fun fromInstanceStatus(v: InstanceStatus): String = v.name
    @TypeConverter fun toInstanceStatus(v: String): InstanceStatus = InstanceStatus.valueOf(v)
    @TypeConverter fun fromBackupType(v: BackupType): String = v.name
    @TypeConverter fun toBackupType(v: String): BackupType = BackupType.valueOf(v)
    @TypeConverter fun fromDownloadStatus(v: DownloadStatus): String = v.name
    @TypeConverter fun toDownloadStatus(v: String): DownloadStatus = DownloadStatus.valueOf(v)
    @TypeConverter fun fromEventType(v: EventType): String = v.name
    @TypeConverter fun toEventType(v: String): EventType = EventType.valueOf(v)
}

@Dao
interface InstanceDao {
    @Query("SELECT * FROM UbuntuInstance WHERE id = 'default'")
    fun observe(): Flow<UbuntuInstance?>

    @Query("SELECT * FROM UbuntuInstance WHERE id = 'default'")
    suspend fun get(): UbuntuInstance?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(instance: UbuntuInstance)

    @Query("DELETE FROM UbuntuInstance")
    suspend fun deleteAll()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM SystemEvent ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<SystemEvent>>

    @Insert
    suspend fun insert(event: SystemEvent)

    @Query("UPDATE SystemEvent SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Long)
}

@Dao
interface StorageDao {
    @Query("SELECT * FROM StorageSnapshot ORDER BY timestamp DESC LIMIT 20")
    fun observeRecent(): Flow<List<StorageSnapshot>>

    @Insert
    suspend fun insert(snapshot: StorageSnapshot)
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM Backup ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Backup>>

    @Insert
    suspend fun insert(backup: Backup)

    @Query("DELETE FROM Backup WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM DownloadRecord ORDER BY startedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<DownloadRecord>>

    @Insert
    suspend fun insert(record: DownloadRecord)

    @Update
    suspend fun update(record: DownloadRecord)
}

@Database(
    entities = [UbuntuInstance::class, SystemEvent::class, StorageSnapshot::class, Backup::class, DownloadRecord::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class LinuxHostDatabase : RoomDatabase() {
    abstract fun instanceDao(): InstanceDao
    abstract fun eventDao(): EventDao
    abstract fun storageDao(): StorageDao
    abstract fun backupDao(): BackupDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile private var INSTANCE: LinuxHostDatabase? = null

        fun get(context: Context): LinuxHostDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LinuxHostDatabase::class.java,
                    "linuxhost.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
