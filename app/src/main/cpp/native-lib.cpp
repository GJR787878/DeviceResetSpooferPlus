// P1: Native 层系统属性 hook
// 使用 Dobby inline hook libc.so 中的 __system_property_get，
// 拦截应用通过 JNI/native 代码直接读取系统属性的行为（绕过 Java 层 Build hook）。
//
// Java 层通过 NativePropertyHook.setSpoofedProperty(name, value) 设置伪装值，
// native 层维护映射表，hook 函数查表返回伪装值。

#include <jni.h>
#include <string>
#include <map>
#include <mutex>
#include <android/log.h>
#include "dobby.h"

#define LOG_TAG "DRS+Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 原始函数指针
static int (*orig_system_property_get)(const char *name, char *value) = nullptr;

// 伪装属性映射表（线程安全）
static std::map<std::string, std::string> g_spoofed_props;
static std::mutex g_props_mutex;

// hook 函数：替换 __system_property_get
static int hook_system_property_get(const char *name, char *value) {
    if (name != nullptr && value != nullptr) {
        std::lock_guard<std::mutex> lock(g_props_mutex);
        auto it = g_spoofed_props.find(name);
        if (it != g_spoofed_props.end()) {
            // 返回伪装值
            strncpy(value, it->second.c_str(), 91);  // PROP_VALUE_MAX = 92
            value[91] = '\0';
            return (int)it->second.length();
        }
    }
    // 不在伪装表中，调用原始函数
    return orig_system_property_get(name, value);
}

// 也 hook __system_property_read_callback（Android O+ 新 API）
// 签名：void __system_property_read_callback(const prop_info *pi,
//     void (*callback)(void *cookie, const char *name, const char *value, uint32_t serial),
//     void *cookie)
// 这个比较复杂，因为需要先 find 再 read。暂时只 hook __system_property_get，
// 它是最常用的 API。

extern "C" JNIEXPORT void JNICALL
Java_io_github_gjr787878_deviceresetplus_xposed_NativePropertyHook_setSpoofedProperty(
        JNIEnv *env, jobject thiz, jstring name, jstring value) {
    if (name == nullptr) return;
    const char *nameStr = env->GetStringUTFChars(name, nullptr);
    std::string propName(nameStr ? nameStr : "");
    env->ReleaseStringUTFChars(name, nameStr);

    if (value == nullptr) {
        // value 为 null 表示删除伪装
        std::lock_guard<std::mutex> lock(g_props_mutex);
        g_spoofed_props.erase(propName);
        LOGI("removed spoofed prop: %s", propName.c_str());
        return;
    }

    const char *valueStr = env->GetStringUTFChars(value, nullptr);
    std::string propValue(valueStr ? valueStr : "");
    env->ReleaseStringUTFChars(value, valueStr);

    std::lock_guard<std::mutex> lock(g_props_mutex);
    g_spoofed_props[propName] = propValue;
    LOGI("set spoofed prop: %s = %s", propName.c_str(), propValue.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_gjr787878_deviceresetplus_xposed_NativePropertyHook_clearAll(
        JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_props_mutex);
    g_spoofed_props.clear();
    LOGI("cleared all spoofed props");
}

// JNI_OnLoad：库加载时执行 hook
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("DRS+ native library loaded");

    // 查找 __system_property_get 符号
    // 它在 libc.so 中，dlsym 可以直接找到
    void *target = DobbySymbolResolver(nullptr, "__system_property_get");
    if (target == nullptr) {
        // 尝试从 libc.so 显式查找
        target = DobbySymbolResolver("libc.so", "__system_property_get");
    }

    if (target != nullptr) {
        LOGI("found __system_property_get at %p", target);
        // 执行 inline hook
        DobbyHook(target, (void *)hook_system_property_get, (void **)&orig_system_property_get);
        LOGI("__system_property_get hooked successfully");
    } else {
        LOGE("failed to find __system_property_get symbol");
    }

    return JNI_VERSION_1_6;
}
