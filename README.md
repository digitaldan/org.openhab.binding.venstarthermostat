# Venstar Thermostat Binding

The Venstar Thermostat binding supports an interface to WiFi enabled 
ColorTouch and Explorer thermostats manufactured by Venstar[1]. 

## Prerequisites

Venstar WiFi enabled thermostats provide a local API that this binding uses
to communicate with the thermostat. This binding does not require "cloud" 
access and may be used independently of Venstar's Skyport cloud services.

The Local API is not enabled by default, so you will need to set up your 
thermostat by configuring its WiFi connection and enabling the Local API. In
order for the binding to connect, you will need to enable HTTPS support and 
set a username and password. While it is possible to enable the Local API
without HTTPS and authentication, the binding doesn't support it, in an effort
to provide as secure an installation as possible.

When you've set the username and password, make a note of these, as you'll need
to enter them in the thermostat configuration in OpenHAB.

## Installation

__Version 2.1.0__

This version of the binding requires a few bundles to be added to your OpenHAB 2 
installation before it will work. It is easiest to do this before downloading 
and installing the thermostat binding. To do this, enter the following commands
from your OpenHAB 2 console:

bundle:install http://central.maven.org/maven2/org/apache/httpcomponents/httpclient-osgi/4.3.4/httpclient-osgi-4.3.4.jar

bundle:install http://central.maven.org/maven2/org/apache/httpcomponents/httpcore-osgi/4.3.3/httpcore-osgi-4.3.3.jar

Once that's done, you can download and drop the binding jar into your addons 
directory and then restart OpenHAB. You can download the binding from the Downloads link 
to the left, or use this direct link for version 2.1.0:

https://bitbucket.org/hww3/org.openhab.binding.venstarthermostat/downloads/org.openhab.binding.venstarthermostat-2.1.0.jar

__Version 2.2.0 and later__

Newer version of this binding include all of its dependencies built into the binding JAR, which
makes for a simpler installation. Thus, you can download and drop the binding jar into your addons 
directory and then restart OpenHAB. You can download the binding from the Downloads link 
to the left, or use this direct link for version 2.2.0:

https://bitbucket.org/hww3/org.openhab.binding.venstarthermostat/downloads/org.openhab.binding.venstarthermostat-2.2.0.jar



When things start back up, the binding will attempt to discover any thermostats installed on your 
network and add them to the OpenHAB inbox.

You can verify that the binding has loaded properly by looking for its startup
messages in the OpenHAB log file. You can use the "log:tail" command in the 
OpenHAB console to look at the logs.

## Release Notes

2.2.0:

- This version of the binding includes all of the prerequisite libraries built directly
into the binding jar. 

- Less verbose logging by default


2.1.0:

- The binding should now be able to detect thermostats on any interface that 
is running IPv4.

- If a thermostat goes offline, the binding should switch into reconnect mode,
and will use new information reported by the discovery process. If a thermostat
receives a new IP address via DHCP, this should be automatically detected.

- The binding is more careful about its communication with the thermostat, and
should be less likely to send bad information to the thermostat.

[1] http://www.venstar.com/thermostats/colortouch/
