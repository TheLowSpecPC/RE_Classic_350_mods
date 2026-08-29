#include <stdio.h>
#include "pico/stdlib.h"
#include "pico/time.h"
#include "FreeRTOS.h"
#include "task.h"
#include "lvgl.h"
#include "st7735.h"
#include "ui.h"

// 1. Define your screen resolution (based on your EEZ Studio settings)
#define MY_DISP_HOR_RES 128
#define MY_DISP_VER_RES 160
#define BYTE_PER_PIXEL 2 // RGB565 (16-bit color)

// 2. Allocate a draw buffer for LVGL (1/10th screen size is standard)
static uint8_t draw_buf[MY_DISP_HOR_RES * MY_DISP_VER_RES / 10 * BYTE_PER_PIXEL];

TaskHandle_t lvglTaskHandle;

// 3. Create the flush callback to push pixels to the ST7735
void my_disp_flush(lv_display_t * disp, const lv_area_t * area, uint8_t * px_map)
{
    uint16_t width = area->x2 - area->x1 + 1;
    uint16_t height = area->y2 - area->y1 + 1;

    // Send the rendered pixels to the hardware
    // Note: Ensure the arguments match your specific LCD_WriteBitmap definition
    LCD_WriteBitmap(area->x1, area->y1, width, height, (uint16_t *)px_map);

    // Tell LVGL the flushing is complete
    lv_display_flush_ready(disp);
}

void lvgl_task(void *p) {
    absolute_time_t start = get_absolute_time();
    bool tach = false;
    int16_t step = 10; // Controls the speed and direction of the needle

    while (1) {
        if(get_absolute_time() - start >= 3000000) { // 3 seconds
            loadScreen(2);
            tach = true;
        }

        if(tach) {
            //lv_arc_set_value(objects.tachometer_arc, (lv_arc_get_value(objects.tachometer_arc) + 10) % 3300);
            //lv_image_set_rotation(objects.gauge_needle, (lv_image_get_rotation(objects.gauge_needle) + 10) % 3300);

            // 2. Read the current value
            int32_t current_val = lv_arc_get_value(objects.tachometer_arc);
            
            // 3. Add the step (moves up if positive, down if negative)
            current_val += step;

            // 4. Check boundaries and reverse direction if limits are hit
            if(current_val >= 3300) {
                current_val = 3300;
                step = -10; // Reverse direction to sweep down
            } else if(current_val <= 360) {
                current_val = 360;
                step = 10;  // Reverse direction to sweep up
            }

            // 5. Apply the synchronized value to both the arc and the needle
            lv_arc_set_value(objects.tachometer_arc, current_val);
            lv_image_set_rotation(objects.gauge_needle, current_val);
        }

        lv_tick_inc(5);
        lv_timer_handler(); 
        vTaskDelay(pdMS_TO_TICKS(5));
    }
}

int main()
{
    stdio_init_all();
    
    // Initialize the LCD hardware
    LCD_initDisplay(INITR_BLACKTAB);
    LCD_setRotation(2);

    // Initialize the LVGL core
    lv_init();

    // 4. Register the display with LVGL
    lv_display_t * disp = lv_display_create(MY_DISP_HOR_RES, MY_DISP_VER_RES);
    lv_display_set_flush_cb(disp, my_disp_flush);
    lv_display_set_buffers(disp, draw_buf, NULL, sizeof(draw_buf), LV_DISPLAY_RENDER_MODE_PARTIAL);

    // Initialize the EEZ Studio UI
    ui_init();

    // Create the LVGL processing thread
    xTaskCreate(lvgl_task, "LVGL_Task", 4096, NULL, 1, &lvglTaskHandle);

    // Start FreeRTOS kernel
    vTaskStartScheduler();

    panic("RTOS kernel not running!"); 
}