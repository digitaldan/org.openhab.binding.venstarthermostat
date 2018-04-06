/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.venstarthermostat.handler;

import static org.openhab.binding.venstarthermostat.VenstarThermostatBindingConstants.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.UriBuilder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.venstarthermostat.VenstarThermostatBindingConstants;
import org.openhab.binding.venstarthermostat.model.VenstarInfoData;
import org.openhab.binding.venstarthermostat.model.VenstarResponse;
import org.openhab.binding.venstarthermostat.model.VenstarSensor;
import org.openhab.binding.venstarthermostat.model.VenstarSensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * The {@link VenstarThermostatHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author William Welliver - Initial contribution
 */
public class VenstarThermostatHandler extends ConfigStatusThingHandler {

    private Logger log = LoggerFactory.getLogger(VenstarThermostatHandler.class);
    ScheduledFuture<?> refreshJob;

    private String username;
    private String password;
    private URL url;
    private BigDecimal refresh;
    private DefaultHttpClient httpclient;
    private List<VenstarSensor> sensorData = new ArrayList<>();
    private VenstarInfoData infoData = new VenstarInfoData();
    private Future<?> initializeTask;
    private Future<?> updatesTask;
    private boolean shouldRunUpdates = false;

    // Create a trust manager that does not validate certificate chains
    TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
    } };

    // Install the all-trusting trust manager
    final SSLContext sc;

    // Create all-trusting host name verifier
    X509HostnameVerifier allHostsValid = new X509HostnameVerifier() {

        @Override
        public boolean verify(String hostname, SSLSession arg1) {
            return true;
        }

        @Override
        public void verify(String host, SSLSocket ssl) throws IOException {
        }

        @Override
        public void verify(String host, X509Certificate cert) throws SSLException {
        }

        @Override
        public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
        }

    };

    public VenstarThermostatHandler(Thing thing) {
        super(thing);
        try {
            sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("Unable to configure SSL context: {}", e.getMessage());
            throw (new RuntimeException(e));
        }
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        log.warn("getConfigStatus called.");
        Collection<ConfigStatusMessage> status = new ArrayList<>();
        try {
            String username = (String) (getThing().getConfiguration().get(CONFIG_USERNAME));

            if (username == null || username.isEmpty()) {
                log.warn("username is empty");
                status.add(ConfigStatusMessage.Builder.error(CONFIG_USERNAME).withMessageKeySuffix(EMPTY_INVALID)
                        .withArguments(CONFIG_USERNAME).build());
            }

            String password = (String) (getThing().getConfiguration().get(CONFIG_PASSWORD));

            if (password == null || password.isEmpty()) {
                log.warn("password is empty");
                status.add(ConfigStatusMessage.Builder.error(CONFIG_PASSWORD).withMessageKeySuffix(EMPTY_INVALID)
                        .withArguments(CONFIG_PASSWORD).build());
            }

            BigDecimal refresh = (BigDecimal) (getThing().getConfiguration().get(CONFIG_REFRESH));

            if (refresh.intValue() < 10) {
                log.warn("refresh is too small: {}", refresh.intValue());

                status.add(ConfigStatusMessage.Builder.error(CONFIG_REFRESH).withMessageKeySuffix(REFRESH_INVALID)
                        .withArguments(CONFIG_REFRESH).build());
            }
        } catch (Throwable th) {
            log.error("{}", th.getMessage());
            status.add(ConfigStatusMessage.Builder.error("AIEEE").withMessageKeySuffix(th.getMessage()).build());
        }

        log.warn("getConfigStatus returning {}", status);

        return status;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (!(command instanceof DecimalType)) {
            log.warn("Invalid command {} for channel {}", command, channelUID.getId());
            return;
        }

        if (channelUID.getId().equals(CHANNEL_HEATING_SETPOINT)) {
            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
            log.debug("Setting heating setpoint to {}", command.toString());
            setHeatingSetpoint(((DecimalType) command).intValue());
        }

        if (channelUID.getId().equals(CHANNEL_COOLING_SETPOINT)) {
            log.debug("Setting cooling setpoint to {}", command.toString());
            setCoolingSetpoint(((DecimalType) command).intValue());
        }

        if (channelUID.getId().equals(CHANNEL_SYSTEM_MODE)) {
            log.debug("Setting system mode to {}", command.toString());
            setSystemMode(((DecimalType) command).intValue());
        }
    }

    public void updateUrl(String url) {
        Map<String, String> props = editProperties();
        props.put(VenstarThermostatBindingConstants.PROPERTY_URL, url);
        updateProperties(props);
        thingUpdated(getThing());
    }

    @Override
    public void initialize() {
        getConfigurationFromThing();
        scheduleCheckCommunication(1);
    }

    protected boolean getConfigurationFromThing() {
        Configuration config = getThing().getConfiguration();
        Map<String, String> properties = getThing().getProperties();
        username = (String) config.get(CONFIG_USERNAME);
        password = (String) config.get(CONFIG_PASSWORD);
        refresh = (BigDecimal) config.get(CONFIG_REFRESH);

        final String u = properties.get(PROPERTY_URL);

        try {
            url = new URL(u);
        } catch (MalformedURLException e) {
            goOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Invalid device URL: " + e.getMessage());
            return false;
        }

        httpclient = buildHttpClient();

        log.info("Got configuration from thing. Url=" + url);

        return true;
    }

    protected void checkCommunication() {

        HttpResponse response = null;
        try {
            response = getConnection();
            log.debug("got response from venstar: {}", response.getStatusLine());
        } catch (Throwable e) {
            log.warn("communication error:{} ", e.getMessage());
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR,
                    "Failed to connect to URL (" + url.toString() + "): " + e.getMessage());
            return;
        }

        if (response.getStatusLine().getStatusCode() == 401 || response.getStatusLine().getStatusCode() == 403) {
            goOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Invalid Credentials");
            return;
        } else if (response.getStatusLine().getStatusCode() != 200) {
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR,
                    "Unexpected response: " + response.getStatusLine().getStatusCode());
            return;
        }

        log.debug("setting online");
        goOnline();
        return;
    }

    protected void scheduleCheckCommunication(int seconds) {

        log.info("running communication check in {} seconds", seconds);
        Future<?> initializeTask1 = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                checkCommunication();
            }
        }, seconds, TimeUnit.SECONDS);

        // only one initialization task at a time, please.
        Future<?> i = initializeTask;
        initializeTask = initializeTask1;
        if (i != null && !i.isDone()) {
            i.cancel(true);
        }
    }

    protected void goOnline() {
        // we don't need to check communications if we're online already.
        // only one initialization task at a time, please.
        if (initializeTask != null && !initializeTask.isDone()) {
            initializeTask.cancel(true);
        }

        if (updatesTask != null && !updatesTask.isDone()) {
            updatesTask.cancel(true);
        }
        shouldRunUpdates = true;
        updateStatus(ThingStatus.ONLINE);
        startUpdatesTask();
    }

    protected void goOffline(ThingStatusDetail detail, String reason) {
        if (updatesTask != null && !updatesTask.isDone()) {
            updatesTask.cancel(true);
        }

        shouldRunUpdates = false;
        updateStatus(ThingStatus.OFFLINE, detail, reason);
        scheduleCheckCommunication(15);
    }

    @Override
    public void dispose() {
        if (refreshJob != null) {
            refreshJob.cancel(true);
        }
        if (updatesTask != null) {
            updatesTask.cancel(true);
        }
    }

    private void fetchUpdate() {
        try {
            log.debug("running refresh");
            boolean success = updateSensorData();
            if (success) {
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_TEMPERATURE), getTemperature());
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_EXTERNAL_TEMPERATURE), getOutdoorTemperature());
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_HUMIDITY), getHumidity());
            }
        } catch (Exception e) {
            log.debug("Exception occurred during execution: {}", e.getMessage(), e);
        }

        try {
            log.debug("updating info");
            boolean success = updateInfoData();
            if (success) {
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_HEATING_SETPOINT), getHeatingSetpoint());
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_COOLING_SETPOINT), getCoolingSetpoint());
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_SYSTEM_STATE), getSystemState());
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_SYSTEM_MODE), getSystemMode());
            }
        } catch (Exception e) {
            log.debug("Exception occurred during execution: {}", e.getMessage(), e);
        }
    }

    private void startUpdatesTask() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                fetchUpdate();
                if (shouldRunUpdates) {
                    startUpdatesTask();
                }
            }
        };

        updatesTask = scheduler.schedule(runnable, refresh.intValue(), TimeUnit.SECONDS);
    }

    private State getTemperature() {

        for (VenstarSensor sensor : sensorData) {
            String name = sensor.getName();
            if (name.equalsIgnoreCase("Thermostat")) {
                return new DecimalType(sensor.getTemp());
            }
        }

        return DecimalType.ZERO;
    }

    private State getHumidity() {

        for (VenstarSensor sensor : sensorData) {
            String name = sensor.getName();
            if (name.equalsIgnoreCase("Thermostat")) {
                return new DecimalType(sensor.getHum());
            }
        }

        return PercentType.ZERO;
    }

    private State getOutdoorTemperature() {
        for (VenstarSensor sensor : sensorData) {
            String name = sensor.getName();
            if (name.equalsIgnoreCase("Outdoor")) {
                return new DecimalType(sensor.getTemp());
            }
        }
        return DecimalType.ZERO;
    }

    private void setCoolingSetpoint(int cool) {
        int heat = ((DecimalType) getHeatingSetpoint()).intValue();
        int mode = ((DecimalType) getSystemMode()).intValue();
        updateThermostat(heat, cool, mode);
    }

    private void setSystemMode(int mode) {
        int cool = ((DecimalType) getCoolingSetpoint()).intValue();
        int heat = ((DecimalType) getHeatingSetpoint()).intValue();
        updateThermostat(heat, cool, mode);
    }

    private void setHeatingSetpoint(int heat) {
        int cool = ((DecimalType) getCoolingSetpoint()).intValue();
        int mode = ((DecimalType) getSystemMode()).intValue();
        updateThermostat(heat, cool, mode);
    }

    private State getCoolingSetpoint() {
        if (log.isTraceEnabled()) {
            log.trace("CoolingSetpoint: {} -> {}", infoData.getCooltemp(), new DecimalType(infoData.getCooltemp()));
        }
        return new DecimalType(infoData.getCooltemp());
    }

    private State getHeatingSetpoint() {
        if (log.isTraceEnabled()) {
            log.trace("HeatingSetpoint:  {} -> {}", infoData.getHeattemp(), new DecimalType(infoData.getHeattemp()));
        }
        return new DecimalType(infoData.getHeattemp());
    }

    private State getSystemState() {
        int num = Math.round(infoData.getState());
        return new DecimalType(num);
    }

    private State getSystemMode() {
        int num = Math.round(infoData.getMode());
        return new DecimalType(num);
    }

    private void updateThermostat(int heat, int cool, int mode) {
        Map<String, String> params = new HashMap<>();

        log.debug("Updating thermostat {} heat:{} cool:{} mode:{}", getThing().getLabel(), heat, cool, mode);
        if (heat > 0) {
            params.put("heattemp", "" + heat);
        }
        if (cool > 0) {
            params.put("cooltemp", "" + cool);
        }
        params.put("mode", "" + mode);

        try {
            HttpResponse result = postConnection("/control", params);
            if (log.isTraceEnabled()) {
                log.trace("Result from theromstat: {}", result.getStatusLine().toString());
            }
            if (result.getStatusLine().getStatusCode() == 401) {
                goOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Invalid credentials");
                log.info("Failed to update thermostat: invalid credentials");
                return;
            }

            HttpEntity entity = result.getEntity();
            String data = EntityUtils.toString(entity, "UTF-8");
            if (log.isTraceEnabled()) {
                log.trace("Result from theromstat: {}", data);
            }
            ;

            VenstarResponse res = new Gson().fromJson(data, VenstarResponse.class);
            if (res.isSuccess()) {
                log.info("Updated thermostat");
            } else {
                log.info("Failed to update: {}", res.getReason());
                goOffline(ThingStatusDetail.COMMUNICATION_ERROR, "Thermostat update failed: " + res.getReason());
            }

        } catch (IOException e) {
            log.info("Failed to update thermostat: {}", e.getMessage());
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return;
        }

    }

    private boolean updateSensorData() {
        try {
            String sensorData = getData("/query/sensors");
            if (log.isTraceEnabled()) {
                log.trace("got sensordata from thermostat: {}", sensorData);
            }

            VenstarSensorData res = new Gson().fromJson(sensorData, VenstarSensorData.class);

            this.sensorData = res.getSensors();
            return true;
        } catch (Exception e) {

            log.debug("Unable to fetch url '{}': {}", url, e.getMessage());
            return false;
        }
    }

    private boolean updateInfoData() {
        try {

            String infoData = getData("/query/info");
            if (log.isTraceEnabled()) {
                log.trace("got info from thermostat: {}", infoData);
            }
            VenstarInfoData id = new Gson().fromJson(infoData, VenstarInfoData.class);
            if (id != null) {
                this.infoData = id;
            }
            return true;
        } catch (Exception e) {
            log.debug("Unable to fetch url '{}': {}", url, e.getMessage());
            return false;
        }
    }

    String getData(String path) {

        try {
            HttpResponse response = getConnection(path);

            if (response.getStatusLine().getStatusCode() == 401) {
                goOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Invalid credentials");
                return null;
            }

            HttpEntity entity = response.getEntity();
            String sensorData = EntityUtils.toString(entity, "UTF-8");

            return sensorData;
        } catch (IOException e) {
            log.warn("failed to open connection: {}", e.getMessage());
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return null;
        } catch (URISyntaxException use) {
            log.warn("bad uri: {}", use.getMessage());
            goOffline(ThingStatusDetail.CONFIGURATION_ERROR, use.getMessage());
            return null;
        }

    }

    HttpResponse getConnection() throws IOException, URISyntaxException {
        return getConnection(null);
    }

    HttpResponse getConnection(String path) throws IOException, URISyntaxException {

        UriBuilder builder = UriBuilder.fromUri(url.toURI());
        if (path != null) {
            builder.path(path);
        }

        HttpGet httpget = new HttpGet(builder.build());

        HttpResponse response = httpclient.execute(httpget);

        return response;
    }

    HttpResponse postConnection(String path, Map<String, String> params) throws IOException {

        UriBuilder builder = UriBuilder.fromUri(getThing().getProperties().get(PROPERTY_URL));
        if (path != null) {
            builder.path(path);
        }

        HttpPost post = new HttpPost(builder.build());
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();

        for (Entry<String, String> ent : params.entrySet()) {
            if (log.isTraceEnabled()) {
                log.trace("setting {}: {}", ent.getKey(), ent.getValue());
            }
            urlParameters.add(new BasicNameValuePair(ent.getKey(), ent.getValue()));
        }
        post.setEntity(new UrlEncodedFormEntity(urlParameters));

        HttpResponse response = httpclient.execute(post);

        return response;
    }

    DefaultHttpClient buildHttpClient() {
        Credentials creds = new UsernamePasswordCredentials(username, password);
        org.apache.http.client.CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), creds);

        SSLSocketFactory sf = new SSLSocketFactory(sc, allHostsValid);

        DefaultHttpClient httpclient = new DefaultHttpClient();
        ClientConnectionManager manager = httpclient.getConnectionManager();
        manager.getSchemeRegistry().register(new Scheme("https", 443, sf));

        httpclient.setCredentialsProvider(credsProvider);

        return httpclient;
    }

}
