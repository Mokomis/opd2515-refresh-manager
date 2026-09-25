package dev.opd2515.refreshmanager;

import android.util.Log;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Device- and firmware-specific refresh policy hook for OPD2515 ColorOS 16. */
public final class ModuleEntry extends XposedModule {
    private static final String TAG = "OPD2515Refresh";
    private static final String POLICY_CLASS =
            "com.android.server.wm.OplusRefreshRatePolicyImpl$PickRefreshRateData";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_])([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+){1,})(?![A-Za-z0-9_])");
    private boolean installed;
    private Method getIntProperty;

    @Override
    public synchronized void onSystemServerStarting(SystemServerStartingParam param) {
        if (installed) return;
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            getIntProperty = systemProperties.getDeclaredMethod("getInt", String.class, int.class);
            getIntProperty.setAccessible(true);

            Class<?> policy = Class.forName(POLICY_CLASS, false, param.getClassLoader());
            Method revise = policy.getDeclaredMethod(
                    "reviseWinPreferredIdIfNeeded", int.class, int.class, String.class);
            revise.setAccessible(true);
            hook(revise)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        int configuredMode = configuredMode((String) chain.getArg(2));
                        if (configuredMode == 3 || configuredMode == 4) return configuredMode;
                        return chain.proceed();
                    });
            installed = true;
            log(Log.INFO, TAG, "Per-app refresh policy hook installed");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Unable to install refresh policy hook", t);
        }
    }

    private int configuredMode(String reason) {
        if (reason == null || getIntProperty == null) return 0;
        Matcher matcher = PACKAGE_PATTERN.matcher(reason);
        while (matcher.find()) {
            try {
                int mode = (Integer) getIntProperty.invoke(
                        null, RefreshConfig.propertyFor(matcher.group(1)), 0);
                if (mode == 3 || mode == 4) return mode;
            } catch (Throwable ignored) {
                return 0;
            }
        }
        return 0;
    }
}
