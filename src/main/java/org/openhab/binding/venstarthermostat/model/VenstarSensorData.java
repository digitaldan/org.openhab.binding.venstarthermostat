package org.openhab.binding.venstarthermostat.model;

import java.util.List;

public class VenstarSensorData {
    List<VenstarSensor> sensors;

    public List<VenstarSensor> getSensors() {
        return sensors;
    }

    public void setSensors(List<VenstarSensor> sensors) {
        this.sensors = sensors;
    }

}
