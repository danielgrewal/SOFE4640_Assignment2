package com.example.assign2_geocode;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

public class DatabaseActivity extends AppCompatActivity {
    private DatabaseHelper dbHelper;
    private Button buttonNewEntry;
    private Button buttonBackToMain;
    private ListView listViewRecords;
    private ArrayAdapter<String> adapter;
    private SQLiteDatabase db;
    private int id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database);

        buttonNewEntry = findViewById(R.id.buttonNewEntry);
        buttonBackToMain = findViewById(R.id.buttonBackToMain);
        listViewRecords = findViewById(R.id.listViewRecords);

        // Initialize the database
        dbHelper = new DatabaseHelper(this);
        db = dbHelper.getWritableDatabase();

        // Initialize the ArrayAdapter for the ListView
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listViewRecords.setAdapter(adapter);

        // Set an item click listener for the ListView
        listViewRecords.setOnItemClickListener((parent, view, position, id) -> {
            // Retrieve the selected item's data from the adapter
            String selectedRecord = adapter.getItem(position);
            // Retrieve the ID from the selected record
            int recordId = extractRecordId(selectedRecord);

            // Create an Intent to open the EditRecordActivity and pass the selected record data
            Intent intent = new Intent(DatabaseActivity.this, EditRecordActivity.class);
            intent.putExtra("selectedRecord", selectedRecord);
            System.out.println("DEBUG - FROM DATABASE ACTIVITY - id is " + recordId);
            intent.putExtra("recordId", recordId);

            // Start the EditRecordActivity
            startActivity(intent);
        });

        // Add an OnClickListener to the New Entry button
        buttonNewEntry.setOnClickListener(v -> {
            // Create an Intent to open the EditRecordActivity for a new entry
            Intent intent = new Intent(DatabaseActivity.this, EditRecordActivity.class);
            // Pass a default recordId (e.g., -1) to indicate a new entry
            intent.putExtra("recordId", -1);
            // Start the EditRecordActivity for a new entry
            startActivity(intent);
        });

        // Set an onClick listener for the Back button
        buttonBackToMain.setOnClickListener(v -> {
            onBackPressed(); // Navigate back to the previous screen
        });

        // Update the adapter with data from the database
        updateListView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update the adapter with data from the database when the activity resumes
        // this will refresh with any changes made from the EditRecordActivity
        updateListView();
    }

    // Update the ListView with data from the database
    // use this to show the rows in the database so user can modify/delete
    private void updateListView() {
        adapter.clear(); // Clear the existing items in the adapter

        if (db != null) {
            String[] projection = {
                    DatabaseHelper.COLUMN_ID,
                    DatabaseHelper.COLUMN_ADDRESS,
                    DatabaseHelper.COLUMN_LATITUDE,
                    DatabaseHelper.COLUMN_LONGITUDE
            };

            Cursor cursor = db.query(
                    DatabaseHelper.TABLE_NAME,
                    projection,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            if (cursor != null) {
                Log.d("Cursor Rows", "Number of rows in cursor: " + cursor.getCount());

                while (cursor.moveToNext()) {
                    // Retrieve values from the cursor using column indices
                    int idColumnIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_ID);
                    int addressColumnIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_ADDRESS);
                    int latitudeColumnIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_LATITUDE);
                    int longitudeColumnIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_LONGITUDE);

                    if (addressColumnIndex != -1 && latitudeColumnIndex != -1 && longitudeColumnIndex != -1) {
                        //int id = cursor.getInt(idColumnIndex);
                        id = cursor.getInt(idColumnIndex);
                        String address = cursor.getString(addressColumnIndex);
                        double latitude = cursor.getDouble(latitudeColumnIndex);
                        double longitude = cursor.getDouble(longitudeColumnIndex);

                        String recordName = "ID: " + id + "\nAddress: " + address + "\nLatitude: " + latitude + "\nLongitude: " + longitude;
                        adapter.add(recordName);
                    } else {
                        Log.e("Column Index Error", "One or more columns not found in cursor.");
                    }
                }
                cursor.close(); // Close the cursor when done
            }
            adapter.notifyDataSetChanged();
        } else {
            Log.e("Database Error", "Database is not initialized.");
        }
    }

    // close the database when the activity is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }

    // Helper function to extract the ID from the selected record string
    private int extractRecordId(String selectedRecord) {
        // This is a simple example; you should implement your own logic based on your record format
        String[] parts = selectedRecord.split("\n");
        if (parts.length > 0) {
            String idPart = parts[0].replace("ID: ", "");
            try {
                return Integer.parseInt(idPart);
            } catch (NumberFormatException e) {
                // Handle the conversion error
            }
        }
        return -1; // Return a default value if ID extraction fails
    }
}
