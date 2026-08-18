package com.vekom.hdbtool;

public final class SetHdbKey {
    private SetHdbKey() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || args[0].isEmpty()) {
            throw new IllegalArgumentException("Expected exactly one non-empty HDB key");
        }
        Class<?> packageManagerEx = Class.forName("com.huawei.android.app.PackageManagerEx");
        packageManagerEx.getMethod("setHdbKey", String.class).invoke(null, args[0]);
        System.out.println("HDB key delivered to package manager");
    }
}
