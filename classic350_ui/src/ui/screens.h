#ifndef EEZ_LVGL_UI_SCREENS_H
#define EEZ_LVGL_UI_SCREENS_H

#include <lvgl/lvgl.h>

#ifdef __cplusplus
extern "C" {
#endif

// Screens

enum ScreensEnum {
    _SCREEN_ID_FIRST = 1,
    SCREEN_ID_INTRO = 1,
    SCREEN_ID_TACHOMETER_SCREEN = 2,
    _SCREEN_ID_LAST = 2
};

typedef struct _objects_t {
    lv_obj_t *intro;
    lv_obj_t *tachometer_screen;
    lv_obj_t *tachometer_arc;
    lv_obj_t *tachometer_scale;
    lv_obj_t *lock;
    lv_obj_t *gauge_needle;
} objects_t;

extern objects_t objects;

void create_screen_intro();
void tick_screen_intro();

void create_screen_tachometer_screen();
void tick_screen_tachometer_screen();

void tick_screen_by_id(enum ScreensEnum screenId);
void tick_screen(int screen_index);

void create_screens();

#ifdef __cplusplus
}
#endif

#endif /*EEZ_LVGL_UI_SCREENS_H*/