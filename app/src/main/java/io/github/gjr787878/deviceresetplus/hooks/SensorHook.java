package io.github.gjr787878.deviceresetplus.hooks;

import android.hardware.Sensor;

import io.github.gjr787878.deviceresetplus.xposed.Identity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * P2: Hook 传感器信息（名称/厂商/版本）。
 * 部分反欺诈 SDK 通过 Sensor.getName()/getVendor()/getVersion() 组合构建设备指纹。
 */
public class SensorHook {

    public static void install(XC_LoadPackage.LoadPackageParam lpparam, Identity identity) {
        if (identity.sensorVendor == null && identity.sensorName == null) return;

        // Hook Sensor.getName()
        try {
            XposedHelpers.findAndHookMethod(
                    Sensor.class,
                    "getName",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (identity.sensorName != null) {
                                    param.setResult(identity.sensorName);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[DRS+] Sensor getName error: " + t.getMessage());
                            }
                        }
                    }
            );
        } catch (Throwable ignored) {}

        // Hook Sensor.getVendor()
        try {
            XposedHelpers.findAndHookMethod(
                    Sensor.class,
                    "getVendor",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (identity.sensorVendor != null) {
                                    param.setResult(identity.sensorVendor);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[DRS+] Sensor getVendor error: " + t.getMessage());
                            }
                        }
                    }
            );
        } catch (Throwable ignored) {}

        // Hook Sensor.getVersion()
        try {
            XposedHelpers.findAndHookMethod(
                    Sensor.class,
                    "getVersion",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (identity.sensorVersion != null) {
                                    param.setResult(Integer.parseInt(identity.sensorVersion));
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
            );
        } catch (Throwable ignored) {}

        XposedBridge.log("[DRS+] Sensor hooks installed");
    }
}
