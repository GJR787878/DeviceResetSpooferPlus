package io.github.gjr787878.deviceresetplus.hooks;

import android.content.ContentResolver;
import android.provider.Settings;

import io.github.gjr787878.deviceresetplus.xposed.Identity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * P2: Hook Settings.Global / Settings.System 中的额外标识符。
 * 部分应用从 Settings.Global 读取 android_id 或 device_name 作为辅助指纹。
 */
public class SettingsGlobalHook {

    public static void install(XC_LoadPackage.LoadPackageParam lpparam, Identity identity) {
        // Hook Settings.Global.getString(ContentResolver, String)
        try {
            XposedHelpers.findAndHookMethod(
                    Settings.Global.class,
                    "getString",
                    ContentResolver.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args.length > 1) {
                                    String key = (String) param.args[1];
                                    if ("android_id".equals(key) && identity.androidId != null) {
                                        param.setResult(identity.androidId);
                                    } else if ("device_name".equals(key) && identity.model != null) {
                                        param.setResult(identity.model);
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[DRS+] Settings.Global getString error: " + t.getMessage());
                            }
                        }
                    }
            );
        } catch (Throwable ignored) {}

        // Hook Settings.Global.getStringForUser(ContentResolver, String, int)
        try {
            XposedHelpers.findAndHookMethod(
                    Settings.Global.class,
                    "getStringForUser",
                    ContentResolver.class,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args.length > 1) {
                                    String key = (String) param.args[1];
                                    if ("android_id".equals(key) && identity.androidId != null) {
                                        param.setResult(identity.androidId);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
            );
        } catch (Throwable ignored) {}

        // Hook Settings.System.getString(ContentResolver, String)
        try {
            XposedHelpers.findAndHookMethod(
                    Settings.System.class,
                    "getString",
                    ContentResolver.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args.length > 1) {
                                    String key = (String) param.args[1];
                                    if ("android_id".equals(key) && identity.androidId != null) {
                                        param.setResult(identity.androidId);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
            );
        } catch (Throwable ignored) {}

        XposedBridge.log("[DRS+] SettingsGlobal hooks installed");
    }
}
