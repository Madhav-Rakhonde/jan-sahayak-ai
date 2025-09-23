package com.JanSahayak.AI.enums;



public enum BroadcastScope {
    COUNTRY("Country-wide broadcast"),
    STATE("State-level broadcast"),
    DISTRICT("District-level broadcast"),
    AREA("Area/Pincode-level broadcast");

    private final String description;

    BroadcastScope(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
