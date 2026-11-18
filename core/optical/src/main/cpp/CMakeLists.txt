cmake_minimum_required(VERSION 3.22.1)
project("pharos_optical" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    pharos_optical
    SHARED
    pharos_optical_jni.cpp
)

# Mandatory 2026 Google Play 16 KB Memory Page Alignment
target_link_options(
    pharos_optical
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

find_library(log-lib log)

target_link_libraries(
    pharos_optical
    ${log-lib}
)
