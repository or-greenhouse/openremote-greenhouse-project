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
import org.openremote.agent.custom.entities.HomeAssistantBaseEntity;
import org.openremote.agent.protocol.AbstractProtocol;
import org.openremote.agent.protocol.ProtocolAssetService;
import org.openremote.model.Container;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetTreeNode;
import org.openremote.model.asset.agent.ConnectionStatus;
import org.openremote.model.asset.agent.DefaultAgentLink;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.protocol.ProtocolAssetDiscovery;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.syslog.SyslogCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;

/**
 * A custom protocol that is used by the {@link HomeAssistantAgent}; there is a one-to-one mapping between an {@link
 * HomeAssistantAgent} {@link org.openremote.model.asset.Asset} and its' {@link org.openremote.model.asset.agent.Protocol}.
 * This example does nothing useful but is intended to show where protocol classes should be created.
 */
public class HomeAssistantProtocol extends AbstractProtocol<HomeAssistantAgent, DefaultAgentLink> implements ProtocolAssetDiscovery {

    public static final String PROTOCOL_DISPLAY_NAME = "HomeAssistant Client";
    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, HomeAssistantProtocol.class);
    private final List<String> SUPPORTED_ENTITY_TYPES = new ArrayList<>(List.of("light", "switch", "binary_sensor", "sensor"));
    protected HomeAssistantClient client;
    protected boolean running;


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
        if (client.isConnectionSuccessful()) {
            LOG.info("Connection to HomeAssistant successful");
            setConnectionStatus(ConnectionStatus.CONNECTED);
            assetService = container.getService(ProtocolAssetService.class);
            executorService = container.getExecutorService();
        } else {
            LOG.warning("Connection to HomeAssistant failed");
            setConnectionStatus(ConnectionStatus.DISCONNECTED);
        }


    }


    @Override
    protected void doStop(Container container) throws Exception {

    }

    @Override
    protected void doLinkAttribute(String assetId, Attribute<?> attribute, DefaultAgentLink agentLink) throws RuntimeException {

    }

    @Override
    protected void doUnlinkAttribute(String assetId, Attribute<?> attribute, DefaultAgentLink agentLink) {

    }

    @Override
    protected void doLinkedAttributeWrite(Attribute<?> attribute, DefaultAgentLink agentLink, AttributeEvent event, Object processedValue) {
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
        ConnectionStatus status = agent.getAgentStatus().orElse(ConnectionStatus.DISCONNECTED);
        if (status == ConnectionStatus.DISCONNECTED) {
            LOG.info("Agent not connected so cannot perform discovery");
            return null;
        }

        List<HomeAssistantBaseEntity> homeAssistantBaseEntities = client.getEntities().orElse(null);

        if (homeAssistantBaseEntities != null) {
            setConnectionStatus(ConnectionStatus.CONNECTED);
            List<String> currentAssets = assetService.findAssets(agent.getId(), new AssetQuery().types(Asset.class)).stream().map(Asset::getName).toList();

            return executorService.submit(() -> {
                for (HomeAssistantBaseEntity entity : homeAssistantBaseEntities) {
                    LOG.info("Found entity: " + entity.getEntityId());
                    String assetType = entity.getEntityId().split("\\.")[0];


                    if (!SUPPORTED_ENTITY_TYPES.contains(assetType)) continue;

                    HomeAssistantBaseAsset entityAsset = switch (assetType) {
                        case "light" -> new HomeAssistantLightAsset(entity.getEntityId())
                                .setAssetType(entity.getEntityId())
                                .setState(entity.getState());
                        default -> new HomeAssistantBaseAsset(entity.getEntityId())
                                .setAssetType(entity.getEntityId())
                                .setState(entity.getState());
                    };

                    Map<String, Object> homeAssistantAttributes = entity.getAttributes();
                    entityAsset.setHomeAssistantTextAttributes(homeAssistantAttributes);

                    if (currentAssets.contains(entity.getEntityId())) {
                        LOG.info("Entity already exists: " + entity.getEntityId());
                        continue;
                    }

                    AssetTreeNode assetTreeNode = new AssetTreeNode(entityAsset);
                    assetConsumer.accept(new AssetTreeNode[]{assetTreeNode});
                }

            }, null);
        }
        return null;
    }
}
