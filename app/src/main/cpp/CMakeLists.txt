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

# ビルド ID を付けない (1.0.80・F-Droid の再現可能ビルド)。AGP の release は
# RelWithDebInfo (-g 付き) でビルドし、APK に入れるときにデバッグ情報を削る。
# ビルド ID は削る前の中身から計算されるので、デバッグ情報に入る「ビルドした場所の
# パス」で値が変わり、GitHub と F-Droid の APK がこの 20 バイトだけ一致しなかった。
target_link_options(z2term PRIVATE "-Wl,--build-id=none")
