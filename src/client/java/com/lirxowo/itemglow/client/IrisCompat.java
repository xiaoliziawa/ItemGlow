package com.lirxowo.itemglow.client;

import java.lang.reflect.Method;

public final class IrisCompat {
    private static final boolean IRIS_PRESENT;
    private static Method getInstanceMethod;
    private static Method isShaderPackInUseMethod;
    private static Method isRenderingShadowPassMethod;

    static {
        boolean present = false;
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            getInstanceMethod = irisApiClass.getMethod("getInstance");
            isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            isRenderingShadowPassMethod = irisApiClass.getMethod("isRenderingShadowPass");
            present = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // Iris not installed
        }
        IRIS_PRESENT = present;
    }

    private IrisCompat() {
    }

    public static boolean isIrisPresent() {
        return IRIS_PRESENT;
    }

    public static boolean isShaderPackInUse() {
        if (!IRIS_PRESENT) {
            return false;
        }
        try {
            Object instance = getInstanceMethod.invoke(null);
            return (boolean) isShaderPackInUseMethod.invoke(instance);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRenderingShadowPass() {
        if (!IRIS_PRESENT) {
            return false;
        }
        try {
            Object instance = getInstanceMethod.invoke(null);
            return (boolean) isRenderingShadowPassMethod.invoke(instance);
        } catch (Exception e) {
            return false;
        }
    }
}
