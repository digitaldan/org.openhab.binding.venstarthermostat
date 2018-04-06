/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.venstarthermostat.internal;

import java.util.Set;

import org.apache.http.HttpResponse;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.venstarthermostat.VenstarThermostatBindingConstants;
import org.openhab.binding.venstarthermostat.handler.VenstarThermostatHandler;
import org.osgi.service.component.annotations.Component;

/**
 *
 * @author William Welliver - Initial contribution
 *
 */
@Component(service = DiscoveryService.class, immediate = true, configurationPid = "binding.venstarthermostat")
public class VenstarThermostatDiscoveryService extends AbstractSSDPDiscoveryService {

    private static final String COLOR_TOUCH_DISCOVERY_MESSAGE = "M-SEARCH * HTTP/1.1\r\n"
            + "Host: 239.255.255.250:1900\r\n" + "Man: ssdp:discover\r\n" + "ST: colortouch:ecp\r\n" + "\r\n";

    protected final static Set<ThingTypeUID> supportedDevices = VenstarThermostatBindingConstants.SUPPORTED_THING_TYPES;

    private String COLOR_TOUCH_SERVICE_TYPE = "colortouch:ecp";
    private String SCAN_WORDS = "colortouch:ecp";

    public VenstarThermostatDiscoveryService() {
        super(supportedDevices, 30);
    }

    @Override
    protected String getScanWords() {
        return SCAN_WORDS;
    }

    @Override
    protected String getDiscoveryMessage() {
        return COLOR_TOUCH_DISCOVERY_MESSAGE;
    }

    @Override
    protected void parseResponse(HttpResponse response) {
        DiscoveryResult result;

        String label = "";
        String st = "";
        String url = "";
        String uuid = "";

        log.debug("extracting data from: {}", response);
        org.apache.http.Header h = response.getFirstHeader("ST");
        if (h != null) {
            st = h.getValue();
        }

        if (!st.equalsIgnoreCase("colortouch:ecp")) {
            return;
        }

        h = response.getFirstHeader("Location");
        if (h != null) {
            url = h.getValue();
        }

        h = response.getFirstHeader("USN");
        if (h != null) {
            uuid = h.getValue();
            int s, f;

            s = uuid.indexOf(":name:");
            if (s <= 0) {
                return;
            }
            f = uuid.indexOf(":type:");
            label = uuid.substring(s + 6, f);

            uuid = uuid.substring("colortouchecp".length() + 1, s).toLowerCase();
        }

        ThingUID thingUid = new ThingUID(VenstarThermostatBindingConstants.THING_TYPE_COLOR_TOUCH,
                uuid.replace(":", ""));

        log.debug("Got discovered device.");
        if (getDiscoveryServiceCallback() != null) {
            log.debug("Looking to see if this thing exists already.");
            Thing thing = getDiscoveryServiceCallback().getExistingThing(thingUid);
            if (thing != null) {
                this.backgroundScanInterval = 300;
                log.debug("Already have thing with ID=<{}>", thingUid);
                String thingUrl = thing.getProperties().get(VenstarThermostatBindingConstants.PROPERTY_URL);
                log.debug("ThingURL=<{}>, discoveredUrl=<{}>", thingUrl, url);
                this.backgroundScanInterval = 300;
                if (thingUrl == null || !thingUrl.equals(url)) {
                    ((VenstarThermostatHandler) thing.getHandler()).updateUrl(url);
                    thing.getHandler().thingUpdated(thing);
                    log.info("Updated url for existing Thermostat => {}", url);
                }
                return;
            } else {
                log.debug("Nope. This should trigger a new inbox entry.");
            }
        } else {
            log.warn("DiscoveryServiceCallback not set. This shouldn't happen!");
        }

        result = DiscoveryResultBuilder.create(thingUid).withLabel(label).withRepresentationProperty(uuid)
                .withProperty(VenstarThermostatBindingConstants.PROPERTY_UUID, uuid)
                .withProperty(VenstarThermostatBindingConstants.PROPERTY_URL, url).build();
        log.info("New venstar thermostat discovered with ID=<{}>", uuid.replace(":", ""));
        this.thingDiscovered(result);
    }

}
