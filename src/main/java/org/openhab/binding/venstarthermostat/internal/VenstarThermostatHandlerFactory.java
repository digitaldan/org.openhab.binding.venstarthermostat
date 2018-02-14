/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.venstarthermostat.internal;

import static org.openhab.binding.venstarthermostat.VenstarThermostatBindingConstants.THING_TYPE_COLOR_TOUCH;

import java.util.Collections;
import java.util.Set;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.venstarthermostat.handler.VenstarThermostatHandler;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link VenstarThermostatHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author William Welliver - Initial contribution
 */

@Component(service = ThingHandlerFactory.class, immediate = true, configurationPid = "binding.venstarthermostat")
public class VenstarThermostatHandlerFactory extends BaseThingHandlerFactory {

    private final static Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections.singleton(THING_TYPE_COLOR_TOUCH);

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {

        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.equals(THING_TYPE_COLOR_TOUCH)) {
            return new VenstarThermostatHandler(thing);
        }

        return null;
    }
}
