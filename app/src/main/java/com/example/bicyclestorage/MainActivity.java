package com.example.bicyclestorage;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.bicyclestorage.auth.AccountActivity;
import com.example.bicyclestorage.auth.FirebaseUserRepository;
import com.example.bicyclestorage.auth.LoginActivity;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * MainActivity – Google Maps, multiple bicycle storage markers,
 * lock button (persistently toggles open/closed), edge-to-edge layout,
 * sign-in check and account screen launcher.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    // --- Data model for storages ---
    private static class Storage {
        final LatLng position;
        final String title;
        String snippet;
        Storage(LatLng pos, String title, String snippet) {
            this.position = pos;
            this.title = title;
            this.snippet = snippet;
        }
    }

    // List of storages – extendable
    private static final Storage[] STORAGES = new Storage[]{
            new Storage(new LatLng(47.543277, 21.640391), "Bicycle storage 1", "In use: 3/6"),
            new Storage(new LatLng(47.532368, 21.629087), "Bicycle storage 2", "In use: 1/4"),
            new Storage(new LatLng(47.553577, 21.621793), "Bicycle storage 3", "In use: 5/10")
    };

    // Constants
    private static final float DEFAULT_ZOOM = 15f;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int REQUEST_CHECK_SETTINGS = 2;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    // Lock state persistence keys
    private static final String PREFS_NAME = "lock_prefs";
    private static final String KEY_LOCKED = "key_locked";
    private static final String STATE_LOCKED = "state_locked";

    // Map and location
    private GoogleMap myMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    // Marker -> Storage mapping
    private final Map<Marker, Storage> markerStorageMap = new HashMap<>();

    // UI elements
    private ImageButton lockButton;     // bottom-right – red/green selector
    private ImageButton accountButton;  // top-right
    private ImageButton storageButton;  // bottom-left – bicycle icon

    // Lock state
    private boolean locked = true; // true = LOCKED (red), false = UNLOCKED (green)

    // Firebase user repo (auth + profile)
    private FirebaseUserRepository userRepo;

    // System insets cache (for map padding)
    private int systemTopInset = 0;
    private int systemBottomInset = 0;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase user repo
        userRepo = new FirebaseUserRepository();

        // Sign-in check (if no user → LoginActivity)
        if (!userRepo.isLoggedIn()) {
            goToLoginAndFinish();
            return;
        }

        // Restore from config change (e.g., rotation)
        if (savedInstanceState != null) {
            locked = savedInstanceState.getBoolean(STATE_LOCKED, true);
        }

        // Persistent state (after app restart)
        restoreLockFromPrefs();

        // System bars appearance
        setupSystemBarsAppearance();

        // Play Services check
        if (!checkPlayServices()) {
            finish();
            return;
        }

        initUiReferences();
        setupButtons();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Window Insets handling (status/nav bar): top padding to root content
        View root = findViewById(R.id.main);
        applyWindowInsets(root);

        // Load map
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    // --- UI init ---
    private void initUiReferences() {
        lockButton = findViewById(R.id.lockButton);
        accountButton = findViewById(R.id.accountButton);
        storageButton = findViewById(R.id.storageButton);
    }

    private void setupButtons() {
        if (storageButton != null) {
            storageButton.setOnClickListener(v -> resetMapPosition());
        }

        if (lockButton != null) {
            applyLockVisual();
            lockButton.setOnClickListener(v -> {
                locked = !locked;
                applyLockVisual();
                persistLockState();
            });
            lockButton.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setMessage(locked ? "Storage is currently LOCKED." : "Storage is currently UNLOCKED.")
                        .setPositiveButton("OK", null)
                        .show();
                return true;
            });
        }

        if (accountButton != null) {
            accountButton.setOnClickListener(v -> {
                // Open account screen
                startActivity(new Intent(this, AccountActivity.class));
            });
            accountButton.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setMessage("View / edit account")
                        .setPositiveButton("OK", null)
                        .show();
                return true;
            });
        }
    }

    private void restoreLockFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        locked = prefs.getBoolean(KEY_LOCKED, locked);
    }

    private void persistLockState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LOCKED, locked)
                .apply();
    }

    private void goToLoginAndFinish() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private void setupSystemBarsAppearance() {
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), decorView);

        // Dark icons for light backgrounds
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }

    private void applyWindowInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            systemTopInset = sysBars.top;
            systemBottomInset = sysBars.bottom;

            // Add padding so status bar doesn't overlap content
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom);

            // Map top padding no longer needs topInset (root handles it)
            applyMapPadding();

            return insets;
        });
    }

    private void bumpButtonMarginsForInsets(int topInset) {
        // lockButton – bottom-right (root already has bottom padding)
        if (lockButton != null && lockButton.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams lp =
                    (android.widget.FrameLayout.LayoutParams) lockButton.getLayoutParams();
            lp.bottomMargin = dp(16);
            lp.rightMargin = dp(16);
            lockButton.setLayoutParams(lp);
        }
        // storageButton – bottom-left
        if (storageButton != null && storageButton.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams lp =
                    (android.widget.FrameLayout.LayoutParams) storageButton.getLayoutParams();
            lp.bottomMargin = dp(16);
            lp.leftMargin = dp(16);
            storageButton.setLayoutParams(lp);
        }
        // accountButton – top-right
        if (accountButton != null && accountButton.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams lp =
                    (android.widget.FrameLayout.LayoutParams) accountButton.getLayoutParams();
            lp.topMargin = dp(16);
            lp.rightMargin = dp(16);
            accountButton.setLayoutParams(lp);
        }
    }

    private void applyLockVisual() {
        if (lockButton == null) return;
        // selected = TRUE → UNLOCKED (green) in selector
        lockButton.setSelected(!locked);
        lockButton.setImageResource(locked ? R.drawable.ic_lock_closed : R.drawable.ic_lock_open);
        lockButton.setContentDescription(locked ? "Storage locked" : "Storage unlocked");
    }

    // --- Google Map ready ---
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        myMap = googleMap;

        setupMapUi();
        addStorageMarkers();
        focusInitial();
        setupInfoWindowAdapter();
        setupMarkerClickAndNavigation();
        checkLocationSettings();
    }

    private void setupMapUi() {
        if (myMap == null) return;
        myMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Enable built-in Map Toolbar (bottom-right), so navigation shortcut appears
        myMap.getUiSettings().setMapToolbarEnabled(true);

        applyMapPadding();
    }

    private void applyMapPadding() {
        if (myMap == null) return;
        int sidePad = dp(16);
        int topPad = dp(16);
        int bottomControlsPad = dp(96);       // space for floating buttons (root has bottom inset)
        myMap.setPadding(sidePad, topPad, sidePad, bottomControlsPad);
    }

    private void addStorageMarkers() {
        for (Storage storage : STORAGES) {
            MarkerOptions opts = new MarkerOptions()
                    .position(storage.position)
                    .title(storage.title)
                    .snippet(storage.snippet)
                    .icon(getResizedMarkerIcon(R.drawable.bicycle, 120, 120));
            Marker m = myMap.addMarker(opts);
            if (m != null) {
                markerStorageMap.put(m, storage);
            }
        }
    }

    private void focusInitial() {
        if (STORAGES.length > 0) {
            myMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    STORAGES[0].position, DEFAULT_ZOOM));
        }
    }

    private void setupInfoWindowAdapter() {
        // Default info window content
        myMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                Storage st = markerStorageMap.get(marker);
                if (st == null) return null;
                View view = getLayoutInflater().inflate(R.layout.custom_info_window, null);
                TextView infoText = view.findViewById(R.id.info_text);
                infoText.setText(st.snippet);
                return view;
            }
            @Override
            public View getInfoContents(Marker marker) { return null; }
        });
    }

    private void setupMarkerClickAndNavigation() {
        // Let default behavior show info window + toolbar
        myMap.setOnMarkerClickListener(marker -> {
            if (!markerStorageMap.containsKey(marker)) return false;
            return false;
        });

        // InfoWindow click → open Google Maps with bicycling route
        myMap.setOnInfoWindowClickListener(marker -> {
            Storage st = markerStorageMap.get(marker);
            if (st != null) openInGoogleMaps(st.position, st.title);
            else openInGoogleMaps(marker.getPosition(), marker.getTitle());
        });
    }

    private void openInGoogleMaps(LatLng pos, String label) {
        // Prefer Google Maps app navigation intent (bicycle mode)
        Uri uri = Uri.parse("google.navigation:q=" + pos.latitude + "," + pos.longitude + "&mode=b");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // Fallback: web Google Maps
            Uri web = Uri.parse("https://www.google.com/maps/dir/?api=1"
                    + "&destination=" + pos.latitude + "," + pos.longitude
                    + "&travelmode=bicycling");
            startActivity(new Intent(Intent.ACTION_VIEW, web));
        }
    }

    // --- Location / permissions / settings ---
    private void checkLocationSettings() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
                .setFastestInterval(2000);

        LocationSettingsRequest.Builder builder =
                new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        client.checkLocationSettings(builder.build())
                .addOnSuccessListener(this, response -> {
                    enableMyLocation();
                    startLocationUpdates();
                })
                .addOnFailureListener(this, e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            ((ResolvableApiException) e)
                                    .startResolutionForResult(this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException ignored) {}
                    }
                });
    }

    private void enableMyLocation() {
        boolean fine = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            if (myMap != null) {
                try {
                    myMap.setMyLocationEnabled(true);
                } catch (SecurityException ignored) {}
            }
        } else {
            requestLocationPermissionsWithRationale();
        }
    }

    private void requestLocationPermissionsWithRationale() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setMessage("Location access is required to show your position.")
                    .setPositiveButton("OK", (d, w) -> ActivityCompat.requestPermissions(this,
                            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void startLocationUpdates() {
        boolean fine = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fine && !coarse) return;

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)
                .setFastestInterval(2000);

        locationCallback = new LocationCallback() {
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        // future logic
                    }
                }
            }
        };
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException ignored) {}
    }

    // --- Permission result ---
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkLocationSettings();
            }
        }
    }

    // --- Activity results (e.g., location settings dialog) ---
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHECK_SETTINGS && resultCode == RESULT_OK) {
            enableMyLocation();
            startLocationUpdates();
        }
    }

    // --- Lifecycle ---
    @Override
    protected void onPause() {
        super.onPause();
        removeLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If user signed out in AccountActivity, verify here:
        if (!userRepo.isLoggedIn()) {
            goToLoginAndFinish();
            return;
        }
        if (myMap != null &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
            applyMapPadding(); // if status bar height changed
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeLocationUpdates();
    }

    private void removeLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_LOCKED, locked);
        super.onSaveInstanceState(outState);
    }

    // --- Play Services check ---
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(
                        this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            }
            return false;
        }
        return true;
    }

    // --- Map reset: include all storages in bounds ---
    private void resetMapPosition() {
        if (myMap == null || STORAGES.length == 0) return;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Storage s : STORAGES) {
            builder.include(s.position);
        }
        LatLngBounds bounds = builder.build();
        int padding = dp(48); // uniform dp-based padding
        myMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    }

    // --- Marker icon scaling ---
    private BitmapDescriptor getResizedMarkerIcon(int resourceId, int width, int height) {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resourceId);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}