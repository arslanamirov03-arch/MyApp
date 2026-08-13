cmake_minimum_required(VERSION 3.22)
project(hoerpraxis)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/whisper)

set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_DIR}/ggml ggml_build)

add_library(hoerpraxis SHARED
    ${WHISPER_DIR}/src/whisper.cpp
    jni.cpp
)

target_include_directories(hoerpraxis PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
)

target_compile_definitions(hoerpraxis PUBLIC GGML_USE_CPU)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(hoerpraxis PRIVATE -march=armv8.2-a+fp16)
endif ()

target_compile_options(hoerpraxis PRIVATE -O3 -ffunction-sections -fdata-sections)
target_link_options(hoerpraxis PRIVATE -Wl,--gc-sections)

find_library(LOG_LIB log)
target_link_libraries(hoerpraxis ${LOG_LIB} android ggml)
