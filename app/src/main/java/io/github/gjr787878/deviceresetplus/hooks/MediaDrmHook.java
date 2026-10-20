package io.github.gjr787878.deviceresetplus.hooks;

import android.media.MediaDrm;

import java.util.UUID;

import io.github.gjr787878.deviceresetplus.xposed.Identity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * P0: Hook Widevine DRM 设备唯一 ID。
 * MediaDrm.getPropertyByteArray("deviceUniqueId") 返回硬件级别的设备标识，
 * 存储在 /data/vendor/mediadrm/，clean flash 才会重新生成。
 * 很多视频/金融/直播应用用它做主设备指纹。
 */
public class MediaDrmHook {

    // Widevine UUID
    private static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

    public static void install(XC_LoadPackage.LoadPackageParam lpparam, Identity identity) {
        if (identity.drmId == null) return;

        // Hook MediaDrm.getPropertyByteArray(String)
        try {
            XposedHelpers.findAndHookMethod(
                    MediaDrm.class,
                    "getPropertyByteArray",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                String propName = (String) param.args[0];
                                if ("deviceUniqueId".equals(propName)
                                        || "deviceUniqueId".equalsIgnoreCase(propName)) {
                                    byte[] fake = android.util.Base64.decode(
                                            identity.drmId, android.util.Base64.NO_WRAP);
                                    param.setResult(fake);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[DRS+] MediaDrm getPropertyByteArray error: " + t.getMessage());
                            }
                        }
                    }
            );
            XposedBridge.log("[DRS+] MediaDrm getPropertyByteArray hook installed");
        } catch (Throwable t) {
            XposedBridge.log("[DRS+] MediaDrm getPropertyByteArray hook failed: " + t.getMessage());
        }

        // Hook MediaDrm.getPropertyString(String)
        try {
            XposedHelpers.findAndHookMethod(
                    MediaDrm.class,
                    "getPropertyString",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                String propName = (String) param.args[0];
                                if ("deviceUniqueId".equalsIgnoreCase(propName)) {
                                    param.setResult(identity.drmId);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("[DRS+] MediaDrm getPropertyString error: " + t.getMessage());
                            }
                        }
                    }
            );
        } catch (Throwable ignored) {}

        // Hook 构造函数：部分应用通过 new MediaDrm(UUID) 后立即读取
        // 这里不拦截构造，只拦截属性读取即可
    }
}
