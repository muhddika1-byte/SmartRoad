package com.smartroad.app.models;

public class Hazard {
    private String id;
    private String userId;
    private String userName;
    private String hazardType;
    private String description;
    private String photoUrl;
    private double latitude;
    private double longitude;
    private String status;
    private String timestamp;
    private String userAgent;

    public Hazard() {}

    public Hazard(String userId, String userName, String hazardType, String description,
                  String photoUrl, double latitude, double longitude, String userAgent) {
        this.userId = userId;
        this.userName = userName;
        this.hazardType = hazardType;
        this.description = description;
        this.photoUrl = photoUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = "New";
        this.userAgent = userAgent;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getHazardType() { return hazardType; }
    public void setHazardType(String hazardType) { this.hazardType = hazardType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public static int getStatusColor(String status) {
        switch (status) {
            case "New": return 0xFFFF5722;
            case "Under Investigation": return 0xFFFFC107;
            case "Resolved": return 0xFF4CAF50;
            default: return 0xFF757575;
        }
    }
}
