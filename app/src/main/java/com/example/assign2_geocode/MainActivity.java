package com.example.assign2_geocode;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private Button convertButton;
    private Button fileButton;
    private TextView filePreview;
    private TextView addressPreview;
    private EditText addressInput;
    private Button queryButton;
    private Button databaseButton;
    private TextView latlongResult;
    private GeofencingClient geofencingClient;
    private Uri selectedFileUri; // Store the selected lat/long coordinate file's URI
    private DatabaseHelper dbHelper;
    // using this keep UI responsive while file is being processed and written to database
    // I was getting lots of time out errors in my android emulator because the UI was
    // not responding after 5000 ms when processing the file etc.
    // using multi-threading allows the UI thread to stay responsive
    private ExecutorService executorService = Executors.newFixedThreadPool(4);

    // Create an ActivityResultLauncher to handle file picking
    private ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Get the selected file's URI
                    Intent data = result.getData();
                    if (data != null) {
                        selectedFileUri = data.getData();
                        // Display the selected file's contents in the filePreview
                        String fileContents = readCoordinatesFromFile(selectedFileUri).toString();
                        filePreview.setText(fileContents);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // init the dbHelper instance
        dbHelper = new DatabaseHelper(this);

        // init the componenets
        fileButton = findViewById(R.id.fileButton);
        filePreview = findViewById(R.id.fileTextView);
        filePreview.setMovementMethod(new ScrollingMovementMethod());
        convertButton = findViewById(R.id.convertButton);
        addressPreview = findViewById(R.id.addressTextView);
        addressPreview.setMovementMethod(new ScrollingMovementMethod());
        addressInput = findViewById(R.id.addressTextInput);
        queryButton = findViewById(R.id.queryButton);
        latlongResult = findViewById(R.id.latlongTextView);
        databaseButton = findViewById(R.id.databaseButton);
        geofencingClient = LocationServices.getGeofencingClient(this);

        // Add a click listener for the fileButton
        fileButton.setOnClickListener(v -> {
            // Open the file picker using the ActivityResultLauncher
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*"); // Allow all file types
            filePickerLauncher.launch(intent);
        });

        convertButton.setOnClickListener(v -> {
            addressPreview.setText("Loading...");
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            } else {
                if (selectedFileUri != null) {
                    // Read coordinates from the selected file as a list of strings in a worker thread
                    executorService.execute(() -> {
                        List<String> coordinatesList = readCoordinatesFromFile(selectedFileUri);
                        StringBuilder addressStringBuilder = new StringBuilder();

                        for (String line : coordinatesList) {
                            String[] coordinates = line.split(",");

                            if (coordinates.length >= 2) {
                                String latitudeStr = coordinates[0].trim();
                                String longitudeStr = coordinates[1].trim();

                                try {
                                    double latitude = Double.parseDouble(latitudeStr);
                                    double longitude = Double.parseDouble(longitudeStr);
                                    LatLng latLng = new LatLng(latitude, longitude);
                                    String address = geocodeLatLng(latLng);
                                    addressStringBuilder.append("Latitude: ").append(latitude).append(", Longitude: ").append(longitude).append("\n").append("Address: ").append(address).append("\n\n");
                                } catch (NumberFormatException e) {
                                    addressStringBuilder.append("Invalid coordinates: ").append(line).append("\n");
                                }
                            }
                        }

                        // Update the UI on the main thread
                        runOnUiThread(() -> {
                            addressPreview.setText(addressStringBuilder.toString());
                            // Perform database insertions in another worker thread
                            executorService.execute(() -> {
                                for (String line : coordinatesList) {
                                    String[] coordinates = line.split(",");
                                    if (coordinates.length >= 2) {
                                        String latitudeStr = coordinates[0].trim();
                                        String longitudeStr = coordinates[1].trim();
                                        try {
                                            double latitude = Double.parseDouble(latitudeStr);
                                            double longitude = Double.parseDouble(longitudeStr);
                                            String address = geocodeLatLng(new LatLng(latitude, longitude));
                                            saveLocationToDatabase(address, latitude, longitude);
                                        } catch (NumberFormatException e) {
                                            // Handle invalid coordinates
                                        }
                                    }
                                }
                            });
                        });
                    });
                } else {
                    Toast.makeText(MainActivity.this, "Please select a file first.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // click listener for the add, delete and update entries button
        // opens a new activity where user can modify database entries
        databaseButton.setOnClickListener(v -> {
            // Create an Intent to start 'DatabaseActivity'
            Intent intent = new Intent(MainActivity.this, DatabaseActivity.class);
            startActivity(intent);
        });

        queryButton.setOnClickListener(v -> {
            String addressToQuery = addressInput.getText().toString().trim();
            if (!addressToQuery.isEmpty()) {
                // Perform the database query and display results
                queryDatabaseForLocation(addressToQuery);
            } else {
                Toast.makeText(MainActivity.this, "Please enter an address to query.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String geocodeLatLng(LatLng latLng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (!addresses.isEmpty()) {
                Address address = addresses.get(0);
                return address.getAddressLine(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Address not found";
    }

    // read the file coordinates and put them into a list for processing
    private List<String> readCoordinatesFromFile(Uri fileUri) {
        List<String> coordinatesList = new ArrayList<>();
        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            if (inputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    coordinatesList.add(line);
                }
                inputStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return coordinatesList;
    }

    // required for geocoding intent permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                convertButton.performClick();
            } else {
                Toast.makeText(this, "Location permission is required to convert coordinates to an address.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Method to save location data to the database
    private void saveLocationToDatabase(String address, double latitude, double longitude) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_ADDRESS, address);
        values.put(DatabaseHelper.COLUMN_LATITUDE, latitude);
        values.put(DatabaseHelper.COLUMN_LONGITUDE, longitude);

        db.insert(DatabaseHelper.TABLE_NAME, null, values);
    }

    private void queryDatabaseForLocation(String partialAddress) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String[] projection = {
                DatabaseHelper.COLUMN_ADDRESS,
                DatabaseHelper.COLUMN_LATITUDE,
                DatabaseHelper.COLUMN_LONGITUDE
        };

        // returns the query for address using LIKE
        // in case user does not enter the address exactly
        String selection = DatabaseHelper.COLUMN_ADDRESS + " LIKE ?";
        String[] selectionArgs = new String[]{"%" + partialAddress + "%"};

        Cursor cursor = db.query(
                DatabaseHelper.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            StringBuilder resultBuilder = new StringBuilder();

            int addressColumnIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_ADDRESS);
            int latitudeColumnIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_LATITUDE);
            int longitudeColumnIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_LONGITUDE);

            do {
                String address = cursor.getString(addressColumnIndex);
                double latitude = cursor.getDouble(latitudeColumnIndex);
                double longitude = cursor.getDouble(longitudeColumnIndex);

                resultBuilder.append("Address: ").append(address).append("\n");
                resultBuilder.append("Latitude: ").append(latitude).append("\n");
                resultBuilder.append("Longitude: ").append(longitude).append("\n\n");
            } while (cursor.moveToNext());
            cursor.close();

            // Display the results
            latlongResult.setText(resultBuilder.toString());
        } else {
            // Address not found in the database
            latlongResult.setText("Address not found in the database.");
        }
    }
}
