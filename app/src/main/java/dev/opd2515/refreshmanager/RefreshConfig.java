package dev.opd2515.refreshmanager;

final class RefreshConfig {
    private static final String PREFIX = "persist.opdrr.";
    private RefreshConfig() {}
    static String propertyFor(String packageName) {
        return PREFIX + Integer.toHexString(packageName.hashCode());
    }
}
