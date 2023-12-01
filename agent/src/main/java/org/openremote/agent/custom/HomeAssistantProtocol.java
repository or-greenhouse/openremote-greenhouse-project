/*
 * Copyright 2017, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.agent.custom;

import org.openremote.agent.custom.assets.HomeAssistantLightAsset;
import org.openremote.agent.protocol.AbstractProtocol;
import org.openremote.agent.protocol.ProtocolAssetService;
import org.openremote.model.Container;
import org.openremote.model.asset.agent.ConnectionStatus;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.syslog.SyslogCategory;

import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;

/**
 * A custom protocol that is used by the {@link HomeAssistantAgent}; there is a one-to-one mapping between an {@link
 * HomeAssistantAgent} {@link org.openremote.model.asset.Asset} and its' {@link org.openremote.model.asset.agent.Protocol}.
 * This example does nothing useful but is intended to show where protocol classes should be created.
 */
public class HomeAssistantProtocol extends AbstractProtocol<HomeAssistantAgent, HomeAssistantAgentLink> {

    public static final String PROTOCOL_DISPLAY_NAME = "HomeAssistant Client";
    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, HomeAssistantProtocol.class);
    public HomeAssistantEntityProcessor entityProcessor;
    protected HomeAssistantClient client;
    protected HomeAssistantWebSocketClient webSocketClient;
    protected volatile boolean running;

    public HomeAssistantProtocol(HomeAssistantAgent agent) {
        super(agent);
    }

    @Override
    protected void doStart(Container container) throws Exception {
        running = true;

        String url = agent.getHomeAssistantUrl().orElseThrow(() -> {
            String msg = "HomeAssistant URL is not defined so cannot start protocol: " + this;
            LOG.warning(msg);
            return new IllegalArgumentException(msg);
        });

        String accessToken = agent.getAccessToken().orElseThrow(() -> {
            String msg = "Access token is not defined so cannot start protocol: " + this;
            LOG.warning(msg);
            return new IllegalArgumentException(msg);

        });

        client = new HomeAssistantClient(url, accessToken);
        setConnectionStatus(ConnectionStatus.CONNECTING);
        if (client.isConnectionSuccessful()) {
            setConnectionStatus(ConnectionStatus.CONNECTED);
            LOG.info("Connection to HomeAssistant API successful");

            assetService = container.getService(ProtocolAssetService.class);
            executorService = container.getExecutorService();
            webSocketClient = new HomeAssistantWebSocketClient(this);
            entityProcessor = new HomeAssistantEntityProcessor(this, assetService);

            importHomeAssistantEntities();
            startWebSocketClient();
        } else {
            LOG.warning("Connection to HomeAssistant failed");
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
        }

    }

    // Starts the WebSocket client in a separate thread
    private void startWebSocketClient() {
        executorService.submit(() -> {
            webSocketClient.connect();
            while (running) {
                Thread.onSpinWait();
            }
        }, null);
    }

    // Imports all entities from Home Assistant and merges them into the agents asset store
    private void importHomeAssistantEntities() {
        var entities = client.getEntities();
        if (entities.isPresent()) {
            var assets = entityProcessor.convertEntitiesToAssets(entities.get());
            if (assets.isPresent()) {
                for (var asset : assets.get()) {
                    asset.setParentId(agent.getId()); // set the parent to the agent (this)
                    asset.setRealm(agent.getRealm()); // set the realm to the agent (this)
                    assetService.mergeAsset(asset);
                }
            }
        }
    }


    @Override
    protected void doStop(Container container) throws Exception {
        running = false;
    }

    @Override
    protected void doLinkAttribute(String assetId, Attribute<?> attribute, HomeAssistantAgentLink agentLink) throws RuntimeException {
        LOG.info("Linking attribute: " + attribute.getName() + " to asset: " + assetId);
    }

    @Override
    protected void doUnlinkAttribute(String assetId, Attribute<?> attribute, HomeAssistantAgentLink agentLink) {
        LOG.info("Unlinking attribute: " + attribute.getName() + " from asset: " + assetId);
    }

    @Override
    protected void doLinkedAttributeWrite(Attribute<?> attribute, HomeAssistantAgentLink agentLink, AttributeEvent event, Object processedValue) {
        var asset = assetService.findAsset(event.getAssetId());
        if (asset == null) {
            return;
        }
         if (attribute.getValue().equals(processedValue)) {
            updateLinkedAttribute(event.getAttributeState());
            return;
        }

        if (asset instanceof HomeAssistantLightAsset) {
            String value = processedValue.toString();
            if (attribute.getName().equals("state") && value.equals("on") || attribute.getName().equals("brightness")) {

                var setting = "";
                if (attribute.getName().equals("brightness")) {
                    setting = "\"brightness\": " + value;
                }

                client.setEntityState(agentLink.domainId, "turn_on", agentLink.entityId, setting);
            } else if (attribute.getName().equals("state") && value.equals("off")) {
                client.setEntityState(agentLink.domainId, "turn_off", agentLink.entityId, "");
            }
        }

        updateLinkedAttribute(event.getAttributeState());

    }

    public void handleHomeAssistantAssetChange(AttributeEvent event) {
        updateLinkedAttribute(event.getAttributeState());
    }

    @Override
    public String getProtocolName() {
        return PROTOCOL_DISPLAY_NAME;
    }

    @Override
    public String getProtocolInstanceUri() {
        return agent.getHomeAssistantUrl().orElse("");
    }


}
