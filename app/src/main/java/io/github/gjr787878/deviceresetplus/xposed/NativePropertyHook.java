package io.github.gjr787878.deviceresetplus.xposed;

import de.robv.android.xposed.XposedBridge;

/**
 * P1: Native 系统属性 hook 的 Java 桥接层。
 * 将 Identity 中的伪装值传递给 native 层，
 * native 层用 Dobby inline hook __system_property_get。
 *
 * 这样即使应用通过 JNI/native 代码直接读取 ro.serialno 等属性，也会返回伪装值。
 */
public class NativePropertyHook {

    private static boolean libraryLoaded = false;
    private static boolean loadAttempted = false;

    /** 尝试加载 native 库。失败不影响 Java 层 hook。 */
    public static synchronized void ensureLoaded() {
        if (loadAttempted) return;
        loadAttempted = true;
        try {
            System.loadLibrary("drs_native");
            libraryLoaded = true;
            XposedBridge.log("[DRS+] native library drs_native loaded");
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log("[DRS+] native library not available: " + e.getMessage());
            libraryLoaded = false;
        }
    }

    /** 设置单个伪装属性。value 为 null 表示删除。 */
    public static native void setSpoofedProperty(String name, String value);

    /** 清除所有伪装属性。 */
    public static native void clearAll();

    /**
     * 将 Identity 中的所有伪装值同步到 native 层。
     * 在 MainHook 安装完所有 Java 层 hook 后调用。
     */
    public static void syncIdentity(Identity identity) {
        if (!libraryLoaded) {
            ensureLoaded();
            if (!libraryLoaded) return;
        }
        if (identity == null) return;

        try {
            // Build 相关属性
            setPropIfNotNull("ro.product.brand", identity.brand);
            setPropIfNotNull("ro.product.model", identity.model);
            setPropIfNotNull("ro.product.manufacturer", identity.manufacturer);
            setPropIfNotNull("ro.product.device", identity.device);
            setPropIfNotNull("ro.product.name", identity.product);
            setPropIfNotNull("ro.hardware", identity.hardware);
            setPropIfNotNull("ro.build.fingerprint", identity.fingerprint);
            setPropIfNotNull("ro.bootimage.build.fingerprint", identity.fingerprint);
            setPropIfNotNull("ro.build.id", identity.buildId);
            setPropIfNotNull("ro.build.display.id", identity.buildDisplay);
            setPropIfNotNull("ro.build.tags", identity.buildTags);
            setPropIfNotNull("ro.build.type", identity.buildType);
            setPropIfNotNull("ro.build.user", identity.buildUser);
            setPropIfNotNull("ro.build.host", identity.buildHost);
            setPropIfNotNull("ro.bootloader", identity.bootloader);
            setPropIfNotNull("ro.build.version.incremental", identity.buildId);

            // 序列号
            setPropIfNotNull("ro.serialno", identity.serial);
            setPropIfNotNull("ro.boot.serialno", identity.serial);
            setPropIfNotNull("ro.ril.serialno", identity.serial);

            // 运营商
            setPropIfNotNull("gsm.sim.operator.numeric", identity.simOperator);
            setPropIfNotNull("gsm.sim.operator.alpha", identity.simOperatorName);
            setPropIfNotNull("gsm.sim.operator.iso-country", identity.simCountryIso);
            setPropIfNotNull("gsm.operator.numeric", identity.networkOperator);
            setPropIfNotNull("gsm.operator.alpha", identity.networkOperatorName);
            setPropIfNotNull("gsm.operator.iso-country", identity.networkCountryIso);

            // MAC 地址
            setPropIfNotNull("ro.boot.wifimacaddr", identity.macAddress);
            setPropIfNotNull("ro.boot.btmacaddr", identity.bluetoothMac);

            XposedBridge.log("[DRS+] native properties synced from identity");
        } catch (Throwable t) {
            XposedBridge.log("[DRS+] syncIdentity native error: " + t.getMessage());
        }
    }

    private static void setPropIfNotNull(String name, String value) {
        if (value != null && !value.isEmpty()) {
            try {
                setSpoofedProperty(name, value);
            } catch (Throwable ignored) {}
        }
    }
}
