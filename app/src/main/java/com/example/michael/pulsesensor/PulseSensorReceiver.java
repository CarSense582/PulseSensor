/**
 * Created by michael on 4/23/15.
 */
package com.example.michael.pulsesensor;

import android.content.Context;
import com.example.michael.dataserverlib.ContentManagerReceiver;

public class PulseSensorReceiver extends ContentManagerReceiver<PulseSensor> {
    @Override
    public PulseSensor getSensor() {
        return new PulseSensor();
    }
    @Override
    public String getServiceId(Context context) {
        return context.getResources().getString(R.string.service_id);
    }
}