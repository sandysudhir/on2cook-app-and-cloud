package com.invent.ontocook.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.invent.ontocook.multiple_connection.model.FilterData
import com.invent.ontocook.multiple_connection.model.database.LogDataDb
import io.reactivex.rxjava3.core.Flowable

@Dao
interface LogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(note: LogDataDb)

    @Update
    fun update(note: LogDataDb)

    @Query("DELETE FROM LOG WHERE macAddress =:macAddress AND date =:date")
    fun delete(macAddress: String, date: String)

    @Query("DELETE FROM LOG")
    fun deleteAll()

    @Query("SELECT * FROM LOG")
    fun getAllLog(): Flowable<List<LogDataDb>>

    @Query("SELECT * FROM LOG")
    fun getAllLogList(): List<LogDataDb>

    @Query("SELECT * FROM LOG")
    fun getAllLogRecipe(): List<LogDataDb>

    //    @Query("SELECT * FROM LOG WHERE date=:playListId LIMIT 10 OFFSET :offset")
    @Query("SELECT * FROM LOG WHERE macAddress =:macAddress LIMIT 10 OFFSET :offset ")
    fun getLog(offset: Int, macAddress: String): Flowable<List<LogDataDb>>

    @Query("SELECT * FROM LOG  WHERE macAddress =:macAddress AND date IN (:date)  LIMIT :limit OFFSET :offset")
    fun getLogByDate(
        date: List<String>,
        macAddress: String,
        limit: Int,
        offset: Int
    ): Flowable<List<LogDataDb>>

    @Query("SELECT * FROM LOG  WHERE date IN (:date)")
    fun getLogByDate(date: List<String>): Flowable<List<LogDataDb>>

    @Query("SELECT date FROM LOG WHERE macAddress =:macAddress group by date")
    fun getDate(macAddress: String): Flowable<List<String>>

    //    @Query("SELECT MAX(indPower),MAX(magPower),MAX(magTemp),MAX(coilTemp) FROM LOG")
//    fun getMaxIndPower(): Flowable<FilterData>
    @Query(
        "SELECT MIN(magCurrent) AS magCurrentStart ,MAX(magCurrent) AS magCurrentEnd,MIN(indCurrent) AS indCurrentStart ,MAX(indCurrent) AS indCurrentEnd" +
                ",MIN(magTemp) AS magTempStart ,MAX(magTemp) AS magTempEnd ,MIN(coilTemp) AS coilTempStart ,MAX(coilTemp) AS coilTempEnd " +
                ",MIN(ambientTemp) AS ambientTempStart ,MAX(ambientTemp) AS ambientTempEnd ,MIN(panTemp) AS panTempStart ,MAX(panTemp) AS panTempEnd" +
                ",MIN(pcbTemp) AS pcbTempStart ,MAX(pcbTemp) AS pcbTempEnd ,MIN(oilTemp) AS oilTempStart ,MAX(oilTemp) AS oilTempEnd ,MIN(deviceOnTime) AS deviceOnTimeStart ,MAX(deviceOnTime) AS deviceOnTimeEnd " +
                ",MIN(deviceOffTime) AS deviceOffTimeStart ,MAX(deviceOffTime) AS deviceOffTimeEnd " +
                ",MIN(indTime) AS indTimeStart ,MAX(indTime) AS indTimeEnd ,MIN(magTime) AS magTimeStart ,MAX(magTime) AS magTimeEnd " +
                "FROM LOG WHERE macAddress =:macAddress"
    )
    fun getMinMaxValue(macAddress: String): Flowable<FilterData>

    @Query(
        "SELECT * FROM LOG WHERE macAddress =:macAddress AND magCurrent BETWEEN :magCurrentStart  AND :magCurrentEnd AND indCurrent BETWEEN :indCurrentStart AND :indCurrentEnd " +
                "AND magTemp BETWEEN :magTempStart AND :magTempEnd AND coilTemp BETWEEN :coilTempStart AND :coilTempEnd " +
                "AND ambientTemp BETWEEN :ambientTempStart AND :ambientTempEnd AND panTemp BETWEEN :panTempStart AND :panTempEnd " +
                "AND pcbTemp BETWEEN :pcbTempStart AND :pcbTempEnd AND oilTemp BETWEEN :oilTempStart AND :oilTempEnd " +
                " AND indTime BETWEEN :indTimeStart AND :indTimeEnd " +
                "AND magTime BETWEEN :magTimeStart AND :magTimeEnd " +
                "AND deviceOnTime BETWEEN :deviceOnTimeStart AND :deviceOnTimeEnd AND deviceOffTime BETWEEN :deviceOffTimeStart AND :deviceOffTimeEnd " +
                "LIMIT 10 OFFSET :offset "
    )
    fun getFilterLog(
        macAddress: String,
        magCurrentStart: Float,
        magCurrentEnd: Float,
        indCurrentStart: Float,
        indCurrentEnd: Float,
        magTempStart: Float,
        magTempEnd: Float,
        coilTempStart: Float,
        coilTempEnd: Float,
        ambientTempStart: Float,
        ambientTempEnd: Float,
        panTempStart: Float,
        panTempEnd: Float,
        pcbTempStart: Float,
        pcbTempEnd: Float,
        oilTempStart: Float,
        oilTempEnd: Float,
        deviceOnTimeStart: Int,
        deviceOnTimeEnd: Int,
        deviceOffTimeStart: Int,
        deviceOffTimeEnd: Int,
        indTimeStart: Int,
        indTimeEnd: Int,
        magTimeStart: Int,
        magTimeEnd: Int,
        offset: Int
    ): Flowable<List<LogDataDb>>

    @Query(
        "SELECT * FROM LOG WHERE macAddress =:macAddress AND magCurrent BETWEEN :magCurrentStart  AND :magCurrentEnd AND indCurrent BETWEEN :indCurrentStart AND :indCurrentEnd " +
                "AND magTemp BETWEEN :magTempStart AND :magTempEnd AND coilTemp BETWEEN :coilTempStart AND :coilTempEnd " +
                "AND ambientTemp BETWEEN :ambientTempStart AND :ambientTempEnd AND panTemp BETWEEN :panTempStart AND :panTempEnd " +
                "AND pcbTemp BETWEEN :pcbTempStart AND :pcbTempEnd AND oilTemp BETWEEN :oilTempStart AND :oilTempEnd " +
                " AND indTime BETWEEN :indTimeStart AND :indTimeEnd " +
                "AND magTime BETWEEN :magTimeStart AND :magTimeEnd " +
                "AND deviceOnTime BETWEEN :deviceOnTimeStart AND :deviceOnTimeEnd AND deviceOffTime BETWEEN :deviceOffTimeStart AND :deviceOffTimeEnd "
    )
    fun getAllFilterLog(
        macAddress: String,
        magCurrentStart: Float,
        magCurrentEnd: Float,
        indCurrentStart: Float,
        indCurrentEnd: Float,
        magTempStart: Float,
        magTempEnd: Float,
        coilTempStart: Float,
        coilTempEnd: Float,
        ambientTempStart: Float,
        ambientTempEnd: Float,
        panTempStart: Float,
        panTempEnd: Float,
        pcbTempStart: Float,
        pcbTempEnd: Float,
        oilTempStart: Float,
        oilTempEnd: Float,
        deviceOnTimeStart: Int,
        deviceOnTimeEnd: Int,
        deviceOffTimeStart: Int,
        deviceOffTimeEnd: Int,
        indTimeStart: Int,
        indTimeEnd: Int,
        magTimeStart: Int,
        magTimeEnd: Int
    ): List<LogDataDb>

    @Query(
        "SELECT * FROM LOG WHERE macAddress =:macAddress AND date IN (:date) AND magCurrent BETWEEN :magCurrentStart  AND :magCurrentEnd AND indCurrent BETWEEN :indCurrentStart AND :indCurrentEnd " +
                "AND magTemp BETWEEN :magTempStart AND :magTempEnd AND coilTemp BETWEEN :coilTempStart AND :coilTempEnd " +
                "AND ambientTemp BETWEEN :ambientTempStart AND :ambientTempEnd AND panTemp BETWEEN :panTempStart AND :panTempEnd " +
                "AND pcbTemp BETWEEN :pcbTempStart AND :pcbTempEnd AND oilTemp BETWEEN :oilTempStart AND :oilTempEnd " +
                " AND indTime BETWEEN :indTimeStart AND :indTimeEnd " +
                "AND magTime BETWEEN :magTimeStart AND :magTimeEnd " +
                "AND deviceOnTime BETWEEN :deviceOnTimeStart AND :deviceOnTimeEnd AND deviceOffTime BETWEEN :deviceOffTimeStart AND :deviceOffTimeEnd " +
                "LIMIT :limit OFFSET :offset "
    )
    fun getWeekFilterLog(
        macAddress: String,
        date: List<String>,
        magCurrentStart: Float,
        magCurrentEnd: Float,
        indCurrentStart: Float,
        indCurrentEnd: Float,
        magTempStart: Float,
        magTempEnd: Float,
        coilTempStart: Float,
        coilTempEnd: Float,
        ambientTempStart: Float,
        ambientTempEnd: Float,
        panTempStart: Float,
        panTempEnd: Float,
        pcbTempStart: Float,
        pcbTempEnd: Float,
        oilTempStart: Float,
        oilTempEnd: Float,
        deviceOnTimeStart: Int,
        deviceOnTimeEnd: Int,
        deviceOffTimeStart: Int,
        deviceOffTimeEnd: Int,
        indTimeStart: Int,
        indTimeEnd: Int,
        magTimeStart: Int,
        magTimeEnd: Int,
        limit: Int,
        offset: Int
    ): Flowable<List<LogDataDb>>

    @Query(
        "SELECT * FROM LOG WHERE macAddress =:macAddress AND date IN (:date) AND magCurrent BETWEEN :magCurrentStart  AND :magCurrentEnd AND indCurrent BETWEEN :indCurrentStart AND :indCurrentEnd " +
                "AND magTemp BETWEEN :magTempStart AND :magTempEnd AND coilTemp BETWEEN :coilTempStart AND :coilTempEnd " +
                "AND ambientTemp BETWEEN :ambientTempStart AND :ambientTempEnd AND panTemp BETWEEN :panTempStart AND :panTempEnd " +
                "AND pcbTemp BETWEEN :pcbTempStart AND :pcbTempEnd AND oilTemp BETWEEN :oilTempStart AND :oilTempEnd " +
                " AND indTime BETWEEN :indTimeStart AND :indTimeEnd " +
                "AND magTime BETWEEN :magTimeStart AND :magTimeEnd " +
                "AND deviceOnTime BETWEEN :deviceOnTimeStart AND :deviceOnTimeEnd AND deviceOffTime BETWEEN :deviceOffTimeStart AND :deviceOffTimeEnd "
    )
    fun getAllWeekFilterLog(
        macAddress: String,
        date: List<String>,
        magCurrentStart: Float,
        magCurrentEnd: Float,
        indCurrentStart: Float,
        indCurrentEnd: Float,
        magTempStart: Float,
        magTempEnd: Float,
        coilTempStart: Float,
        coilTempEnd: Float,
        ambientTempStart: Float,
        ambientTempEnd: Float,
        panTempStart: Float,
        panTempEnd: Float,
        pcbTempStart: Float,
        pcbTempEnd: Float,
        oilTempStart: Float,
        oilTempEnd: Float,
        deviceOnTimeStart: Int,
        deviceOnTimeEnd: Int,
        deviceOffTimeStart: Int,
        deviceOffTimeEnd: Int,
        indTimeStart: Int,
        indTimeEnd: Int,
        magTimeStart: Int,
        magTimeEnd: Int
    ): List<LogDataDb>

    @Query("SELECT * FROM LOG  WHERE macAddress =:macAddress AND date IN (:date)")
    fun getAllLogByDate(date: List<String>, macAddress: String): List<LogDataDb>

}