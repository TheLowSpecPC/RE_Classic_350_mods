#include <stdio.h>
#include "pico/stdlib.h"
#include "FreeRTOS.h"
#include "task.h"
#include "lvgl.h"
#include "st7735.h"

TaskHandle_t testHandle;
void testTask(void *p){
    while(1){
        printf("Hello World!\n");
        vTaskDelay(1000);
    }
}


int main()
{
    stdio_init_all();
    
    // Initialize the LCD
    LCD_initDisplay(INITR_BLACKTAB);
    LCD_setRotation(1);

    // Create threads
    xTaskCreate(testTask, "testTask", 256, NULL, 1, &testHandle);

    // Initialize LVGL (creates LVGL thread)
    lv_init();

    // Start FreeRTOS kernel
    vTaskStartScheduler();

    panic("RTOS kernel not running!"); // we shouldn't get here
}
