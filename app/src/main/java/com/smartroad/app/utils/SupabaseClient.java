package com.smartroad.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String PREFS_NAME = "supabase_session";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private static Context appContext;
    private static String accessToken;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        accessToken = prefs.getString("access_token", null);
    }

    public static boolean isLoggedIn() {
        return accessToken != null;
    }

    public static String getAccessToken() {
        return accessToken;
    }

    public static String getUserId() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("user_id", null);
    }

    public static String getUserName() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("user_name", "User");
    }

    public static String getUserEmail() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("user_email", null);
    }

    // --- Auth ---

    public interface AuthCallback {
        void onSuccess(String userId, String userName);
        void onFailure(String error);
    }

    public static void signUp(String email, String password, String name, AuthCallback callback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                JSONObject metadata = new JSONObject();
                metadata.put("name", name);
                body.put("data", metadata);

                JSONObject json = post("/auth/v1/signup", body.toString());
                String error = json.optString("error_description", null);
                if (error != null) {
                    postFailure(callback, error);
                    return;
                }
                if (json.has("error")) {
                    postFailure(callback, json.optString("error_description", json.optString("msg", "Sign up failed")));
                    return;
                }

                // Try to get user from "user" key or direct root
                JSONObject userObj = json.optJSONObject("user");
                if (userObj == null) {
                    // Response format might have id at root
                    String id = json.optString("id", null);
                    if (id == null || id.isEmpty()) {
                        postSuccess(callback, "", name);
                        return;
                    }
                    userObj = json;
                }

                String userId = userObj.optString("id");
                JSONObject userMeta = userObj.optJSONObject("user_metadata");
                String userName = (userMeta != null) ? userMeta.optString("name", name) : name;

                // Save session if access_token is returned (auto-confirm enabled)
                String token = json.optString("access_token", null);
                if (token != null && !token.isEmpty()) {
                    String userEmail = userObj.optString("email", email);
                    String refreshToken = json.optString("refresh_token", "");
                    SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                            .putString("access_token", token)
                            .putString("refresh_token", refreshToken)
                            .putString("user_id", userId)
                            .putString("user_email", userEmail)
                            .putString("user_name", userName)
                            .apply();
                    accessToken = token;
                }

                postSuccess(callback, userId, userName);
            } catch (Exception e) {
                postFailure(callback, e.getMessage());
            }
        }).start();
    }

    public static void signIn(String email, String password, AuthCallback callback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);

                JSONObject json = post("/auth/v1/token?grant_type=password", body.toString());
                String error = json.optString("error_description", null);
                if (error != null) {
                    postFailure(callback, error);
                    return;
                }
                if (json.has("error")) {
                    postFailure(callback, json.optString("error_description", json.optString("msg", "Login failed")));
                    return;
                }

                String token = json.optString("access_token", null);
                if (token == null) {
                    postFailure(callback, "No access token received. Response: " + json.toString());
                    return;
                }

                JSONObject userObj = json.optJSONObject("user");
                if (userObj == null) {
                    postFailure(callback, "No user data received");
                    return;
                }

                String userId = userObj.optString("id");
                String userEmail = userObj.optString("email", email);
                JSONObject userMeta = userObj.optJSONObject("user_metadata");
                String userName = (userMeta != null) ? userMeta.optString("name", "User") : "User";
                String refreshToken = json.optString("refresh_token", "");

                // Save session
                SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit()
                        .putString("access_token", token)
                        .putString("refresh_token", refreshToken)
                        .putString("user_id", userId)
                        .putString("user_email", userEmail)
                        .putString("user_name", userName)
                        .apply();
                accessToken = token;

                postSuccess(callback, userId, userName);
            } catch (Exception e) {
                postFailure(callback, e.getMessage());
            }
        }).start();
    }

    public static void signOut() {
        new Thread(() -> {
            try {
                delete("/auth/v1/logout");
            } catch (Exception ignored) {}
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
            accessToken = null;
        }).start();
    }

    // --- Database ---

    public interface HazardsCallback {
        void onSuccess(JSONArray hazards);
        void onFailure(String error);
    }

    public interface VoidCallback {
        void onSuccess(String responseBody);
        void onFailure(String error);
    }

    private static boolean refreshAccessToken() {
        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String refreshToken = prefs.getString("refresh_token", null);
            if (refreshToken == null || refreshToken.isEmpty()) return false;

            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);

            JSONObject json = post("/auth/v1/token?grant_type=refresh_token", body.toString());
            String newToken = json.optString("access_token", null);
            if (newToken == null) return false;

            String newRefreshToken = json.optString("refresh_token", refreshToken);
            prefs.edit()
                    .putString("access_token", newToken)
                    .putString("refresh_token", newRefreshToken)
                    .apply();
            accessToken = newToken;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void getHazards(HazardsCallback callback) {
        new Thread(() -> {
            try {
                Request request = authRequest()
                        .url(buildUrl("/rest/v1/hazards?select=*&order=timestamp.desc"))
                        .get()
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "[]";
                    if (response.isSuccessful()) {
                        postHazardsSuccess(callback, new JSONArray(body));
                    } else if (response.code() == 401 && refreshAccessToken()) {
                        // Retry with fresh token
                        Request retry = authRequest()
                                .url(buildUrl("/rest/v1/hazards?select=*&order=timestamp.desc"))
                                .get()
                                .build();
                        try (Response retryResponse = client.newCall(retry).execute()) {
                            String retryBody = retryResponse.body() != null ? retryResponse.body().string() : "[]";
                            if (retryResponse.isSuccessful()) {
                                postHazardsSuccess(callback, new JSONArray(retryBody));
                            } else {
                                postHazardsFailure(callback, "HTTP " + retryResponse.code() + ": " + retryBody);
                            }
                        }
                    } else {
                        postHazardsFailure(callback, "HTTP " + response.code() + ": " + body);
                    }
                }
            } catch (Exception e) {
                postHazardsFailure(callback, e.getMessage());
            }
        }).start();
    }

    public static void addHazard(JSONObject hazardData, VoidCallback callback) {
        new Thread(() -> {
            try {
                Request request = authRequest()
                        .url(buildUrl("/rest/v1/hazards"))
                        .addHeader("Prefer", "return=representation")
                        .post(RequestBody.create(hazardData.toString(), JSON))
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "(empty)";
                    if (response.isSuccessful()) {
                        postVoidSuccess(callback, "Response " + response.code() + ": " + body);
                    } else if (response.code() == 401 && refreshAccessToken()) {
                        // Retry with fresh token
                        Request retry = authRequest()
                                .url(buildUrl("/rest/v1/hazards"))
                                .addHeader("Prefer", "return=representation")
                                .post(RequestBody.create(hazardData.toString(), JSON))
                                .build();
                        try (Response retryResponse = client.newCall(retry).execute()) {
                            String retryBody = retryResponse.body() != null ? retryResponse.body().string() : "(empty)";
                            if (retryResponse.isSuccessful()) {
                                postVoidSuccess(callback, "Response " + retryResponse.code() + ": " + retryBody);
                            } else {
                                postVoidFailure(callback, "HTTP " + retryResponse.code() + ": " + retryBody);
                            }
                        }
                    } else {
                        postVoidFailure(callback, "HTTP " + response.code() + ": " + body);
                    }
                }
            } catch (Exception e) {
                postVoidFailure(callback, e.getMessage());
            }
        }).start();
    }

    // --- HTTP helpers ---

    private static String buildUrl(String path) {
        return SupabaseConfig.SUPABASE_URL + path;
    }

    private static Request.Builder authRequest() {
        Request.Builder builder = new Request.Builder()
                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json");
        if (accessToken != null) {
            builder.addHeader("Authorization", "Bearer " + accessToken);
        }
        return builder;
    }

    private static JSONObject post(String path, String jsonBody) throws Exception {
        Request request = authRequest()
                .url(buildUrl(path))
                .post(RequestBody.create(jsonBody, JSON))
                .build();
        return executeJson(request);
    }

    private static JSONObject delete(String path) throws Exception {
        Request request = authRequest()
                .url(buildUrl(path))
                .delete()
                .build();
        return executeJson(request);
    }

    private static JSONArray getArray(String path) throws Exception {
        Request request = authRequest()
                .url(buildUrl(path))
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "[]";
            return new JSONArray(body);
        }
    }

    private static JSONObject executeJson(Request request) throws Exception {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            return new JSONObject(body);
        }
    }

    // --- Callback helpers (post to UI thread) ---

    private static void postSuccess(AuthCallback cb, String uid, String name) {
        if (cb == null) return;
        mainHandler.post(() -> cb.onSuccess(uid, name));
    }

    private static void postFailure(AuthCallback cb, String error) {
        if (cb == null) return;
        mainHandler.post(() -> cb.onFailure(error));
    }

    private static void postHazardsSuccess(HazardsCallback cb, JSONArray data) {
        if (cb == null) return;
        mainHandler.post(() -> cb.onSuccess(data));
    }

    private static void postHazardsFailure(HazardsCallback cb, String error) {
        if (cb == null) return;
        mainHandler.post(() -> cb.onFailure(error));
    }

    private static void postVoidSuccess(VoidCallback cb, String responseBody) {
        if (cb == null) return;
        mainHandler.post(() -> cb.onSuccess(responseBody));
    }

    private static void postVoidFailure(VoidCallback cb, String error) {
        if (cb == null) return;
        mainHandler.post(() -> cb.onFailure(error));
    }
}
