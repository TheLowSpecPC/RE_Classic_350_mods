add_subdirectory(freertos)
add_subdirectory(lvgl)
add_subdirectory(pico-displayDrivs)

target_include_directories(lvgl PUBLIC
    ./
)

target_link_libraries(lvgl PRIVATE freertos)
target_link_libraries(lvgl_examples PRIVATE freertos)