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

import static org.openhab.binding.matter.internal.MatterBindingConstants.CHANNEL_DOORLOCK_OPERATING_MODE;
import static org.openhab.binding.matter.internal.MatterBindingConstants.CHANNEL_DOORLOCK_STATE;
import static org.openhab.binding.matter.internal.MatterBindingConstants.CHANNEL_DOORLOCK_UNLOCK;
import static org.openhab.binding.matter.internal.MatterBindingConstants.CHANNEL_ID_DOORLOCK_OPERATING_MODE;
import static org.openhab.binding.matter.internal.MatterBindingConstants.CHANNEL_ID_DOORLOCK_STATE;
import static org.openhab.binding.matter.internal.MatterBindingConstants.CHANNEL_ID_DOORLOCK_UNLOCK;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.matter.internal.client.dto.cluster.ClusterCommand;
import org.openhab.binding.matter.internal.client.dto.cluster.gen.DoorLockCluster;
import org.openhab.binding.matter.internal.client.dto.ws.AttributeChangedMessage;
import org.openhab.binding.matter.internal.handler.MatterBaseThingHandler;
import org.openhab.core.library.CoreItemFactory;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.StateDescription;

/**
 * A converter for translating {@link DoorLockCluster} events and attributes to openHAB channels and back again.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class DoorLockConverter extends GenericConverter<DoorLockCluster> {

    public DoorLockConverter(DoorLockCluster cluster, MatterBaseThingHandler handler, int endpointNumber,
            String labelPrefix) {
        super(cluster, handler, endpointNumber, labelPrefix);
    }

    @Override
    public Map<Channel, @Nullable StateDescription> createChannels(ChannelGroupUID channelGroupUID) {
        Map<Channel, @Nullable StateDescription> channels = new LinkedHashMap<>();

        // Lock state channel - reflects actual lock state
        Channel lockStateChannel = ChannelBuilder
                .create(new ChannelUID(channelGroupUID, CHANNEL_ID_DOORLOCK_STATE), CoreItemFactory.SWITCH)
                .withType(CHANNEL_DOORLOCK_STATE).build();
        channels.put(lockStateChannel, null);

        // Unlock channel - momentary command-only channel
        // Only create this channel if the unbolting feature is supported
        if (initializingCluster.featureMap.unbolting) {
            Channel unlockChannel = ChannelBuilder
                    .create(new ChannelUID(channelGroupUID, CHANNEL_ID_DOORLOCK_UNLOCK), CoreItemFactory.SWITCH)
                    .withType(CHANNEL_DOORLOCK_UNLOCK).build();
            channels.put(unlockChannel, null);
        }

        // Operating mode channel
        Channel operatingModeChannel = ChannelBuilder
                .create(new ChannelUID(channelGroupUID, CHANNEL_ID_DOORLOCK_OPERATING_MODE), CoreItemFactory.NUMBER)
                .withType(CHANNEL_DOORLOCK_OPERATING_MODE).build();
        channels.put(operatingModeChannel, null);

        return channels;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channelId = channelUID.getIdWithoutGroup();

        if (command instanceof OnOffType onOffType) {
            if (CHANNEL_ID_DOORLOCK_STATE.equals(channelId)) {
                // Lock state channel: ON = lock, OFF = unbolt (unlock without pulling latch)
                ClusterCommand doorLockCommand = onOffType == OnOffType.ON ? DoorLockCluster.lockDoor(null)
                        : DoorLockCluster.unboltDoor(null);
                handler.sendClusterCommand(endpointNumber, DoorLockCluster.CLUSTER_NAME, doorLockCommand);
            } else if (CHANNEL_ID_DOORLOCK_UNLOCK.equals(channelId) && onOffType == OnOffType.ON) {
                // Unlock channel: momentary action - only responds to ON commands
                ClusterCommand doorLockCommand = DoorLockCluster.unlockDoor(null);
                handler.sendClusterCommand(endpointNumber, DoorLockCluster.CLUSTER_NAME, doorLockCommand);

                // Immediately set back to OFF for momentary behavior
                updateState(CHANNEL_ID_DOORLOCK_UNLOCK, OnOffType.OFF);
            }
        } else if (command instanceof DecimalType decimalType && CHANNEL_ID_DOORLOCK_OPERATING_MODE.equals(channelId)) {
            // Operating mode channel: write the operating mode attribute
            handler.writeAttribute(endpointNumber, DoorLockCluster.CLUSTER_NAME, "operatingMode",
                    String.valueOf(decimalType.intValue()));
        }
        super.handleCommand(channelUID, command);
    }

    @Override
    public void onEvent(AttributeChangedMessage message) {
        switch (message.path.attributeName) {
            case "lockState":
                if (message.value instanceof DoorLockCluster.LockStateEnum lockState) {
                    updateState(CHANNEL_ID_DOORLOCK_STATE,
                            lockState == DoorLockCluster.LockStateEnum.LOCKED ? OnOffType.ON : OnOffType.OFF);
                }
                break;
            case "operatingMode":
                if (message.value instanceof DoorLockCluster.OperatingModeEnum operatingMode) {
                    updateState(CHANNEL_ID_DOORLOCK_OPERATING_MODE, new DecimalType(operatingMode.getValue()));
                }
                break;
            default:
                break;
        }
        super.onEvent(message);
    }

    @Override
    public void initState() {
        updateState(CHANNEL_ID_DOORLOCK_STATE,
                initializingCluster.lockState == DoorLockCluster.LockStateEnum.LOCKED ? OnOffType.ON : OnOffType.OFF);
        if (initializingCluster.operatingMode != null) {
            updateState(CHANNEL_ID_DOORLOCK_OPERATING_MODE,
                    new DecimalType(initializingCluster.operatingMode.getValue()));
        }
    }
}
