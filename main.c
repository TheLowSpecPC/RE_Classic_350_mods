#include <stdio.h>
#include "pico/stdlib.h"
#include "pico/time.h"
#include "FreeRTOS.h"
#include "task.h"
#include "lvgl.h"
#include "st7735.h"
#include "ui.h"

// Define your screen resolution (based on your EEZ Studio settings)
#define MY_DISP_HOR_RES 128
#define MY_DISP_VER_RES 160
#define BYTE_PER_PIXEL 2 // RGB565 (16-bit color)

// Allocate a draw buffer for LVGL (1/10th screen size is standard)
static uint8_t draw_buf[MY_DISP_HOR_RES * MY_DISP_VER_RES / 10 * BYTE_PER_PIXEL];

// --- RPM Configuration Constants ---
#define PULSE_PIN 14              // GPIO pin for the sensor
#define DEBOUNCE_US 6000          // 6ms debounce
#define TIMEOUT_US 1500000        // 1.5 second timeout
#define MAX_RPM 5500              // Max RPM
#define ALPHA 0.3f                // Smoothing factor

// --- Volatile Global Variables ---
volatile uint64_t last_pulse_time = 0;
volatile uint64_t pulse_interval_us = 0;
volatile bool new_pulse_received = false;

// The global RPM variable shared between tasks
volatile int32_t global_rpm = 0;

// Linear Interpolation helper
int32_t linear_interp(int32_t in, int32_t in_min, int32_t in_max, int32_t out_min, int32_t out_max) {
    return (((in - in_min) * (out_max - out_min)) / (in_max - in_min)) + out_min;
}

// Create the flush callback to push pixels to the ST7735
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

// --- Hardware Interrupt (ISR) ---
void rpm_callback(uint gpio, uint32_t events) {
    uint64_t current_time = time_us_64();
    uint64_t elapsed = current_time - last_pulse_time;
    
    if (elapsed > DEBOUNCE_US) {
        pulse_interval_us = elapsed;
        last_pulse_time = current_time;
        new_pulse_received = true;
    }
}

// --- RPM Calculator ---
TaskHandle_t rpmTaskHandle;
void rpm_task(void *p) {
    int32_t current_rpm = 0;
    float smoothed_rpm = 0.0f; 

    while (1) {
        uint64_t now = time_us_64();
        uint64_t time_since_last = now - last_pulse_time;
        
        // Check if engine stopped
        if (time_since_last > TIMEOUT_US && last_pulse_time != 0) {
            current_rpm = 0;
            smoothed_rpm = 0.0f;
            last_pulse_time = 0;
            global_rpm = 0;
        }
        // Process new pulse data
        else if (new_pulse_received) {
            new_pulse_received = false;
            
            if (pulse_interval_us > 0) {
                current_rpm = 60000000 / pulse_interval_us; // Calculate RPM from microsecond interval
                smoothed_rpm = (ALPHA * current_rpm) + ((1.0f - ALPHA) * smoothed_rpm); // Apply smoothing
                
                // Cap the RPM so we don't exceed the gauge's bounds
                if(smoothed_rpm > MAX_RPM) smoothed_rpm = MAX_RPM;
                
                // Update the global variable safely
                global_rpm = (int32_t)smoothed_rpm;
            }
        }
        
        vTaskDelay(pdMS_TO_TICKS(20));
    }
}

TaskHandle_t lvglTaskHandle;
void lvgl_task(void *p) {
    absolute_time_t start = get_absolute_time();
    bool tach = false;

    while (1) {
        if(get_absolute_time() - start >= 3000000) { // 3 seconds
            loadScreen(2);
            tach = true;
        }

        if(tach) {
            int32_t mapped_val = linear_interp(global_rpm, 0, 8000, 360, 3300);

            char buffer_rpm[16];
            snprintf(buffer_rpm, sizeof(buffer_rpm), "%d", global_rpm);

            lv_arc_set_value(objects.tachometer_arc, mapped_val);
            lv_image_set_rotation(objects.gauge_needle, mapped_val);
            lv_label_set_text(objects.tach_reading, buffer_rpm);
            lv_obj_center(objects.tach_reading);
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

    // Register the display with LVGL
    lv_display_t * disp = lv_display_create(MY_DISP_HOR_RES, MY_DISP_VER_RES);
    lv_display_set_flush_cb(disp, my_disp_flush);
    lv_display_set_buffers(disp, draw_buf, NULL, sizeof(draw_buf), LV_DISPLAY_RENDER_MODE_PARTIAL);

    // Initialize the EEZ Studio UI
    ui_init();

    // --- GPIO Sensor Setup ---
    gpio_init(PULSE_PIN);
    gpio_set_dir(PULSE_PIN, GPIO_IN); // Set as input
    gpio_pull_up(PULSE_PIN);          // Enable pull up resistor

    // Attach the hardware interrupt to the pulse pin, triggering on the falling edge
    gpio_set_irq_enabled_with_callback(PULSE_PIN, GPIO_IRQ_EDGE_FALL, true, &rpm_callback);

    // Create the LVGL processing thread
    xTaskCreate(lvgl_task, "LVGL_Task", 4096, NULL, 1, &lvglTaskHandle);
    xTaskCreate(rpm_task, "RPM_Task", 1024, NULL, 2, &rpmTaskHandle);

    // Start FreeRTOS kernel
    vTaskStartScheduler();

    panic("RTOS kernel not running!"); 
}