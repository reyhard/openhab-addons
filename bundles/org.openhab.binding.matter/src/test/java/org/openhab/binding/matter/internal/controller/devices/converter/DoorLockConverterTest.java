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
    void testCreateChannels() {
        ChannelGroupUID channelGroupUID = new ChannelGroupUID("matter:node:test:12345:1");
        Map<Channel, @Nullable StateDescription> channels = converter.createChannels(channelGroupUID);
        assertEquals(2, channels.size());

        // Verify both channels are created
        boolean foundLockState = false;
        boolean foundUnlock = false;
        for (Channel channel : channels.keySet()) {
            String channelId = channel.getUID().getIdWithoutGroup();
            if ("doorlock-lockstate".equals(channelId)) {
                foundLockState = true;
                assertEquals("Switch", channel.getAcceptedItemType());
            } else if ("doorlock-unlock".equals(channelId)) {
                foundUnlock = true;
                assertEquals("Switch", channel.getAcceptedItemType());
            }
        }
        assertEquals(true, foundLockState, "doorlock-lockstate channel should be created");
        assertEquals(true, foundUnlock, "doorlock-unlock channel should be created");
    }

    @Test
    void testHandleCommandLock() {
        ChannelUID channelUID = new ChannelUID("matter:node:test:12345:1#doorlock-lockstate");
        converter.handleCommand(channelUID, OnOffType.ON);
        verify(mockHandler, times(1)).sendClusterCommand(eq(1), eq(DoorLockCluster.CLUSTER_NAME),
                eq(DoorLockCluster.lockDoor(null)));
    }

    @Test
    void testHandleCommandUnlock() {
        ChannelUID channelUID = new ChannelUID("matter:node:test:12345:1#doorlock-lockstate");
        converter.handleCommand(channelUID, OnOffType.OFF);
        verify(mockHandler, times(1)).sendClusterCommand(eq(1), eq(DoorLockCluster.CLUSTER_NAME),
                eq(DoorLockCluster.unboltDoor(null)));
    }

    @Test
    void testHandleCommandMomentaryUnlock() {
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
        AttributeChangedMessage message = new AttributeChangedMessage();
        message.path = new Path();
        message.path.attributeName = "lockState";
        message.value = DoorLockCluster.LockStateEnum.LOCKED;
        converter.onEvent(message);
        verify(mockHandler, times(1)).updateState(eq(1), eq("doorlock-lockstate"), eq(OnOffType.ON));
    }

    @Test
    void testInitState() {
        mockCluster.lockState = DoorLockCluster.LockStateEnum.LOCKED;
        converter.initState();
        verify(mockHandler, times(1)).updateState(eq(1), eq("doorlock-lockstate"), eq(OnOffType.ON));
    }
}
