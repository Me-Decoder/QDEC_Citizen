package com.sujalkatariya.qdec.citizen.DAO

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sujalkatariya.qdec.citizen.complaints.ComplaintEntity

@Dao
interface ComplaintDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplaint(complaint: ComplaintEntity)

    @Query("SELECT * FROM complaints ORDER BY complaintId DESC")
    suspend fun getAllComplaints(): List<ComplaintEntity>

    @Query("SELECT * FROM complaints WHERE status = 'PENDING'")
    suspend fun getPendingComplaints(): List<ComplaintEntity>

    @Query("UPDATE complaints SET status = :status WHERE complaintId = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM complaints WHERE userId = :uid ORDER BY rowid DESC")
    fun getUserComplaints(uid: String): List<ComplaintEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(complaint: ComplaintEntity)


}