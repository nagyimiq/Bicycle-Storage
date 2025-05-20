package com.example.bicyclestorage;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap myMap;
    private Toolbar toolbar;
    private TextView toolbarText;
    private Button resetButton;
    private Button actionButton;
    private boolean isToolbarVisible = false;
    private static final LatLng BICYCLE_STORAGE = new LatLng(47.543277, 21.640391);
    private static final float DEFAULT_ZOOM = 15f;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int REQUEST_CHECK_SETTINGS = 2;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private AppDatabase database;
    private List<Marker> noteMarkers = new ArrayList<>();
    private Marker storageMarker;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        try {
            database = AppDatabase.getInstance(this);
            Log.d("MainActivity", "Database initialized successfully");
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to initialize database: " + e.getMessage());
            Toast.makeText(this, "Database error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (!checkPlayServices()) {
            Log.e("MainActivity", "Google Play Services not available");
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.dark_blue, getTheme()));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        toolbar = findViewById(R.id.toolbar2);
        toolbarText = findViewById(R.id.toolbar_text);
        resetButton = findViewById(R.id.reset_button);
        actionButton = findViewById(R.id.action_button);

        // Toolbar kezdeti inicializálása
        toolbar.post(() -> {
            toolbar.setTranslationY(-toolbar.getHeight());
            toolbar.setVisibility(View.INVISIBLE);
        });

        resetButton.setOnClickListener(v -> resetMapPosition());
        actionButton.setOnClickListener(v -> centerOnMyLocation());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.e("MainActivity", "Map fragment not found");
            Toast.makeText(this, "Map initialization failed", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        myMap = googleMap;

        try {
            storageMarker = myMap.addMarker(new MarkerOptions()
                    .position(BICYCLE_STORAGE)
                    .icon(getResizedMarkerIcon(R.drawable.bicycle, 150, 150)));
            storageMarker.setTag("storage");
            myMap.moveCamera(CameraUpdateFactory.newLatLngZoom(BICYCLE_STORAGE, DEFAULT_ZOOM));
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to initialize map markers: " + e.getMessage());
            Toast.makeText(this, "Map marker error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        checkLocationSettings();

        myMap.setOnMarkerClickListener(marker -> {
            if ("storage".equals(marker.getTag())) {
                isToolbarVisible = !isToolbarVisible;
                if (isToolbarVisible) {
                    toolbar.setVisibility(View.VISIBLE);
                    toolbar.setTranslationY(-toolbar.getHeight());
                    toolbar.animate()
                            .translationY(0)
                            .setDuration(300)
                            .setListener(null)
                            .start();

                    int occupied = 3;
                    String message = "In use: " + occupied + "/6";
                    toolbarText.setText(message);
                } else {
                    toolbar.animate()
                            .translationY(-toolbar.getHeight())
                            .setDuration(300)
                            .withEndAction(() -> toolbar.setVisibility(View.INVISIBLE))
                            .start();
                }
                return true;
            } else {
                marker.showInfoWindow();
                Log.d("MainActivity", "Note marker clicked: " + marker.getTitle());
                return true;
            }
        });

        myMap.setOnInfoWindowClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof Integer) {
                int noteId = (Integer) tag;
                Log.d("MainActivity", "InfoWindow clicked for note ID: " + noteId);
                try {
                    StorageNote note = database.appDao().getNoteById(noteId);
                    if (note != null) {
                        database.appDao().deleteNote(note);
                        updateNotes();
                        Log.d("MainActivity", "Deleted note from InfoWindow: " + note.note);
                        Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("MainActivity", "No StorageNote found for ID: " + noteId);
                        Toast.makeText(this, "Error: Note not found", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Failed to delete note from InfoWindow: " + e.getMessage());
                    Toast.makeText(this, "Error deleting note: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e("MainActivity", "Invalid marker tag: " + tag);
            }
        });

        myMap.setOnMapLongClickListener(latLng -> {
            Log.d("MainActivity", "Long press at: " + latLng);
            showNoteDialog(latLng);
        });

        updateNotes();
    }

    private void showNoteDialog(LatLng latLng) {
        EditText input = new EditText(this);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Note")
                .setMessage("Enter a note for this location")
                .setView(input)
                .setPositiveButton("Save", (d, which) -> {
                    String noteText = input.getText().toString().trim();
                    if (!noteText.isEmpty()) {
                        try {
                            StorageNote note = new StorageNote(noteText, latLng.latitude, latLng.longitude, System.currentTimeMillis());
                            database.appDao().insertNote(note);
                            updateNotes();
                            Log.d("MainActivity", "Saved note: " + noteText + " at " + latLng);
                            Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.e("MainActivity", "Failed to save note: " + e.getMessage());
                            Toast.makeText(this, "Error saving note: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    private void updateNotes() {
        if (myMap == null) {
            Log.e("MainActivity", "Map is null, cannot update notes");
            return;
        }

        for (Marker marker : noteMarkers) {
            marker.remove();
        }
        noteMarkers.clear();

        List<StorageNote> notes;
        try {
            notes = database.appDao().getAllNotes();
            Log.d("MainActivity", "Loaded notes: " + notes.size() + " items");
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to load notes: " + e.getMessage());
            Toast.makeText(this, "Error loading notes: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        for (StorageNote note : notes) {
            LatLng noteLocation = new LatLng(note.latitude, note.longitude);
            try {
                Marker marker = myMap.addMarker(new MarkerOptions()
                        .position(noteLocation)
                        .title(note.note)
                        .snippet("Click to delete"));
                if (marker != null) {
                    marker.setTag(note.id);
                    noteMarkers.add(marker);
                    Log.d("MainActivity", "Added marker for note ID: " + note.id + ", text: " + note.note);
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to add marker for note: " + note.note + ", error: " + e.getMessage());
            }
        }
        Log.d("MainActivity", "Updated notes: " + notes.size() + " items");
    }



    private BitmapDescriptor getResizedMarkerIcon(int resourceId, int width, int height) {
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resourceId);
            if (bitmap == null) {
                throw new IllegalStateException("Resource not found: " + resourceId);
            }
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            return BitmapDescriptorFactory.fromBitmap(resizedBitmap);
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to resize marker icon: " + e.getMessage());
            throw e;
        }
    }

    private void checkLocationSettings() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
                .setFastestInterval(2000);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        client.checkLocationSettings(builder.build())
                .addOnSuccessListener(this, locationSettingsResponse -> {
                    enableMyLocation();
                    startLocationUpdates();
                })
                .addOnFailureListener(this, e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            resolvable.startResolutionForResult(this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException sendEx) {
                            Log.e("MainActivity", "Resolution failed: " + sendEx.getMessage());
                        }
                    }
                });
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "Enabling My Location");
            if (myMap != null) {
                myMap.setMyLocationEnabled(true);
            }
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setMessage("This app needs location access to show your position on the map.")
                        .setPositiveButton("OK", (dialog, which) -> {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                                    LOCATION_PERMISSION_REQUEST_CODE);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(5000)
                    .setFastestInterval(2000);

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    for (Location location : locationResult.getLocations()) {
                        if (location != null) {
                            Log.d("MainActivity", "Location updated: " + location.getLatitude() + ", " + location.getLongitude());
                        }
                    }
                }
            };

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void centerOnMyLocation() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null && myMap != null) {
                            LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, myMap.getCameraPosition().zoom));
                            Log.d("MainActivity", "Centered on user location: " + userLocation);
                        } else {
                            Log.e("MainActivity", "Location unavailable");
                            Toast.makeText(this, "Unable to get your location. Please ensure location services are enabled.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(this, e -> {
                        Log.e("MainActivity", "Location fetch failed: " + e.getMessage());
                        Toast.makeText(this, "Error getting location: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            Log.d("MainActivity", "Requesting location permissions");
            new AlertDialog.Builder(this)
                    .setMessage("Location permission is required to center the map on your position.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this,
                                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION},
                                LOCATION_PERMISSION_REQUEST_CODE);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Location permission granted");
                checkLocationSettings();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                Log.d("MainActivity", "Location settings enabled");
                enableMyLocation();
                startLocationUpdates();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d("MainActivity", "Location updates paused");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myMap != null && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
            Log.d("MainActivity", "Location updates resumed");
        }
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            }
            return false;
        }
        return true;
    }

    private void resetMapPosition() {
        if (myMap != null) {
            float currentZoom = myMap.getCameraPosition().zoom;
            myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(BICYCLE_STORAGE, currentZoom));
            Log.d("MainActivity", "Map reset to BICYCLE_STORAGE");
        }
    }
}