# Create a static library for the generated UI
add_library(classic350_ui STATIC)

# Automatically grab all generated C files in the ui directory.
# (Using GLOB is generally frowned upon in CMake, but it is highly recommended 
# for auto-generated UI folders so you don't have to manually update this list).
file(GLOB UI_SOURCES CONFIGURE_DEPENDS "src/ui/*.c")

# Add the sources to the library
target_sources(classic350_ui PRIVATE ${UI_SOURCES})

# Expose the include directory so your main.c can find "ui.h"
target_include_directories(classic350_ui PUBLIC ${CMAKE_CURRENT_LIST_DIR}/src/ui)

# Link LVGL so the UI files can find "lvgl.h"
target_link_libraries(classic350_ui PRIVATE lvgl)