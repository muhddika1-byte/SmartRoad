package com.smartroad.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.smartroad.app.R;
import com.smartroad.app.models.Hazard;
import com.smartroad.app.utils.LocationHelper;
import com.smartroad.app.utils.SupabaseClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private MapView mapView;
    private LocationHelper locationHelper;
    private Map<String, Hazard> hazardMarkerMap = new HashMap<>();
    private DrawerLayout drawerLayout;
    private Button btnReportHere;
    private Marker selectedLocationMarker;
    private double selectedLat = 0.0;
    private double selectedLng = 0.0;
    private boolean hasSelectedLocation = false;
    private static final int LOCATION_PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SupabaseClient.init(this);

        if (!SupabaseClient.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.nav_open, R.string.nav_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        View headerView = navigationView.getHeaderView(0);
        TextView tvName = headerView.findViewById(R.id.tv_nav_name);
        TextView tvEmail = headerView.findViewById(R.id.tv_nav_email);
        String userName = SupabaseClient.getUserName();
        tvName.setText(userName);
        tvEmail.setText(SupabaseClient.getUserEmail());
        getSupportActionBar().setTitle("SmartRoad - " + userName);

        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);
        mapView.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);

        Button btnZoomIn = findViewById(R.id.btn_zoom_in);
        Button btnZoomOut = findViewById(R.id.btn_zoom_out);
        btnZoomIn.setOnClickListener(v -> mapView.getController().zoomIn());
        btnZoomOut.setOnClickListener(v -> mapView.getController().zoomOut());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }

        loadHazardMarkers();
        setupMapTap();
        setupReportHereButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    private void getCurrentLocation() {
        locationHelper = new LocationHelper(this);
        locationHelper.getCurrentLocation(new LocationHelper.LocationCallback() {
            @Override
            public void onLocationResult(double latitude, double longitude) {
                GeoPoint currentPoint = new GeoPoint(latitude, longitude);
                mapView.getController().setCenter(currentPoint);
            }

            @Override
            public void onLocationError(String error) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadHazardMarkers() {
        SupabaseClient.getHazards(new SupabaseClient.HazardsCallback() {
            @Override
            public void onSuccess(JSONArray hazards) {
                mapView.getOverlays().removeIf(o -> o instanceof Marker && o != selectedLocationMarker);
                hazardMarkerMap.clear();

                for (int i = 0; i < hazards.length(); i++) {
                    try {
                        JSONObject obj = hazards.getJSONObject(i);
                        Hazard hazard = new Hazard();
                        hazard.setId(obj.optString("id"));
                        hazard.setUserId(obj.optString("user_id"));
                        hazard.setUserName(obj.optString("user_name", "Unknown"));
                        hazard.setHazardType(obj.optString("hazard_type"));
                        hazard.setDescription(obj.optString("description"));
                        hazard.setPhotoUrl(obj.optString("photo_url"));
                        hazard.setLatitude(obj.optDouble("latitude"));
                        hazard.setLongitude(obj.optDouble("longitude"));
                        hazard.setStatus(obj.optString("status", "New"));
                        hazard.setTimestamp(obj.optString("timestamp"));
                        hazard.setUserAgent(obj.optString("user_agent"));

                        hazardMarkerMap.put(hazard.getId(), hazard);

                        GeoPoint position = new GeoPoint(hazard.getLatitude(), hazard.getLongitude());
                        Marker marker = new Marker(mapView);
                        marker.setPosition(position);
                        marker.setTitle(hazard.getHazardType());
                        marker.setSnippet("Status: " + hazard.getStatus() + "\n" + hazard.getDescription());
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        marker.setIcon(getMarkerIcon(hazard.getHazardType(), hazard.getStatus()));

                        final String hazardId = hazard.getId();
                        marker.setOnMarkerClickListener((m, mapView) -> {
                            showHazardDetails(hazardId);
                            return true;
                        });

                        mapView.getOverlays().add(marker);
                    } catch (Exception ignored) {}
                }
                mapView.invalidate();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(MainActivity.this, "Failed to load hazards: " + error,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private Drawable getMarkerIcon(String type, String status) {
        int iconResId;
        switch (type) {
            case "Pothole": iconResId = R.drawable.ic_hazard_pothole; break;
            case "Flooding": iconResId = R.drawable.ic_hazard_flooding; break;
            case "Fallen Tree": iconResId = R.drawable.ic_hazard_tree; break;
            case "Traffic Accident": iconResId = R.drawable.ic_hazard_accident; break;
            case "Damaged Road Sign": iconResId = R.drawable.ic_hazard_sign; break;
            case "Broken Traffic Light": iconResId = R.drawable.ic_hazard_traffic_light; break;
            default: iconResId = R.drawable.ic_hazard_default;
        }

        int statusColor;
        switch (status) {
            case "New": statusColor = 0xFFFF5722; break;
            case "Under Investigation": statusColor = 0xFFFFC107; break;
            case "Resolved": statusColor = 0xFF4CAF50; break;
            default: statusColor = 0xFF757575;
        }

        Drawable iconDrawable = getResources().getDrawable(iconResId).mutate();
        iconDrawable.setColorFilter(new PorterDuffColorFilter(statusColor, PorterDuff.Mode.SRC_IN));
        return iconDrawable;
    }

    private void showHazardDetails(String hazardId) {
        Hazard hazard = hazardMarkerMap.get(hazardId);
        if (hazard == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(hazard.getHazardType());
        builder.setMessage("Reported by: " + hazard.getUserName() + "\n"
                + "Status: " + hazard.getStatus() + "\n"
                + "Date: " + hazard.getTimestamp() + "\n"
                + "Location: " + hazard.getLatitude() + ", " + hazard.getLongitude() + "\n\n"
                + hazard.getDescription());
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private void setupMapTap() {
        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                placePin(p.getLatitude(), p.getLongitude());
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        });
        mapView.getOverlays().add(0, eventsOverlay);
    }

    private void placePin(double lat, double lng) {
        selectedLat = lat;
        selectedLng = lng;
        hasSelectedLocation = true;

        if (selectedLocationMarker != null) {
            mapView.getOverlays().remove(selectedLocationMarker);
        }

        selectedLocationMarker = new Marker(mapView);
        selectedLocationMarker.setPosition(new GeoPoint(lat, lng));
        selectedLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        selectedLocationMarker.setTitle("Selected Location");
        selectedLocationMarker.setIcon(getResources().getDrawable(R.drawable.ic_pin));
        mapView.getOverlays().add(selectedLocationMarker);
        mapView.invalidate();

        btnReportHere.setVisibility(View.VISIBLE);
    }

    private void setupReportHereButton() {
        btnReportHere = findViewById(R.id.btn_report_here);
        btnReportHere.setOnClickListener(v -> {
            if (!hasSelectedLocation) return;
            Intent intent = new Intent(MainActivity.this, ReportHazardActivity.class);
            intent.putExtra("lat", selectedLat);
            intent.putExtra("lng", selectedLng);
            startActivity(intent);
        });
    }

    private void openReportHazard() {
        if (hasSelectedLocation) {
            Intent intent = new Intent(MainActivity.this, ReportHazardActivity.class);
            intent.putExtra("lat", selectedLat);
            intent.putExtra("lng", selectedLng);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Tap a location on the map first", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_report) {
            openReportHazard();
        } else if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        } else if (id == R.id.nav_logout) {
            SupabaseClient.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show();
            }
        }
    }
}
