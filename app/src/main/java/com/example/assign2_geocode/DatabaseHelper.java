package com.example.assign2_geocode;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    //private static DatabaseHelper db;
    public static final String DATABASE_NAME = "location_database";
    public static final int DATABASE_VERSION = 1;
    public static final String TABLE_NAME = "location";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_ADDRESS = "address";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        StringBuilder sql;
        sql = new StringBuilder()
                .append("CREATE TABLE ")
                .append(TABLE_NAME)
                .append("(")
                .append(COLUMN_ID)
                .append(" INTEGER PRIMARY KEY AUTOINCREMENT, ")
                .append(COLUMN_ADDRESS)
                .append(" TEXT, ")
                .append(COLUMN_LATITUDE)
                .append(" REAL, ")
                .append(COLUMN_LONGITUDE)
                .append(" REAL)");

        db.execSQL(sql.toString());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database schema upgrades if needed
    }

    // Update an existing record in the database
    public int updateRecord(SQLiteDatabase db, int recordId, String address, double latitude, double longitude) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_ADDRESS, address);
        values.put(COLUMN_LATITUDE, latitude);
        values.put(COLUMN_LONGITUDE, longitude);

        // Define the WHERE clause to identify the record to update based on the record ID
        String whereClause = COLUMN_ID + " = ?";
        String[] whereArgs = {String.valueOf(recordId)};

        // Perform the update operation
        return db.update(TABLE_NAME, values, whereClause, whereArgs);
    }

    // Insert a new record into the database and return the newly generated record ID
    public long insertRecord(SQLiteDatabase db, String address, double latitude, double longitude) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_ADDRESS, address);
        values.put(COLUMN_LATITUDE, latitude);
        values.put(COLUMN_LONGITUDE, longitude);

        // Perform the insert operation
        return db.insert(TABLE_NAME, null, values);
    }

    // Delete a record from the database
    public boolean deleteRecord(SQLiteDatabase db, int recordId) {
        // Define the WHERE clause to identify the record to delete based on the record ID
        String whereClause = COLUMN_ID + " = ?";
        String[] whereArgs = { String.valueOf(recordId) };

        // Perform the delete operation
        int rowsDeleted = db.delete(TABLE_NAME, whereClause, whereArgs);

        // Return true if at least one row was deleted, indicating success
        return rowsDeleted > 0;
    }
}
