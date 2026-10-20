cmake_minimum_required(VERSION 3.22.1)
project(drs_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(drs_native SHARED
    native-lib.cpp
)

target_link_libraries(drs_native
    log
)
