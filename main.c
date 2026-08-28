#include <stdio.h>
#include "pico/stdlib.h"
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
    while (1) {
        // Tell LVGL to process its internal timers and redraw the screen
        // Also increment the tick for LVGL's internal timing
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