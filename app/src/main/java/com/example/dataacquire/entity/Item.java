package com.example.dataacquire.entity;

public class Item {
    private double latitude;
    private double longitude;
    private double gri;

    public Item(double latitude, double longtitude, double gri) {
        this.latitude = latitude;
        this.longitude = longtitude;
        this.gri = gri;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getGri() {
        return gri;
    }

    public void setGri(double gri) {
        this.gri = gri;
    }
}
