package com.example.bicyclestorage;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import com.example.bicyclestorage.auth.LoginActivity;
import com.example.bicyclestorage.auth.FirebaseUserRepository;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * MainActivity – Google Maps, több biciklitároló marker,
 * lakat gomb (nyit / zár perzisztensen), edge-to-edge elrendezés,
 * bejelentkezés ellenőrzéssel és account ikon megnyitással.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    // --- Adatmodell a tárolókhoz ---
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

    // Tárolók listája – bővíthető
    private static final Storage[] STORAGES = new Storage[]{
            new Storage(new LatLng(47.543277, 21.640391), "Biciklitároló 1", "In use: 3/6"),
            new Storage(new LatLng(47.532368, 21.629087), "Biciklitároló 2", "In use: 1/4"),
            new Storage(new LatLng(47.553577, 21.621793), "Biciklitároló 3", "In use: 5/10")
    };

    // Állandók
    private static final float DEFAULT_ZOOM = 15f;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int REQUEST_CHECK_SETTINGS = 2;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    // Lakat állapot perzisztencia kulcsok
    private static final String PREFS_NAME = "lock_prefs";
    private static final String KEY_LOCKED = "key_locked";
    private static final String STATE_LOCKED = "state_locked";

    // Térkép és helymeghatározás
    private GoogleMap myMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    // Marker -> Storage mapping
    private final Map<Marker, Storage> markerStorageMap = new HashMap<>();

    private Marker currentlyShownInfoMarker = null;

    // UI elemek
    private ImageButton lockButton;
    private ImageButton accountButton;
    private LinearLayout buttonContainer;

    // Lakat állapot
    private boolean locked = true; // true = ZÁRVA (piros), false = NYITVA (zöld)

    // Firebase user repo (auth + profil)
    private FirebaseUserRepository userRepo;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase user repo inicializálás
        userRepo = new FirebaseUserRepository();

        // Bejelentkezés ellenőrzés (ha nincs user → LoginActivity)
        if (!userRepo.isLoggedIn()) {
            goToLoginAndFinish();
            return;
        }

        // Config change (pl. forgatás) állapot visszatöltés
        if (savedInstanceState != null) {
            locked = savedInstanceState.getBoolean(STATE_LOCKED, true);
        }

        // Perzisztens állapot (app újraindítás után)
        restoreLockFromPrefs();

        // Rendszer sávok megjelenésének testreszabása
        setupSystemBarsAppearance();

        // Play Services ellenőrzés
        if (!checkPlayServices()) {
            finish();
            return;
        }

        initUiReferences();
        setupButtons();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Window Insets kezelése (status / nav bar)
        View root = findViewById(R.id.main);
        applyWindowInsets(root, buttonContainer);

        // Térkép betöltése
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    // --- UI inicializálás ---
    private void initUiReferences() {
        lockButton = findViewById(R.id.lockButton);
        accountButton = findViewById(R.id.accountButton);
        buttonContainer = findViewById(R.id.button_container);
    }

    private void setupButtons() {
        Button resetButton = findViewById(R.id.reset_button);
        if (resetButton != null) {
            resetButton.setOnClickListener(v -> resetMapPosition());
        }

        Button actionButton = findViewById(R.id.action_button);
        if (actionButton != null) {
            actionButton.setOnClickListener(v -> centerOnMyLocation());
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
                        .setMessage(locked ? "Tároló jelenleg ZÁRVA." : "Tároló jelenleg NYITVA.")
                        .setPositiveButton("OK", null)
                        .show();
                return true;
            });
        }

        if (accountButton != null) {
            accountButton.setOnClickListener(v -> {
                // Megnyitjuk a profil / account nézetet
                startActivity(new Intent(this, AccountActivity.class));
            });
            accountButton.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setMessage("Fiók megtekintése / módosítása")
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

        // Világos háttérhez sötét ikonok
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Ha átlátszó status bart akarsz, lehetne:
            // getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
    }

    private void applyWindowInsets(View root, LinearLayout bottomBar) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, 0);
            if (bottomBar != null) {
                bottomBar.setPadding(
                        bottomBar.getPaddingLeft(),
                        bottomBar.getPaddingTop(),
                        bottomBar.getPaddingRight(),
                        bottomBar.getPaddingBottom() + sysBars.bottom
                );
            }
            return insets;
        });
    }

    private void applyLockVisual() {
        if (lockButton == null) return;
        // selected = TRUE → NYITVA (zöld) a selector-ban
        lockButton.setSelected(!locked);
        lockButton.setImageResource(locked ? R.drawable.ic_lock_closed : R.drawable.ic_lock_open);
        lockButton.setContentDescription(locked ? "Tároló zárva" : "Tároló nyitva");
    }

    // --- Google Map kész ---
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        myMap = googleMap;
        addStorageMarkers();
        focusInitial();
        setupInfoWindowAdapter();
        setupMarkerClickLogic();
        checkLocationSettings();
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
            public View getInfoContents(Marker marker) {
                return null;
            }
        });
    }

    private void setupMarkerClickLogic() {
        myMap.setOnMarkerClickListener(marker -> {
            if (!markerStorageMap.containsKey(marker)) return false;

            if (currentlyShownInfoMarker != null && currentlyShownInfoMarker.equals(marker)) {
                marker.hideInfoWindow();
                currentlyShownInfoMarker = null;
            } else {
                marker.showInfoWindow();
                currentlyShownInfoMarker = marker;
            }
            return true;
        });

        myMap.setOnInfoWindowCloseListener(marker -> {
            if (marker.equals(currentlyShownInfoMarker)) {
                currentlyShownInfoMarker = null;
            }
        });
    }

    // --- Helymeghatározás / engedélyek / beállítások ---
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
                        } catch (IntentSender.SendIntentException ignored) {
                        }
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
                    .setMessage("A helyhozzáférés szükséges a pozíciód megjelenítéséhez.")
                    .setPositiveButton("OK", (d, w) -> ActivityCompat.requestPermissions(this,
                            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE))
                    .setNegativeButton("Mégse", null)
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
                        // TODO: későbbi logika (pl. legközelebbi tároló jelzése)
                    }
                }
            }
        };
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException ignored) {}
    }

    private void centerOnMyLocation() {
        boolean fine = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (fine || coarse) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null && myMap != null) {
                            LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                    userLocation, myMap.getCameraPosition().zoom));
                        } else {
                            showInfoDialog("Nem sikerült a pozíciót lekérdezni. Ellenőrizd a helyszolgáltatást.");
                        }
                    })
                    .addOnFailureListener(this, e ->
                            showInfoDialog("Hiba a helylekérés során: " + e.getMessage()));
        } else {
            showInfoDialog("Engedély szükséges a pozíciód középre helyezéséhez.");
            requestLocationPermissionsWithRationale();
        }
    }

    private void showInfoDialog(String msg) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    // --- Engedélykérés eredménye ---
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

    // --- Activity eredmények (pl. location settings dialógus) ---
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
        // Ha időközben kijelentkezett (AccountActivity-ben), itt ellenőrizhetjük:
        if (!userRepo.isLoggedIn()) {
            goToLoginAndFinish();
            return;
        }
        if (myMap != null &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
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

    // --- Play Services ellenőrzés ---
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

    // --- Térkép reset: az összes tároló bekeretezése ---
    private void resetMapPosition() {
        if (myMap == null || STORAGES.length == 0) return;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Storage s : STORAGES) {
            builder.include(s.position);
        }
        LatLngBounds bounds = builder.build();
        int padding = 120; // px
        myMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    }

    // --- Marker ikon skálázás ---
    private BitmapDescriptor getResizedMarkerIcon(int resourceId, int width, int height) {
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resourceId);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap);
    }
}