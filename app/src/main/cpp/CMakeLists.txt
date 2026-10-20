cmake_minimum_required(VERSION 3.22.1)
project(drs_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ===== Dobby (inline hook 框架) =====
# 使用 FetchContent 从 GitHub 拉取 Dobby 源码
include(FetchContent)
FetchContent_Declare(
    dobby
    GIT_REPOSITORY https://github.com/jmgao/Dobby.git
    GIT_TAG        master
    GIT_SHALLOW    TRUE
)
# 关闭 Dobby 的示例和测试
set(DOBBY_GENERATE_SHARED OFF CACHE BOOL "" FORCE)
set(DOBBY_DEBUG OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(dobby)

add_library(drs_native SHARED
    native-lib.cpp
)

target_link_libraries(drs_native
    dobby
    log
)
