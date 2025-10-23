/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.matter.internal.controller.devices.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.openhab.binding.matter.internal.client.dto.cluster.gen.DoorLockCluster;
import org.openhab.binding.matter.internal.client.dto.ws.AttributeChangedMessage;
import org.openhab.binding.matter.internal.client.dto.ws.Path;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.types.StateDescription;

/**
 * Test class for DoorLockConverter
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
class DoorLockConverterTest extends BaseMatterConverterTest {

    @Mock
    @NonNullByDefault({})
    private DoorLockCluster mockCluster;

    @NonNullByDefault({})
    private DoorLockConverter converter;

    @Override
    @BeforeEach
    void setUp() {
        super.setUp();
        converter = new DoorLockConverter(mockCluster, mockHandler, 1, "TestLabel");
    }

    @Test
    void testCreateStandardChannels() {
        // Initialize featureMap without unbolting feature
        mockCluster.featureMap = new DoorLockCluster.FeatureMap(false, false, false, false, false, false, false, false,
                false, false, false, false, false);

        ChannelGroupUID channelGroupUID = new ChannelGroupUID("matter:node:test:12345:1");
        Map<Channel, @Nullable StateDescription> channels = converter.createChannels(channelGroupUID);
        assertEquals(2, channels.size());

        // Verify standard channels are created (without unlock channel)
        boolean foundLockState = false;
        boolean foundOperatingMode = false;
        for (Channel channel : channels.keySet()) {
            String channelId = channel.getUID().getIdWithoutGroup();
            if ("doorlock-lockstate".equals(channelId)) {
                foundLockState = true;
                assertEquals("Switch", channel.getAcceptedItemType());
            } else if ("doorlock-operatingmode".equals(channelId)) {
                foundOperatingMode = true;
                assertEquals("Number", channel.getAcceptedItemType());
            }
        }
        assertEquals(true, foundLockState, "doorlock-lockstate channel should be created");
        assertEquals(true, foundOperatingMode, "doorlock-operatingmode channel should be created");
    }

    @Test
    void testCreateChannelsWithUnbolting() {
        // Initialize featureMap with unbolting feature enabled (11th parameter)
        mockCluster.featureMap = new DoorLockCluster.FeatureMap(false, false, false, false, false, false, false, false,
                false, false, true, false, false);

        ChannelGroupUID channelGroupUID = new ChannelGroupUID("matter:node:test:12345:1");
        Map<Channel, @Nullable StateDescription> channels = converter.createChannels(channelGroupUID);
        assertEquals(3, channels.size());

        // Verify all channels are created including unlock channel
        boolean foundLockState = false;
        boolean foundUnlock = false;
        boolean foundOperatingMode = false;
        for (Channel channel : channels.keySet()) {
            String channelId = channel.getUID().getIdWithoutGroup();
            if ("doorlock-lockstate".equals(channelId)) {
                foundLockState = true;
                assertEquals("Switch", channel.getAcceptedItemType());
            } else if ("doorlock-unlock".equals(channelId)) {
                foundUnlock = true;
                assertEquals("Switch", channel.getAcceptedItemType());
            } else if ("doorlock-operatingmode".equals(channelId)) {
                foundOperatingMode = true;
                assertEquals("Number", channel.getAcceptedItemType());
            }
        }
        assertEquals(true, foundLockState, "doorlock-lockstate channel should be created");
        assertEquals(true, foundUnlock, "doorlock-unlock channel should be created");
        assertEquals(true, foundOperatingMode, "doorlock-operatingmode channel should be created");
    }

    @Test
    void testHandleCommandLock() {
        mockCluster.featureMap = new DoorLockCluster.FeatureMap(false, false, false, false, false, false, false, false,
                false, false, false, false, false);

        ChannelUID channelUID = new ChannelUID("matter:node:test:12345:1#doorlock-lockstate");
        converter.handleCommand(channelUID, OnOffType.ON);
        verify(mockHandler, times(1)).sendClusterCommand(eq(1), eq(DoorLockCluster.CLUSTER_NAME),
                eq(DoorLockCluster.lockDoor(null)));
    }

    @Test
    void testHandleCommandUnlock() {
        mockCluster.featureMap = new DoorLockCluster.FeatureMap(false, false, false, false, false, false, false, false,
                false, false, false, false, false);

        ChannelUID channelUID = new ChannelUID("matter:node:test:12345:1#doorlock-lockstate");
        converter.handleCommand(channelUID, OnOffType.OFF);
        verify(mockHandler, times(1)).sendClusterCommand(eq(1), eq(DoorLockCluster.CLUSTER_NAME),
                eq(DoorLockCluster.unboltDoor(null)));
    }

    @Test
    void testHandleCommandMomentaryUnlock() {
        mockCluster.featureMap = new DoorLockCluster.FeatureMap(false, false, false, false, false, false, false, false,
                false, false, true, false, false);

        ChannelUID channelUID = new ChannelUID("matter:node:test:12345:1#doorlock-unlock");
        converter.handleCommand(channelUID, OnOffType.ON);

        // Verify unlockDoor command is sent
        verify(mockHandler, times(1)).sendClusterCommand(eq(1), eq(DoorLockCluster.CLUSTER_NAME),
                eq(DoorLockCluster.unlockDoor(null)));

        // Verify momentary behavior - state is immediately set back to OFF
        verify(mockHandler, times(1)).updateState(eq(1), eq("doorlock-unlock"), eq(OnOffType.OFF));
    }

    @Test
    void testOnEventWithLockState() {
        mockCluster.featureMap = new DoorLockCluster.FeatureMap(false, false, false, false, false, false, false, false,
                false, false, false, false, false);

        AttributeChangedMessage message = new AttributeChangedMessage();
        message.path = new Path();
        message.path.attributeName = "lockState";
        message.value = DoorLockCluster.LockStateEnum.LOCKED;
        converter.onEvent(message);
        verify(mockHandler, times(1)).updateState(eq(1), eq("doorlock-lockstate"), eq(OnOffType.ON));
    }

    @Test
    void testHandleCommandOperatingMode() {
        mockCluster.featureMap = new DoorLockCluster.FeatureMap(false, false, false, false, false, false, false, false,
                false, false, false, false, false);

        ChannelUID channelUID = new ChannelUID("matter:node:test:12345:1#doorlock-operatingmode");
        converter.handleCommand(channelUID, new DecimalType(2));
        verify(mockHandler, times(1)).writeAttribute(eq(1), eq(DoorLockCluster.CLUSTER_NAME), eq("operatingMode"),
                eq("2"));
    }

    @Test
    void testOnEventWithOperatingMode() {
        mockCluster.featureMap = new DoorLockCluster.FeatureMap(false, false, false, false, false, false, false, false,
                false, false, false, false, false);

        AttributeChangedMessage message = new AttributeChangedMessage();
        message.path = new Path();
        message.path.attributeName = "operatingMode";
        message.value = DoorLockCluster.OperatingModeEnum.PRIVACY;
        converter.onEvent(message);
        verify(mockHandler, times(1)).updateState(eq(1), eq("doorlock-operatingmode"), eq(new DecimalType(2)));
    }

    @Test
    void testInitState() {
        mockCluster.featureMap = new DoorLockCluster.FeatureMap(false, false, false, false, false, false, false, false,
                false, false, false, false, false);
        mockCluster.lockState = DoorLockCluster.LockStateEnum.LOCKED;
        mockCluster.operatingMode = DoorLockCluster.OperatingModeEnum.NORMAL;
        converter.initState();
        verify(mockHandler, times(1)).updateState(eq(1), eq("doorlock-lockstate"), eq(OnOffType.ON));
        verify(mockHandler, times(1)).updateState(eq(1), eq("doorlock-operatingmode"), eq(new DecimalType(0)));
    }
}
