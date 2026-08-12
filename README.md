## Royal Enfield Classic 350 mods
This repo is dedicated to building mods for Royal Enfield Classic 350 2016 model. 
The mods will include electrical, electronics and embedded systems components.

## Mods
* ## RingLight
  Attached turn signals to ring light using 2 10A diodes.
* ## Tachometer
  Made a tachometer using Raspberry Pi Pico as the brains of the whole operation.
  
  The positive lead of pickup coil of the bike is connected in series with 6.5k Ohm resistance which is connected to a diode then connected to the base of a BJT.
  
  The collector of BJT is connected to GPIO 14 and emitter is connected to common ground shared by both Pico and the bike.
  
  The Pickup coil produces an AC output voltage whoes magnitude depends upon the RPM. But we use the time period of one complete wave to find the RPM since 1 wave equals 1 rotation.

  We can find the time period by subtracting the time at which the current wave started by the time at which the previous wave started.

  Then we can divide 60 by Time Period in order to get our desired RPM.

  We can find the start of the wave by attaching the rectified signal from Pickup coil to the base of a BJT. That way the GPIO pin will trigger once the signal reaches 0.7V.

  <div id="header" align="center">
  <img src="https://github.com/TheLowSpecPC/RE_Classic_350_mods/blob/main/Assets/Tachomete.png"/>
  </div>

## TODO
* Hazard Lights
* Quick Shifter
