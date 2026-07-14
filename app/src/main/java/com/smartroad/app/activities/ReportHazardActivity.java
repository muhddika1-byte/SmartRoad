package com.smartroad.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.dhaval2404.imagepicker.ImagePicker;
import com.smartroad.app.R;
import com.smartroad.app.models.Hazard;
import com.smartroad.app.utils.LocationHelper;
import com.smartroad.app.utils.SupabaseClient;
import com.smartroad.app.utils.SupabaseStorageHelper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ReportHazardActivity extends AppCompatActivity {

    private Spinner spinnerHazardType;
    private EditText etDescription;
    private Button btnTakePhoto, btnSubmit;
    private ImageView ivPhoto;
    private ProgressBar progressBar;

    private Uri photoUri;
    private LocationHelper locationHelper;

    private double currentLat = 0.0;
    private double currentLng = 0.0;
    private boolean locationReady = false;

    private static final int LOCATION_PERMISSION_REQUEST = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_hazard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        SupabaseClient.init(this);
        locationHelper = new LocationHelper(this);

        if (getIntent().hasExtra("lat") && getIntent().hasExtra("lng")) {
            currentLat = getIntent().getDoubleExtra("lat", 0.0);
            currentLng = getIntent().getDoubleExtra("lng", 0.0);
            locationReady = true;
            locationHelper.reverseGeocode(currentLat, currentLng, address ->
                    Toast.makeText(this, "Location: " + address, Toast.LENGTH_LONG).show());
        } else {
            getCurrentLocation();
        }

        spinnerHazardType = findViewById(R.id.spinner_hazard_type);
        etDescription = findViewById(R.id.et_description);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        btnSubmit = findViewById(R.id.btn_submit);
        ivPhoto = findViewById(R.id.iv_photo);
        progressBar = findViewById(R.id.progress_bar);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.hazard_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerHazardType.setAdapter(adapter);

        btnTakePhoto.setOnClickListener(v -> ImagePicker.with(this)
                .crop()
                .compress(1024)
                .maxResultSize(1080, 1080)
                .start());

        btnSubmit.setOnClickListener(v -> submitReport());
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        locationHelper.getCurrentLocation(new LocationHelper.LocationCallback() {
            @Override
            public void onLocationResult(double latitude, double longitude) {
                currentLat = latitude;
                currentLng = longitude;
                locationReady = true;
                locationHelper.reverseGeocode(latitude, longitude, address ->
                        Toast.makeText(ReportHazardActivity.this, "Location: " + address,
                                Toast.LENGTH_LONG).show());
            }

            @Override
            public void onLocationError(String error) {
                Toast.makeText(ReportHazardActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            photoUri = data.getData();
            if (photoUri != null) {
                ivPhoto.setImageURI(photoUri);
                ivPhoto.setVisibility(View.VISIBLE);
            }
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(this, "Error picking image", Toast.LENGTH_SHORT).show();
        }
    }

    private void submitReport() {
        String hazardType = spinnerHazardType.getSelectedItem().toString();
        String description = etDescription.getText().toString().trim();

        if (hazardType.equals("Select hazard type")) {
            Toast.makeText(this, "Please select a hazard type", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(description)) {
            etDescription.setError("Description is required");
            return;
        }
        if (!locationReady && currentLat == 0.0) {
            Toast.makeText(this, "Waiting for GPS location...", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSubmit.setEnabled(false);

        if (photoUri != null) {
            uploadPhotoAndSave(hazardType, description);
        } else {
            saveHazard(hazardType, description, null);
        }
    }

    private void uploadPhotoAndSave(String hazardType, String description) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(photoUri);
            if (inputStream == null) {
                progressBar.setVisibility(View.GONE);
                btnSubmit.setEnabled(true);
                Toast.makeText(this, "Failed to read image", Toast.LENGTH_LONG).show();
                return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            inputStream.close();
            byte[] imageBytes = baos.toByteArray();

            SupabaseStorageHelper.uploadPhoto(imageBytes, new SupabaseStorageHelper.UploadCallback() {
                @Override
                public void onSuccess(String photoUrl) {
                    saveHazard(hazardType, description, photoUrl);
                }

                @Override
                public void onFailure(String error) {
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    Toast.makeText(ReportHazardActivity.this, "Photo upload failed: " + error,
                            Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            btnSubmit.setEnabled(true);
            Toast.makeText(this, "Photo upload failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void saveHazard(String hazardType, String description, String photoUrl) {
        try {
            JSONObject data = new JSONObject();
            data.put("user_id", SupabaseClient.getUserId());
            data.put("user_name", SupabaseClient.getUserName());
            data.put("hazard_type", hazardType);
            data.put("description", description);
            data.put("photo_url", photoUrl != null ? photoUrl : "");
            data.put("latitude", currentLat);
            data.put("longitude", currentLng);
            data.put("status", "New");
            data.put("user_agent", System.getProperty("http.agent", "unknown"));

            SupabaseClient.addHazard(data, new SupabaseClient.VoidCallback() {
                @Override
                public void onSuccess(String responseBody) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ReportHazardActivity.this,
                            "Hazard reported! Server: " + responseBody, Toast.LENGTH_LONG).show();
                    finish();
                }

                @Override
                public void onFailure(String error) {
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    Toast.makeText(ReportHazardActivity.this,
                            "Failed to submit report: " + error, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            btnSubmit.setEnabled(true);
            Toast.makeText(this, "Failed to submit report: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
