# SPDX-FileCopyrightText: 2026 ChenIME contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds the official Ismantic/Handwritten HCCR JNI runtime (Apache-2.0)
# against a prebuilt ncnn Android package.

cmake_minimum_required(VERSION 3.18)
project(hccr_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT HCCR_NCNN_ROOT)
  message(FATAL_ERROR "HCCR_NCNN_ROOT is not set. Run the downloadHandwrittenDeps Gradle task first.")
endif()

set(NCNN_ROOT ${HCCR_NCNN_ROOT}/${ANDROID_ABI})
if(NOT EXISTS ${NCNN_ROOT})
  message(FATAL_ERROR "ncnn-android not found for ${ANDROID_ABI}: ${NCNN_ROOT}")
endif()

set(ncnn_DIR ${NCNN_ROOT}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

set(HCCR_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/handwritten)
set(HCCR_CORE_CPP ${HCCR_ROOT}/src/cpp)

add_library(hccr_jni SHARED
    ${HCCR_ROOT}/runtime/src/main/jni/hccr_jni.cc
    ${HCCR_CORE_CPP}/preprocess.c
)
target_include_directories(hccr_jni PRIVATE ${HCCR_CORE_CPP})
target_link_libraries(hccr_jni PRIVATE ncnn android log)
