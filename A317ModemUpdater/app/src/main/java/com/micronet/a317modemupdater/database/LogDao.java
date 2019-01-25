package com.micronet.a317modemupdater.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import java.util.List;

@Dao
public interface LogDao {

    @Query("SELECT * FROM logs")
    List<LogEntity> getAll();

    @Query("SELECT * FROM logs WHERE uploaded = 0")
    List<LogEntity> getAllWhereNotUploaded();

    @Query("SELECT * FROM logs WHERE id = :id")
    List<LogEntity> getById(int id);

    @Query("UPDATE logs set uploaded = 1 where id = :id")
    void updateLogStatus(int id);

    @Insert
    void insert(LogEntity log);

    @Query("DELETE FROM logs where id = :id")
    void deleteById(int id);
}
