package io.github.gjr787878.deviceresetplus.hooks;

import android.bluetooth.BluetoothAdapter;

import io.github.gjr787878.deviceresetplus.xposed.Identity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * P0: Hook 蓝牙 MAC 地址。
 * BluetoothAdapter.getAddress() 虽已废弃但仍可通过反射或隐藏 API 获取。
 * 真实 MAC 存储在 /persist 分区，clean flash 也不一定清除。
 */
public class BluetoothMacHook {

    public static void install(XC_LoadPackage.LoadPackageParam lpparam, Identity identity) {
        if (identity.bluetoothMac == null) return;

        // Hook BluetoothAdapter.getAddress()
        try {
            XposedHelpers.findAndHookMethod(
                    BluetoothAdapter.class,
                    "getAddress",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                param.setResult(identity.bluetoothMac);
                            } catch (Throwable t) {
                                XposedBridge.log("[DRS+] BluetoothAdapter getAddress error: " + t.getMessage());
                            }
                        }
                    }
            );
            XposedBridge.log("[DRS+] BluetoothAdapter getAddress hook installed");
        } catch (Throwable t) {
            XposedBridge.log("[DRS+] BluetoothAdapter getAddress hook failed: " + t.getMessage());
        }

        // Hook 反射字段 mAddress（部分应用通过反射读取）
        try {
            XposedHelpers.findAndHookMethod(
                    BluetoothAdapter.class,
                    "getDefaultAdapter",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object adapter = param.getResult();
                                if (adapter != null) {
                                    XposedHelpers.setObjectField(adapter, "mAddress", identity.bluetoothMac);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
            );
        } catch (Throwable ignored) {}
    }
}
