package com.speechify;

public enum ClientType {
    VERY_IMPORTANT_CLIENT("VeryImportantClient", false, 0),
    IMPORTANT_CLIENT("ImportantClient", true, 20000),
    REGULAR_CLIENT("RegularClient", true, 10000);

    private final String displayName;
    private final boolean hasCreditLimit;
    private final int creditLimit;

    ClientType(String displayName, boolean hasCreditLimit, int creditLimit) {
        this.displayName = displayName;
        this.hasCreditLimit = hasCreditLimit;
        this.creditLimit = creditLimit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean hasCreditLimit() {
        return hasCreditLimit;
    }

    public int getCreditLimit() {
        return creditLimit;
    }

    public static ClientType fromName(String name) {
        for (ClientType type : values()) {
            if (type.displayName.equals(name)) {
                return type;
            }
        }
        return REGULAR_CLIENT; // default
    }

    public void applyCreditLimit(User user) {
        user.setHasCreditLimit(this.hasCreditLimit);
        user.setCreditLimit(this.creditLimit);
    }
}
