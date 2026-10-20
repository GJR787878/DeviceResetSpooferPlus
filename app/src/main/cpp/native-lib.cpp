// P1: Native 层系统属性 hook（自实现 PLT hook，无第三方依赖）
//
// 原理：遍历所有已加载的共享库，解析 ELF 的 .dynamic 段，
// 找到每个库中调用 __system_property_get 的 PLT 重定位条目，
// 修改其 GOT 条目指向我们的 hook 函数。
//
// 这样应用通过 JNI/native 代码调用 __system_property_get 时，
// 会走 PLT → GOT → 我们的 hook 函数，返回伪装值。
//
// 不依赖 Dobby 等第三方 inline hook 框架，纯自实现，CI 构建无需网络下载。

#include <jni.h>
#include <string>
#include <map>
#include <mutex>
#include <vector>
#include <cstring>
#include <cstdio>
#include <dlfcn.h>
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>
#include <elf.h>

#define LOG_TAG "DRS+Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 伪装属性映射表
static std::map<std::string, std::string> g_spoofed_props;
static std::mutex g_props_mutex;

// 原始 __system_property_get 函数指针
typedef int (*system_property_get_t)(const char *name, char *value);
static system_property_get_t g_orig_prop_get = nullptr;

// hook 函数
static int hook_system_property_get(const char *name, char *value) {
    if (name != nullptr && value != nullptr) {
        std::lock_guard<std::mutex> lock(g_props_mutex);
        auto it = g_spoofed_props.find(name);
        if (it != g_spoofed_props.end()) {
            strncpy(value, it->second.c_str(), 91);
            value[91] = '\0';
            return (int)it->second.length();
        }
    }
    if (g_orig_prop_get) {
        return g_orig_prop_get(name, value);
    }
    return 0;
}

// ========== PLT Hook 实现 ==========

struct LibraryInfo {
    std::string path;
    uintptr_t base_addr;
};

// 读取 /proc/self/maps 获取所有已加载的共享库
static std::vector<LibraryInfo> get_loaded_libraries() {
    std::vector<LibraryInfo> libs;
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) return libs;

    char line[512];
    std::map<std::string, uintptr_t> seen;
    while (fgets(line, sizeof(line), fp)) {
        uintptr_t start, end;
        char perms[8], path[256] = {0};
        if (sscanf(line, "%lx-%lx %7s %*x %*x:%*x %*d %255s",
                   &start, &end, perms, path) >= 4) {
            if (perms[2] == 'x' && strstr(path, ".so")) {
                if (seen.find(path) == seen.end()) {
                    seen[path] = start;
                    libs.push_back({path, start});
                }
            }
        }
    }
    fclose(fp);
    return libs;
}

// 修改内存页保护为可写
static bool make_writable(uintptr_t addr, size_t size) {
    uintptr_t page_start = addr & ~(static_cast<uintptr_t>(sysconf(_SC_PAGESIZE)) - 1);
    size_t page_size = ((addr + size - page_start) + sysconf(_SC_PAGESIZE) - 1)
                       & ~(static_cast<size_t>(sysconf(_SC_PAGESIZE)) - 1);
    return mprotect(reinterpret_cast<void*>(page_start), page_size,
                    PROT_READ | PROT_WRITE | PROT_EXEC) == 0;
}

// 对单个库执行 PLT hook
static bool hook_library_plt(const LibraryInfo &lib, const char *symbol, void *hook_func) {
    uint8_t *base = reinterpret_cast<uint8_t*>(lib.base_addr);

    // 读取 ELF header
    const ElfW(Ehdr) *ehdr = reinterpret_cast<const ElfW(Ehdr)*>(base);
    if (memcmp(ehdr->e_ident, ELFMAG, SELFMAG) != 0) return false;

    // 找到 .dynamic 段
    const ElfW(Phdr) *phdr = reinterpret_cast<const ElfW(Phdr)*>(base + ehdr->e_phoff);
    const ElfW(Dyn) *dynamic = nullptr;
    for (int i = 0; i < ehdr->e_phnum; i++) {
        if (phdr[i].p_type == PT_DYNAMIC) {
            dynamic = reinterpret_cast<const ElfW(Dyn)*>(base + phdr[i].p_vaddr);
            break;
        }
    }
    if (!dynamic) return false;

    // 从 .dynamic 段解析各种表
    const ElfW(Sym) *symtab = nullptr;
    const char *strtab = nullptr;
    const ElfW(Rela) *jmprel = nullptr;
    size_t pltrel_count = 0;
    uintptr_t *pltgot = nullptr;

    for (const ElfW(Dyn) *d = dynamic; d->d_tag != DT_NULL; d++) {
        switch (d->d_tag) {
            case DT_SYMTAB:
                symtab = reinterpret_cast<const ElfW(Sym)*>(base + d->d_un.d_ptr);
                break;
            case DT_STRTAB:
                strtab = reinterpret_cast<const char*>(base + d->d_un.d_ptr);
                break;
            case DT_JMPREL:
                jmprel = reinterpret_cast<const ElfW(Rela)*>(base + d->d_un.d_ptr);
                break;
            case DT_PLTRELSZ:
                pltrel_count = d->d_un.d_val / sizeof(ElfW(Rela));
                break;
            case DT_PLTGOT:
                pltgot = reinterpret_cast<uintptr_t*>(base + d->d_un.d_ptr);
                break;
        }
    }

    if (!symtab || !strtab || !jmprel || pltrel_count == 0) return false;

    // 遍历 PLT 重定位表，找到目标符号
    bool hooked = false;
    for (size_t i = 0; i < pltrel_count; i++) {
        const ElfW(Rela) *rela = &jmprel[i];
        uint32_t sym_idx = ELF64_R_SYM(rela->r_info);
        if (sym_idx == 0) continue;

        const ElfW(Sym) *sym = &symtab[sym_idx];
        const char *sym_name = strtab + sym->st_name;

        if (strcmp(sym_name, symbol) == 0) {
            // 修改 GOT 条目
            uintptr_t *got_entry = reinterpret_cast<uintptr_t*>(base + rela->r_offset);
            if (make_writable(reinterpret_cast<uintptr_t>(got_entry), sizeof(uintptr_t))) {
                // 保存原始函数地址（只保存一次）
                if (!g_orig_prop_get && *got_entry != 0) {
                    g_orig_prop_get = reinterpret_cast<system_property_get_t>(*got_entry);
                }
                *got_entry = reinterpret_cast<uintptr_t>(hook_func);
                hooked = true;
                LOGD("PLT hooked %s in %s at offset 0x%lx", symbol, lib.path.c_str(), rela->r_offset);
            }
        }
    }
    return hooked;
}

// 对所有已加载库执行 PLT hook
static void hook_all_libraries(const char *symbol, void *hook_func) {
    std::vector<LibraryInfo> libs = get_loaded_libraries();
    int count = 0;
    for (const auto &lib : libs) {
        // 跳过系统库自身（libc.so 中的定义不需要 hook）
        // 但需要 hook 所有调用方的 PLT
        if (hook_library_plt(lib, symbol, hook_func)) {
            count++;
        }
    }
    LOGI("PLT hook '%s' applied to %d libraries", symbol, count);
}

// ========== JNI 接口 ==========

extern "C" JNIEXPORT void JNICALL
Java_io_github_gjr787878_deviceresetplus_xposed_NativePropertyHook_setSpoofedProperty(
        JNIEnv *env, jobject thiz, jstring name, jstring value) {
    if (name == nullptr) return;
    const char *nameStr = env->GetStringUTFChars(name, nullptr);
    std::string propName(nameStr ? nameStr : "");
    env->ReleaseStringUTFChars(name, nameStr);

    if (value == nullptr) {
        std::lock_guard<std::mutex> lock(g_props_mutex);
        g_spoofed_props.erase(propName);
        return;
    }

    const char *valueStr = env->GetStringUTFChars(value, nullptr);
    std::string propValue(valueStr ? valueStr : "");
    env->ReleaseStringUTFChars(value, valueStr);

    std::lock_guard<std::mutex> lock(g_props_mutex);
    g_spoofed_props[propName] = propValue;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_gjr787878_deviceresetplus_xposed_NativePropertyHook_clearAll(
        JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_props_mutex);
    g_spoofed_props.clear();
}

// 库加载时执行 PLT hook
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("DRS+ native library loaded (PLT hook mode)");

    // 先通过 dlsym 获取原始函数地址（兜底）
    g_orig_prop_get = reinterpret_cast<system_property_get_t>(
            dlsym(RTLD_DEFAULT, "__system_property_get"));
    if (g_orig_prop_get) {
        LOGI("found original __system_property_get at %p", g_orig_prop_get);
    } else {
        LOGE("failed to find __system_property_get via dlsym");
    }

    // 对所有已加载库执行 PLT hook
    hook_all_libraries("__system_property_get", reinterpret_cast<void*>(hook_system_property_get));

    return JNI_VERSION_1_6;
}
