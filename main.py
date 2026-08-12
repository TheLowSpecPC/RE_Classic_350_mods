from machine import Pin, I2C
from pico_i2c_lcd import I2cLcd
import time

# --- I2C and LCD Setup ---
sda = Pin(16, Pin.PULL_UP)
scl = Pin(17, Pin.PULL_UP)
i2c = I2C(0, sda=sda, scl=scl, freq=400000)
lcd = I2cLcd(i2c, 39, 2, 16)

# --- Custom Characters for the Rev Bar (5 thickness levels) ---
# This allows us to make a smooth sweeping bar graph instead of chunky blocks
lcd.custom_char(0, bytearray([0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])) # Empty space
lcd.custom_char(1, bytearray([0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x10])) # 1 line
lcd.custom_char(2, bytearray([0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18])) # 2 lines
lcd.custom_char(3, bytearray([0x1C, 0x1C, 0x1C, 0x1C, 0x1C, 0x1C, 0x1C, 0x1C])) # 3 lines
lcd.custom_char(4, bytearray([0x1E, 0x1E, 0x1E, 0x1E, 0x1E, 0x1E, 0x1E, 0x1E])) # 4 lines
lcd.custom_char(5, bytearray([0x1F, 0x1F, 0x1F, 0x1F, 0x1F, 0x1F, 0x1F, 0x1F])) # Full block

# --- Configuration Constants ---
PULSE_PIN = 14              
DEBOUNCE_US = 6000          
TIMEOUT_US = 1500000        
MAX_RPM = 5500              # RE Classic 350 max RPM for the bar graph

# --- Volatile Shared Variables ---
last_pulse_time = 0
pulse_interval_us = 0
new_pulse_received = False

# --- Interrupt Service Routine (ISR) ---
def rpm_callback(pin):
    global last_pulse_time, pulse_interval_us, new_pulse_received
    current_time = time.ticks_us()
    elapsed = time.ticks_diff(current_time, last_pulse_time)
    
    if elapsed > DEBOUNCE_US:
        pulse_interval_us = elapsed
        last_pulse_time = current_time
        new_pulse_received = True

# --- Hardware Pin Setup ---
sensor_pin = Pin(PULSE_PIN, Pin.IN, Pin.PULL_UP)
sensor_pin.irq(trigger=Pin.IRQ_FALLING, handler=rpm_callback)

# --- LCD Drawing Function ---
def update_dashboard(rpm):
    # 1. Top Row: Digital Readout
    # :>4 pads the number with spaces so it always takes up 4 characters 
    # (e.g., "   0", " 850", "3200"). This overwrites old digits without clearing the screen!
    lcd.move_to(0, 0)
    lcd.putstr(f"RE 350 |{rpm:>4} RPM")
    
    # 2. Bottom Row: Sweeping Rev Bar
    display_rpm = min(rpm, MAX_RPM) # Cap at redline so we don't overflow the screen
    
    # Calculate how many of the 80 pixel segments should be filled (16 chars * 5 segments)
    total_segments = int((display_rpm / MAX_RPM) * 80)
    full_blocks = total_segments // 5
    remainder = total_segments % 5
    
    bar_string = ""
    for i in range(16):
        if i < full_blocks:
            bar_string += chr(5)           # Draw a full block
        elif i == full_blocks:
            bar_string += chr(remainder)   # Draw the partial block (1 to 4 lines)
        else:
            bar_string += chr(0)           # Draw empty space
            
    lcd.move_to(0, 1)
    lcd.putstr(bar_string)

# --- Boot Sequence ---
print("System Initialized.")
lcd.clear()
lcd.move_to(0, 0)
lcd.putstr("Tachometer V1.0")
lcd.move_to(0, 1)
lcd.putstr("Initializing...")
time.sleep(2)
lcd.clear()

# Draw the initial empty dashboard
update_dashboard(0)

# --- Main Program Loop ---
current_rpm = 0
smoothed_rpm = 0
ALPHA = 0.3  
last_lcd_update = time.ticks_ms()

while True:
    now = time.ticks_us()
    time_since_last = time.ticks_diff(now, last_pulse_time)
    
    # 1. Check if engine stopped
    if time_since_last > TIMEOUT_US and last_pulse_time != 0:
        current_rpm = 0
        smoothed_rpm = 0
        last_pulse_time = 0
        update_dashboard(0)
        
    # 2. Process new pulse data
    elif new_pulse_received:
        new_pulse_received = False
        
        if pulse_interval_us > 0:
            current_rpm = int(60000000 / pulse_interval_us)
            smoothed_rpm = int((ALPHA * current_rpm) + ((1 - ALPHA) * smoothed_rpm))
            
            # Limit LCD updates to ~10 times a second to prevent I2C lag
            # This keeps the interrupt running fast while the screen updates smoothly
            if time.ticks_diff(time.ticks_ms(), last_lcd_update) > 100:
                update_dashboard(smoothed_rpm)
                last_lcd_update = time.ticks_ms()
    
    time.sleep_ms(20)