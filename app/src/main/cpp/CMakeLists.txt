cmake_minimum_required(VERSION 3.22.1)
project("z2term")

# C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 最適化フラグ
set(CMAKE_CXX_FLAGS_RELEASE "-O2 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O0 -g")

# 共有ライブラリ libz2term.so
add_library(
    z2term
    SHARED
    pty_jni.cpp
)

# Android NDK の log ライブラリをリンク
find_library(log-lib log)

target_link_libraries(
    z2term
    ${log-lib}
)
