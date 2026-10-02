cmake_minimum_required(VERSION 3.22.1)
project(anchor_llama)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

option(ANCHOR_ENABLE_OPENCL "Enable GGML OpenCL backend for Adreno GPU offload" OFF)
set(ANCHOR_OPENCL_LINKED 0)

if (ANDROID AND ANCHOR_ENABLE_OPENCL)
    include(FetchContent)
    FetchContent_Declare(
        opencl_headers
        GIT_REPOSITORY https://github.com/KhronosGroup/OpenCL-Headers.git
        GIT_TAG v2024.10.24
        GIT_SHALLOW TRUE
    )
    FetchContent_MakeAvailable(opencl_headers)

    set(OpenCL_INCLUDE_DIR "${opencl_headers_SOURCE_DIR}" CACHE PATH "" FORCE)
    set(OpenCL_INCLUDE_DIRS "${opencl_headers_SOURCE_DIR}" CACHE PATH "" FORCE)

    add_library(opencl_android_stub SHARED opencl_android_stub.c)
    target_link_options(opencl_android_stub PRIVATE "-Wl,--allow-shlib-undefined")
    set(OpenCL_LIBRARY opencl_android_stub CACHE FILEPATH "" FORCE)
    set(OpenCL_LIBRARIES opencl_android_stub CACHE STRING "" FORCE)

    set(GGML_OPENCL ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_USE_ADRENO_KERNELS ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_EMBED_KERNELS ON CACHE BOOL "" FORCE)
    set(ANCHOR_OPENCL_LINKED 1)
else()
    set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
endif()

include(FetchContent)
FetchContent_Declare(
    llama
    URL https://codeload.github.com/ggml-org/llama.cpp/tar.gz/refs/tags/b9371
    URL_HASH SHA256=3684b9a214302cc69e0bc6a52c9b7d707515305273a109d9f71e65934f91731d
)
FetchContent_MakeAvailable(llama)

if (TARGET ggml-opencl)
    target_link_options(ggml-opencl PRIVATE "-Wl,--allow-shlib-undefined")
endif()

add_library(anchor_llama SHARED anchor_llama_jni.cpp)
target_include_directories(anchor_llama PRIVATE ${llama_SOURCE_DIR}/include)
target_link_libraries(anchor_llama llama android log)

if (ANCHOR_OPENCL_LINKED)
    target_link_libraries(anchor_llama opencl_android_stub)
    target_link_options(anchor_llama PRIVATE
        "-Wl,--allow-shlib-undefined"
        "-Wl,--unresolved-symbols=ignore-all"
    )
    target_compile_definitions(anchor_llama PRIVATE ANCHOR_OPENCL_LINKED=1)
endif()
