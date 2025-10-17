package com.viettel.config;

public enum SystemType {
    SYSTEM_5GC,
    SYSTEM_5GA,
    SYSTEM_4GA,
    SYSTEM_ONT;

    public static SystemType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return SystemType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}


