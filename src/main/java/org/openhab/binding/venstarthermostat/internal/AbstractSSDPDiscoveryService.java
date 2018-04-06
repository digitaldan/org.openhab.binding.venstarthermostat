/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.venstarthermostat.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.entity.EntityDeserializer;
import org.apache.http.impl.entity.LaxContentLengthStrategy;
import org.apache.http.impl.io.AbstractSessionInputBuffer;
import org.apache.http.impl.io.HttpResponseParser;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicLineParser;
import org.apache.http.params.BasicHttpParams;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryServiceCallback;
import org.eclipse.smarthome.config.discovery.ExtendedDiscoveryService;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSSDPDiscoveryService extends AbstractDiscoveryService
        implements ExtendedDiscoveryService {

    protected Logger log = LoggerFactory.getLogger(getClass());

    protected DiscoveryServiceCallback discoveryServiceCallback;

    private boolean continueScanning;
    private ScheduledFuture<?> scanFuture = null;
    protected int backgroundScanInterval = 30; // seconds

    public AbstractSSDPDiscoveryService(int timeout) throws IllegalArgumentException {
        super(timeout);
    }

    public AbstractSSDPDiscoveryService(Set<ThingTypeUID> supportedDevices, int timeout) {
        super(supportedDevices, timeout);
    }

    @Override
    protected void startScan() {
        log.info("Starting Interactive Scan");
        doRunRun();
    }

    protected void doRunRun() {
        log.debug("Sending SSDP discover.");
        String scanWords = getScanWords();
        for (int i = 0; i < 5; i++) {
            try {
                Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                while (nets.hasMoreElements()) {
                    NetworkInterface ni = nets.nextElement();

                    MulticastSocket socket = sendDiscoveryBroacast(ni);
                    // log.debug("Waiting for response.");
                    if (socket != null) {
                        scanResposesForKeywords(socket, scanWords);
                    }
                }
            } catch (IOException e) {
                // log.debug("Timeout of request... " + e.getMessage());
            }
        }
    }

    protected abstract String getScanWords();

    protected void runBackgroundScan(final int seconds) {
        log.info("scheduling discovery in {} seconds.", seconds);
        scanFuture = scheduler.schedule(new Runnable() {

            @Override
            public void run() {
                if (getDiscoveryServiceCallback() == null) {
                    log.warn("DiscoveryServiceCallback not set (still starting?), waiting.");
                    runBackgroundScan(2);
                    return;
                }
                doRunRun();
                if (continueScanning) {
                    runBackgroundScan(backgroundScanInterval);
                }
            }
        }, seconds, TimeUnit.SECONDS);

    }

    @Override
    protected synchronized void stopScan() {
        log.info("Stopping Interactive Discovery.");
        removeOlderResults(getTimestampOfLastScan());
        super.stopScan();
        if (!isBackgroundDiscoveryEnabled()) {
        }
    }

    @Override
    protected void startBackgroundDiscovery() {
        log.info("Starting Background Discovery");
        continueScanning = true;
        runBackgroundScan(0);
    }

    @Override
    protected void stopBackgroundDiscovery() {
        log.info("Stopping Background Discovery");
        removeOlderResults(getTimestampOfLastScan());
        continueScanning = false;
        if (scanFuture != null) {
            scanFuture.cancel(true);
        }
    }

    /**
     * Broadcasts a SSDP discovery message into the network to find provided
     * services.
     *
     * @return The Socket the answers will arrive at.
     * @throws UnknownHostException
     * @throws IOException
     * @throws SocketException
     * @throws UnsupportedEncodingException
     */

    private MulticastSocket sendDiscoveryBroacast(NetworkInterface ni)
            throws UnknownHostException, SocketException, UnsupportedEncodingException {
        // NetworkAddressFactory factory = new NetworkAddressFactoryImpl(1900, 1900);
        InetAddress m = InetAddress.getByName("239.255.255.250");
        // factory.getMulticastGroup();

        final int port = 1900;

        log.debug("Considering []", ni.getName());
        try {
            if (!ni.isUp() || !ni.supportsMulticast()) {
                log.debug("skipping interface {}", ni.getName());
                return null;
            }

            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            InetAddress a = null;
            while (addrs.hasMoreElements()) {
                a = addrs.nextElement();
                if (a instanceof Inet4Address) {
                    break;
                } else {
                    a = null;
                }
            }
            if (a == null) {
                log.debug("no ipv4 address on {}", ni.getName());
                return null;
            }

            // for whatever reason, the venstar thermostat responses will not be seen
            // if we bind this socket to a particular address.
            // this seems to be okay on linux systems, but osx apparently prefers ipv6, so this
            // prevents responses from being received unless the ipv4 stack is given preference.
            MulticastSocket socket = new MulticastSocket(new InetSocketAddress(port));
            socket.setSoTimeout(2000);
            socket.setReuseAddress(true);
            // log.debug("Network Interface: " + ni.getName());
            socket.setNetworkInterface(ni);
            socket.joinGroup(m);

            log.trace("Joined UPnP Multicast group on Interface: {}", ni.getName());
            byte[] requestMessage = this.getDiscoveryMessage().getBytes("UTF-8");
            DatagramPacket datagramPacket = new DatagramPacket(requestMessage, requestMessage.length, m, port);
            socket.send(datagramPacket);
            return socket;
        } catch (IOException e) {
            log.debug("got ioexception: {}", e.getMessage());
        }

        return null;
    }

    protected abstract String getDiscoveryMessage();

    /**
     * Scans all messages that arrive on the socket and scans them for the
     * search keywords. The search is not case sensitive.
     *
     * @param socket
     *            The socket where the answers arrive.
     * @param keywords
     *            The keywords to be searched for.
     * @return
     * @throws IOException
     */
    private void scanResposesForKeywords(MulticastSocket socket, String... keywords) throws IOException {
        // In the worst case a SocketTimeoutException raises
        do {
            byte[] rxbuf = new byte[8192];
            DatagramPacket packet = new DatagramPacket(rxbuf, rxbuf.length);
            try {
                // log.debug("Waiting for packet.");
                socket.receive(packet);
            } catch (Exception e) {
                log.debug("Got exception while trying to receive UPnP packets: {}", e.getMessage());
                return;
            }
            log.trace("Got an answer message.");
            final String resultStr = analyzePacket(packet, keywords);
            log.trace("Finished analyzing packet... {}", resultStr);
            if (resultStr != null) {
                HttpResponse response;

                log.trace("loading buffer");
                try {
                    SessionInputBuffer sessionInputBuffer = new AbstractSessionInputBuffer() {
                        {
                            init(new ByteArrayInputStream(resultStr.getBytes()), 10, new BasicHttpParams());
                        }

                        @Override
                        public boolean isDataAvailable(int timeout) throws IOException {
                            throw new RuntimeException("have to override but probably not even called");
                        }
                    };
                    // log.info("got buffer");
                    // sessionInputBuffer.bind(new ByteArrayInputStream(resultStr.getBytes()));
                    // DefaultHttpResponseParser responseParser = new DefaultHttpResponseParser(sessionInputBuffer);
                    log.trace("Parsing answer.");
                    HttpMessageParser responseParser = new HttpResponseParser(sessionInputBuffer,
                            new BasicLineParser(new ProtocolVersion("HTTP", 1, 1)), new DefaultHttpResponseFactory(),
                            new BasicHttpParams());
                    HttpMessage message = responseParser.parse();
                    if (message instanceof BasicHttpEntityEnclosingRequest) {
                        BasicHttpEntityEnclosingRequest request = (BasicHttpEntityEnclosingRequest) message;
                        EntityDeserializer entityDeserializer = new EntityDeserializer(new LaxContentLengthStrategy());
                        HttpEntity entity = entityDeserializer.deserialize(sessionInputBuffer, message);
                        request.setEntity(entity);
                    }
                    response = (HttpResponse) message;
                } catch (Throwable e) {
                    // we don't need to report errors for SEARCH requests.
                    if (!e.getMessage().contains("M-SEARCH")) {
                        log.debug("unable to parse response: {}", e.getMessage());
                    }
                    continue;
                }

                parseResponse(response);
            }
        } while (true);
    }

    protected abstract void parseResponse(HttpResponse response);

    /**
     * Checks whether a packet does contain all given keywords case insensitive.
     * If all keywords are contained the IP address of the packet sender will be
     * returned.
     *
     * @param packet
     *            The data packet to be analyzed.
     * @param keywords
     *            The keywords to be searched for.
     * @return The body of the message if all keywords found, null
     *         otherwise.
     *
     * @throws IOException
     */
    private String analyzePacket(DatagramPacket packet, String... keywords) throws IOException {

        // log.debug("Analyzing answer message.");

        InetAddress addr = packet.getAddress();
        ByteArrayInputStream in = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
        String response = IOUtils.toString(in);
        log.trace("packet: {}", response);

        boolean foundAllKeywords = true;

        for (String keyword : keywords) {
            foundAllKeywords &= response.toUpperCase().contains(keyword.toUpperCase());
        }

        if (foundAllKeywords) {
            log.trace("Found matching answer.");
            return response;
        }

        log.trace("Answer did not match.");
        return null;
    }

    @Override
    public void setDiscoveryServiceCallback(DiscoveryServiceCallback discoveryServiceCallback) {
        // log.warn(discoveryServiceCallback.toString());
        this.discoveryServiceCallback = discoveryServiceCallback;
        // log.debug(discoveryServiceCallback.toString());
    }

    public DiscoveryServiceCallback getDiscoveryServiceCallback() {
        return discoveryServiceCallback;
    }

}
