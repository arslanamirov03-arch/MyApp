# Install script for directory: /home/user/MyApp/app/src/main/cpp/whisper/ggml

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "/usr/local")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "Release")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "1")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "TRUE")
endif()

# Set default install directory permissions.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "/tmp/claude-0/-home-user-MyApp/f45a90a0-5b78-581d-8464-0210cfbfc467/scratchpad/android-sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objdump")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("/home/user/MyApp/app/.cxx/Release/6a6f3z2d/arm64-v8a/ggml_build/src/cmake_install.cmake")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "/home/user/MyApp/app/.cxx/Release/6a6f3z2d/arm64-v8a/ggml_build/src/libggml.a")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include" TYPE FILE FILES
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-cpu.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-alloc.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-backend.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-blas.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-cann.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-cpp.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-cuda.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-opt.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-metal.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-rpc.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-sycl.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-vulkan.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/ggml-webgpu.h"
    "/home/user/MyApp/app/src/main/cpp/whisper/ggml/include/gguf.h"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "/home/user/MyApp/app/.cxx/Release/6a6f3z2d/arm64-v8a/ggml_build/src/libggml-base.a")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/ggml" TYPE FILE FILES
    "/home/user/MyApp/app/.cxx/Release/6a6f3z2d/arm64-v8a/ggml_build/ggml-config.cmake"
    "/home/user/MyApp/app/.cxx/Release/6a6f3z2d/arm64-v8a/ggml_build/ggml-version.cmake"
    )
endif()

