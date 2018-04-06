/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.venstarthermostat.model;

/**
 * The {@link VenstarInfoData} class defines the vendor info data from a thermostat
 *
 * @author William Welliver - Initial contribution
 */
public class VenstarInfoData {
    double cooltemp;
    double heattemp;

    int state;
    int mode;

    public double getCooltemp() {
        return cooltemp;
    }

    public void setCooltemp(double cooltemp) {
        this.cooltemp = cooltemp;
    }

    public double getHeattemp() {
        return heattemp;
    }

    public void setHeattemp(double heattemp) {
        this.heattemp = heattemp;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

}
