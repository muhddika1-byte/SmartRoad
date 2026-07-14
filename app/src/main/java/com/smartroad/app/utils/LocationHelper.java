package com.smartroad.app.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationHelper {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private final LocationManager locationManager;
    private final Context context;

    public LocationHelper(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void requestLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    public void getCurrentLocation(LocationCallback callback) {
        if (!hasLocationPermission()) {
            callback.onLocationError("Location permission not granted");
            return;
        }

        String provider = getBestProvider();
        if (provider == null) {
            callback.onLocationError("No location provider available. Enable GPS.");
            return;
        }

        try {
            Location lastKnown = locationManager.getLastKnownLocation(provider);
            if (lastKnown != null) {
                callback.onLocationResult(lastKnown.getLatitude(), lastKnown.getLongitude());
                return;
            }

            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        callback.onLocationResult(location.getLatitude(), location.getLongitude());
                    }
                    locationManager.removeUpdates(this);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {
                    callback.onLocationError("GPS is disabled. Please enable it.");
                }
            };

            locationManager.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                locationManager.removeUpdates(listener);
                callback.onLocationError("Location request timed out. Try again.");
            }, 15000);

        } catch (SecurityException e) {
            callback.onLocationError("Location permission error");
        }
    }

    private String getBestProvider() {
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        String provider = locationManager.getBestProvider(criteria, true);
        if (provider != null) return provider;

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            return LocationManager.PASSIVE_PROVIDER;
        }
        return null;
    }

    public void reverseGeocode(double latitude, double longitude, GeocodeCallback callback) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                if (!Geocoder.isPresent()) {
                    return latitude + ", " + longitude;
                }
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                try {
                    List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(address.getAddressLine(i));
                        }
                        return sb.toString();
                    }
                } catch (IOException e) {
                    return latitude + ", " + longitude;
                }
                return latitude + ", " + longitude;
            }

            @Override
            protected void onPostExecute(String result) {
                callback.onGeocodeResult(result);
            }
        }.execute();
    }

    public interface LocationCallback {
        void onLocationResult(double latitude, double longitude);
        void onLocationError(String error);
    }

    public interface GeocodeCallback {
        void onGeocodeResult(String address);
    }
}
