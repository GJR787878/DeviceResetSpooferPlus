cmake_minimum_required(VERSION 3.22.1)
project(drs_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ===== Dobby (inline hook 框架) =====
# CI 环境中由 workflow 预先 clone 到 ${CMAKE_CURRENT_SOURCE_DIR}/Dobby
# 本地开发时如果目录不存在，尝试 FetchContent
set(DOBBY_DIR "${CMAKE_CURRENT_SOURCE_DIR}/Dobby")
if(EXISTS "${DOBBY_DIR}/CMakeLists.txt")
    message(STATUS "Using local Dobby from ${DOBBY_DIR}")
    set(DOBBY_GENERATE_SHARED OFF CACHE BOOL "" FORCE)
    set(DOBBY_DEBUG OFF CACHE BOOL "" FORCE)
    add_subdirectory(${DOBBY_DIR} dobby_build)
else()
    message(STATUS "Local Dobby not found, using FetchContent")
    include(FetchContent)
    FetchContent_Declare(
        dobby
        GIT_REPOSITORY https://github.com/jmgao/Dobby.git
        GIT_TAG        master
        GIT_SHALLOW    TRUE
    )
    set(DOBBY_GENERATE_SHARED OFF CACHE BOOL "" FORCE)
    set(DOBBY_DEBUG OFF CACHE BOOL "" FORCE)
    FetchContent_MakeAvailable(dobby)
endif()

add_library(drs_native SHARED
    native-lib.cpp
)

target_link_libraries(drs_native
    dobby
    log
)
