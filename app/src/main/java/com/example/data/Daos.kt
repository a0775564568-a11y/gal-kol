package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun getAllMeasurements(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getById(id: Long): MeasurementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: MeasurementEntity): Long

    @Update
    suspend fun update(measurement: MeasurementEntity)

    @Delete
    suspend fun delete(measurement: MeasurementEntity)

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AnomalyDao {
    @Query("SELECT * FROM acoustic_anomalies ORDER BY timestamp DESC")
    fun getAllAnomalies(): Flow<List<AcousticAnomalyEventEntity>>

    @Query("SELECT * FROM acoustic_anomalies WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getAnomaliesSince(sinceTimestamp: Long): Flow<List<AcousticAnomalyEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anomaly: AcousticAnomalyEventEntity): Long

    @Query("DELETE FROM acoustic_anomalies WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM acoustic_anomalies")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM acoustic_anomalies")
    fun getCount(): Flow<Int>
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM live_sessions_log ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<LiveSessionSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: LiveSessionSummaryEntity): Long

    @Query("DELETE FROM live_sessions_log")
    suspend fun deleteAll()
}
