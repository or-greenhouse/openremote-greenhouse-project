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

import org.openremote.agent.custom.assets.HomeAssistantBaseAsset;
import org.openremote.agent.custom.assets.HomeAssistantLightAsset;
import org.openremote.agent.custom.commands.EntityStateCommandFactory;
import org.openremote.agent.protocol.AbstractProtocol;
import org.openremote.agent.protocol.ProtocolAssetService;
import org.openremote.container.util.UniqueIdentifierGenerator;
import org.openremote.model.Container;
import org.openremote.model.asset.AssetTreeNode;
import org.openremote.model.asset.agent.ConnectionStatus;
import org.openremote.model.asset.impl.GroupAsset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.protocol.ProtocolAssetDiscovery;
import org.openremote.model.syslog.SyslogCategory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;

/**
 * A custom protocol that is used by the {@link HomeAssistantAgent}; there is a one-to-one mapping between an {@link
 * HomeAssistantAgent} {@link org.openremote.model.asset.Asset} and its' {@link org.openremote.model.asset.agent.Protocol}.
 * This example does nothing useful but is intended to show where protocol classes should be created.
 */
public class HomeAssistantProtocol extends AbstractProtocol<HomeAssistantAgent, HomeAssistantAgentLink> implements ProtocolAssetDiscovery {

    public static final String PROTOCOL_DISPLAY_NAME = "HomeAssistant Client";
    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, HomeAssistantProtocol.class);
    public HomeAssistantEntityProcessor entityProcessor;
    protected HomeAssistantHttpClient client;
    protected HomeAssistantWebSocketClient webSocketClient;
    protected volatile boolean running;

    public HomeAssistantProtocol(HomeAssistantAgent agent) {
        super(agent);
    }

    @Override
    protected void doStart(Container container) {
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

        client = new HomeAssistantHttpClient(url, accessToken);
        if (client.isConnectionSuccessful()) {
            setConnectionStatus(ConnectionStatus.CONNECTED);
            LOG.info("Connection to HomeAssistant API successful");

            assetService = container.getService(ProtocolAssetService.class);
            executorService = container.getExecutorService();
            webSocketClient = createWebSocketClient();
            entityProcessor = new HomeAssistantEntityProcessor(this, assetService);

            importAssets();
        } else {
            LOG.warning("Connection to HomeAssistant failed");
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
        }

    }

    // Imports all entities from Home Assistant and merges them into the agents asset store
    // Uses the assetDiscoveryConsumer to pass the assets back to the agent
    private void importAssets() {
        var assetConsumer = new Consumer<AssetTreeNode[]>() {
            @Override
            public void accept(AssetTreeNode[] assetTreeNodes) {
                for (var assetTreeNode : assetTreeNodes) {
                    assetService.mergeAsset(assetTreeNode.getAsset());
                }
            }
        };

        startAssetDiscovery(assetConsumer);
    }


    @Override
    protected void doStop(Container container) {
        running = false;
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
    }

    @Override
    protected void doLinkAttribute(String assetId, Attribute<?> attribute, HomeAssistantAgentLink agentLink) throws RuntimeException {
    }

    @Override
    protected void doUnlinkAttribute(String assetId, Attribute<?> attribute, HomeAssistantAgentLink agentLink) {
    }

    @Override
    protected void doLinkedAttributeWrite(Attribute<?> attribute, HomeAssistantAgentLink agentLink, AttributeEvent event, Object processedValue) {
        var asset = assetService.findAsset(event.getAssetId());
        if (asset == null) {
            return; // asset not found
        }
        if (attribute.getValue().equals(processedValue)) {
            updateLinkedAttribute(event.getAttributeState());
            return; // no change
        }

        var command = EntityStateCommandFactory.createEntityStateCommand(asset, attribute, processedValue.toString());
        if (command.isEmpty())
            return;

        client.setEntityState(agentLink.domainId, command.get());
        updateLinkedAttribute(event.getAttributeState());
    }

    // Called when an attribute is written to due to external changes made by Home Assistant
    public void handleExternalAttributeChange(AttributeEvent event) {
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


    @Override
    public Future<Void> startAssetDiscovery(Consumer<AssetTreeNode[]> assetConsumer) {
        var entities = client.getEntities();
        if (entities.isPresent()) {
            var assets = entityProcessor.convertEntitiesToAssets(entities.get());
            if (assets.isPresent()) {

                //TODO: Remove assets that are no longer present in Home Assistant
                //Pseudo: assetService.removeAssetsNotInList(assets.get());

                // Key: Asset type name, Value: Parent (group asset) id
                HashMap<String, String> assetTypeGroupIds = new HashMap<String, String>();

                for (var asset : assets.get()) {
                    String entityType = HomeAssistantEntityProcessor.getEntityTypeFromEntityId(asset.getEntityId());
                    if (!assetTypeGroupIds.containsKey(entityType)) {
                        Map<String, Object> nameMap = new HashMap<>();
                        nameMap.put("friendly_name", entityType);
                        HomeAssistantBaseAsset groupAsset = entityProcessor.initiateAssetClass(nameMap, entityType, UniqueIdentifierGenerator.generateId());
                        // GroupAsset groupAsset = new GroupAsset(entityType, HomeAssistantBaseAsset.class);

                        groupAsset.setId(UniqueIdentifierGenerator.generateId());
                        groupAsset.setParentId(agent.getId());
                        groupAsset.setRealm(agent.getRealm());

                        assetTypeGroupIds.put(entityType, groupAsset.getId());
                        AssetTreeNode groupAssetNode = new AssetTreeNode(groupAsset);

                        assetConsumer.accept(new AssetTreeNode[]{groupAssetNode});
                    }
                    asset.setParentId(assetTypeGroupIds.get(entityType));
                    asset.setRealm(agent.getRealm());
                    AssetTreeNode node = new AssetTreeNode(asset);
                    assetConsumer.accept(new AssetTreeNode[]{node});
                }
            }
        }
        return null;
    }



    private HomeAssistantWebSocketClient createWebSocketClient()
    {
        if (getAgent().getHomeAssistantUrl().isEmpty()) {
            throw new RuntimeException("Home Assistant URL is not defined so cannot create websocket client for protocol: " + this);
        }
        var wsUrlString = (getAgent().getHomeAssistantUrl().get() + "/api/websocket").replace("http", "ws");
        var wsUrl = URI.create(wsUrlString);

        return new HomeAssistantWebSocketClient(this, wsUrl);
    }
}
