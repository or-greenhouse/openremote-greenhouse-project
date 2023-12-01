package org.openremote.agent.custom;

import org.openremote.agent.custom.assets.HomeAssistantBaseAsset;
import org.openremote.agent.custom.assets.HomeAssistantLightAsset;
import org.openremote.agent.custom.assets.HomeAssistantSensorAsset;
import org.openremote.agent.custom.assets.HomeAssistantSwitchAsset;
import org.openremote.agent.custom.entities.HomeAssistantBaseEntity;
import org.openremote.agent.custom.entities.HomeAssistantEntityStateEvent;
import org.openremote.agent.protocol.ProtocolAssetService;
import org.openremote.container.util.UniqueIdentifierGenerator;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.*;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;
import org.openremote.protocol.zwave.model.commandclasses.channel.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;
import static org.openremote.model.value.MetaItemType.AGENT_LINK;
import static org.openremote.model.value.MetaItemType.READ_ONLY;

public class HomeAssistantEntityProcessor {

    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, HomeAssistantProtocol.class);
    private final List<String> SUPPORTED_ENTITY_TYPES = new ArrayList<>(List.of(ENTITY_TYPE_LIGHT, ENTITY_TYPE_SWITCH, ENTITY_TYPE_BINARY_SENSOR));
    public static final String ENTITY_TYPE_LIGHT = "light";
    public static final String ENTITY_TYPE_SWITCH = "switch";
    public static final String ENTITY_TYPE_BINARY_SENSOR = "binary_sensor";
    //public static final String ENTITY_TYPE_SENSOR = "sensor";

    private final ProtocolAssetService protocolAssetService;
    private final String agentId;

    public HomeAssistantEntityProcessor(ProtocolAssetService protocolAssetService, String agentId) {
        this.protocolAssetService = protocolAssetService;
        this.agentId = agentId;
    }

    // Processes a Home Assistant entity state event and updates the appropriate asset
    public void processEntityStateEvent(HomeAssistantEntityStateEvent event) {
        var entityId = event.getData().getEntityId();
        var entityTypeId = getEntityTypeFromEntityId(entityId);

        if (!SUPPORTED_ENTITY_TYPES.contains(entityTypeId)) {
            return; // skip unsupported entity types
        }

        var asset = findAssetByEntityId(entityId);
        if (asset == null)
            return;

        switch (entityTypeId) {
            case ENTITY_TYPE_LIGHT, ENTITY_TYPE_SWITCH, ENTITY_TYPE_BINARY_SENSOR:
                processEntityStateEvent(asset, event);
                break;
            default:
        }

    }


    // Converts a list of Home Assistant entities to a list of OpenRemote assets
    public Optional<List<Asset<?>>> convertEntitiesToAssets(List<HomeAssistantBaseEntity> entities) {
        List<String> currentAssets = protocolAssetService.findAssets(agentId, new AssetQuery().types(Asset.class)).stream().map(Asset::getName).toList();
        List<Asset<?>> assets = new ArrayList<>();

        for (HomeAssistantBaseEntity entity : entities) {
            Map<String, Object> homeAssistantAttributes = entity.getAttributes();
            String entityId = entity.getEntityId();
            String entityType = getEntityTypeFromEntityId(entityId);

            if (currentAssets.contains(entityId) || !SUPPORTED_ENTITY_TYPES.contains(entityType)) {
                continue; // skip unsupported entity types and already discovered assets
            }

            Asset<?> asset;
            asset = new HomeAssistantBaseAsset(entityId);

            // handle entity type (only used for icons)
            switch (entityType) {
                case ENTITY_TYPE_LIGHT:
                    asset = new HomeAssistantLightAsset(entityId);
                    break;
                case ENTITY_TYPE_BINARY_SENSOR:
                    asset = new HomeAssistantSensorAsset(entityId);
                    break;
                case ENTITY_TYPE_SWITCH:
                    asset = new HomeAssistantSwitchAsset(entityId);
                    break;
                default:
            }


            //handle asset state (text or boolean)
            var assetState = entity.getState();
            if (assetState.equals("on") || assetState.equals("off") || assetState.equals("true") || assetState.equals("false")) {
                Attribute<Boolean> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>("state", ValueType.BOOLEAN));
                attribute.setValue(assetState.equals("on") || assetState.equals("true"));
            } else {
                Attribute<String> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>("state", ValueType.TEXT));
                attribute.setValue(assetState);
            }

            //handle the asset attributes
            for (Map.Entry<String, Object> entry : homeAssistantAttributes.entrySet()) {
                var attributeValue = entry.getValue();
                var attributeKey = entry.getKey();

                if (entry.getKey().isEmpty() || entry.getValue() == null)
                    continue;

                //Integer check
                if (attributeValue instanceof Integer) {
                    Attribute<Integer> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>(attributeKey, ValueType.POSITIVE_INTEGER));
                    attribute.setValue((Integer) attributeValue);
                    continue; // skip to next iteration of loop
                }

                //String boolean check (true, false, on, off)
                if (attributeValue instanceof String) {
                    if (attributeValue.equals("on") || attributeValue.equals("off") || attributeValue.equals("true") || attributeValue.equals("false")) {
                        Attribute<Boolean> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>(attributeKey, ValueType.BOOLEAN));
                        attribute.setValue(attributeValue.equals("on") || attributeValue.equals("true"));
                        continue; // skip to next iteration of loop
                    }
                }


                if (attributeKey.equals("friendly_name") && attributeValue instanceof String)
                {
                    asset.getAttributes().getOrCreate(new AttributeDescriptor<>(attributeKey, ValueType.TEXT))
                            .addOrReplaceMeta(new MetaItem<>(READ_ONLY))
                            .setValue((String)attributeValue);

                }

                //Handle other types of attributes (String, RGB, Date)


            }

            // add agent links to each attribute of the asset
            asset.getAttributes().forEach(attribute -> {
                var agentLink = new HomeAssistantAgentLink(agentId, entityType, entity.getEntityId());
                attribute.addOrReplaceMeta(new MetaItem<>(AGENT_LINK, agentLink));
            });

            asset.setId(UniqueIdentifierGenerator.generateId());
            assets.add(asset);
        }


        return Optional.of(assets);
    }


    @SuppressWarnings("unchecked") // suppress unchecked cast warnings for attribute.get() calls
    private void processEntityStateEvent(Asset<?> asset, HomeAssistantEntityStateEvent event) {
        if (asset == null)
            return;

        for (Map.Entry<String, Object> eventAttribute : event.getData().getNewBaseEntity().getAttributes().entrySet()) {
            var assetAttribute = asset.getAttributes().get(eventAttribute.getKey());
            if (assetAttribute.isEmpty())
                continue; // skip if the attribute doesn't exist

            if (assetAttribute.get().getValue().equals(eventAttribute.getValue()))
                continue; // skip if the attribute value is the same

            LOG.info("Updating attribute: " + assetAttribute.get().getName() + " to value: " + eventAttribute.getValue() + " for asset: " + asset.getId());
            AttributeEvent attributeEvent = new AttributeEvent(asset.getId(),assetAttribute.get().getName(), eventAttribute.getValue());
            protocolAssetService.sendAttributeEvent(attributeEvent);
        }

        var stateAttribute = asset.getAttribute("state");
           if (stateAttribute.isPresent()) {
            if (isAttributeAssignableFrom(stateAttribute.get(), Boolean.class)) {
                Attribute<Boolean> attribute = (Attribute<Boolean>) stateAttribute.get();
                boolean value = event.getData().getNewBaseEntity().getState().equals("on") || event.getData().getNewBaseEntity().getState().equals("true");
                AttributeEvent attributeEvent = new AttributeEvent(asset.getId(), attribute.getName(), value);
                protocolAssetService.sendAttributeEvent(attributeEvent);
            } else if (isAttributeAssignableFrom(stateAttribute.get(), String.class)) {
                Attribute<String> attribute = (Attribute<String>) stateAttribute.get();
                AttributeEvent attributeEvent = new AttributeEvent(asset.getId(), attribute.getName(), event.getData().getNewBaseEntity().getState());
                protocolAssetService.sendAttributeEvent(attributeEvent);
            }
        }

    }

    // Checks whether the attribute<?> can be assigned from the given class, allowing safe casting
    private Boolean isAttributeAssignableFrom(Attribute<?> attribute, Class<?> clazz) {
        return attribute.getType().getType().isAssignableFrom(clazz);
    }

    // Retrieves the appropriate asset based on the given home assistant entity id
    private Asset<?> findAssetByEntityId(String homeAssistantEntityId) {
        return protocolAssetService.findAssets(agentId, new AssetQuery().types(Asset.class)).stream()
                .filter(asset -> asset.getName().equals(homeAssistantEntityId))
                .findFirst()
                .orElse(null);
    }

    // Retrieves the entity type from the given home assistant entity id (format <entity_type>.<entity_id>)
    private String getEntityTypeFromEntityId(String entityId) {
        String[] parts = entityId.split("\\.");
        return parts[0];
    }



}
