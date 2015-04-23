/**
 * Created by michael on 4/23/15.
 */
package com.example.michael.pulsesensor;

import com.example.michael.dataserverlib.SensorData;

public class PulseSensor extends SensorData {
    public int bpm;
    PulseSensor() {
        bpm = 75;
    }
}

