package com.micronet.a317modemupdater.database;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

@Entity(tableName = "logs")
public class LogEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String dt;
    public String summary;
    public boolean pass;
    public boolean uploaded;

    public LogEntity(String summary, boolean uploaded, boolean pass) {
        this.id = 0;
        this.dt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime());
        this.summary = summary;
        this.uploaded = uploaded;
        this.pass = pass;
    }
}
