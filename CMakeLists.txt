# Generated Cmake Pico project file

cmake_minimum_required(VERSION 3.13)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Initialise pico_sdk from installed location
# (note this can come from environment, CMake cache etc)

# == DO NOT EDIT THE FOLLOWING LINES for the Raspberry Pi Pico VS Code Extension to work ==
if(WIN32)
    set(USERHOME $ENV{USERPROFILE})
else()
    set(USERHOME $ENV{HOME})
endif()
set(sdkVersion 2.3.0)
set(toolchainVersion 15_2_Rel1)
set(picotoolVersion 2.3.0)
set(picoVscode ${USERHOME}/.pico-sdk/cmake/pico-vscode.cmake)
if (EXISTS ${picoVscode})
    include(${picoVscode})
endif()
# ====================================================================================
set(PICO_BOARD pico_w CACHE STRING "Board type")

# Pull in Raspberry Pi Pico SDK (must be before project)
include(pico_sdk_import.cmake)

project(RE_Classic_350_mods C CXX ASM)

# Initialise the Raspberry Pi Pico SDK
pico_sdk_init()

add_subdirectory(libraries)
add_subdirectory(classic350_ui)

# Add executable. Default name is the project name, version 0.1

add_executable(RE_Classic_350_mods main.c )

pico_set_program_name(RE_Classic_350_mods "RE_Classic_350_mods")
pico_set_program_version(RE_Classic_350_mods "0.1")

# Modify the below lines to enable/disable output over UART/USB
pico_enable_stdio_uart(RE_Classic_350_mods 0)
pico_enable_stdio_usb(RE_Classic_350_mods 0)

# Add the standard library to the build
target_link_libraries(RE_Classic_350_mods
        pico_stdlib)

# Add the standard include files to the build
target_include_directories(RE_Classic_350_mods PRIVATE
        ${CMAKE_CURRENT_LIST_DIR}
)

# Add any user requested libraries
target_link_libraries(RE_Classic_350_mods 
        st7735 
        freertos 
        lvgl
        classic350_ui)

pico_add_extra_outputs(RE_Classic_350_mods)

