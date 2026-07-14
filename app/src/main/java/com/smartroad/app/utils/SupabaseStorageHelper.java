package com.smartroad.app.utils;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseStorageHelper {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UploadCallback {
        void onSuccess(String photoUrl);
        void onFailure(String error);
    }

    public static void uploadPhoto(byte[] imageBytes, UploadCallback callback) {
        String fileName = UUID.randomUUID().toString() + ".jpg";
        String uploadUrl = SupabaseConfig.SUPABASE_URL
                + "/storage/v1/object/" + SupabaseConfig.STORAGE_BUCKET + "/" + fileName;

        new Thread(() -> {
            try {
                String accessToken = SupabaseClient.getAccessToken();
                if (accessToken == null) {
                    mainHandler.post(() -> callback.onFailure("Not authenticated"));
                    return;
                }

                RequestBody body = RequestBody.create(imageBytes, MediaType.parse("image/jpeg"));
                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .post(body)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "image/jpeg")
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String publicUrl = SupabaseConfig.SUPABASE_URL
                            + "/storage/v1/object/public/"
                            + SupabaseConfig.STORAGE_BUCKET + "/" + fileName;
                    mainHandler.post(() -> callback.onSuccess(publicUrl));
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    mainHandler.post(() -> callback.onFailure("Upload failed (" + response.code() + "): " + errorBody));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure("Network error: " + e.getMessage()));
            }
        }).start();
    }
}
