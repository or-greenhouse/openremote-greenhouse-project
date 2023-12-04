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
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;
import static org.openremote.model.value.MetaItemType.AGENT_LINK;

public class HomeAssistantEntityProcessor {

    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, HomeAssistantProtocol.class);
    private final List<String> SUPPORTED_ENTITY_TYPES = new ArrayList<>(List.of(ENTITY_TYPE_LIGHT, ENTITY_TYPE_SWITCH, ENTITY_TYPE_BINARY_SENSOR));
    public static final String ENTITY_TYPE_LIGHT = "light";
    public static final String ENTITY_TYPE_SWITCH = "switch";
    public static final String ENTITY_TYPE_BINARY_SENSOR = "binary_sensor";
    public static final String ENTITY_TYPE_SENSOR = "sensor";

    private final HomeAssistantProtocol protocol;
    private final ProtocolAssetService protocolAssetService;
    private final String agentId;

    public HomeAssistantEntityProcessor(HomeAssistantProtocol protocol, ProtocolAssetService assetService) {
        this.protocol = protocol;
        this.protocolAssetService = assetService;
        this.agentId = protocol.getAgent().getId();
    }

    // Processes a Home Assistant entity state event and updates the appropriate asset
    public void handleEntityStateEvent(HomeAssistantEntityStateEvent event) {
        var entityId = event.getData().getEntityId();
        var entityTypeId = getEntityTypeFromEntityId(entityId);

        if (!SUPPORTED_ENTITY_TYPES.contains(entityTypeId)) {
            return; // skip unsupported entity types
        }

        var asset = findAssetByEntityId(entityId);
        if (asset == null)
            return;

        processEntityStateEvent(asset, event);
    }


    // Converts a list of Home Assistant entities to a list of OpenRemote assets
    public Optional<List<Asset<?>>> convertEntitiesToAssets(List<HomeAssistantBaseEntity> entities) {

        // Retrieve the current assets for this agent based on the entity id attribute
        List<String> currentAssets = protocolAssetService.findAssets(agentId, new AssetQuery().attributeName("HomeAssistantEntityId")).stream()
                .map(asset -> asset.getAttributes().getValue("HomeAssistantEntityId").orElseThrow().toString())
                .toList();

        List<Asset<?>> assets = new ArrayList<>();

        for (HomeAssistantBaseEntity entity : entities) {
            Map<String, Object> homeAssistantAttributes = entity.getAttributes();
            String entityId = entity.getEntityId();
            String entityType = getEntityTypeFromEntityId(entityId);

            if (currentAssets.contains(entityId) || !SUPPORTED_ENTITY_TYPES.contains(entityType)) {
                continue; // skip unsupported entity types and already discovered assets
            }

            // instantiate the appropriate asset class (only used for icons)
            var friendlyName = (String) homeAssistantAttributes.get("friendly_name"); // friendly name always exists for all entities from the Home Assistant API
            Asset<?> asset = switch (entityType) {
                case ENTITY_TYPE_LIGHT -> new HomeAssistantLightAsset(friendlyName, entityId);
                case ENTITY_TYPE_BINARY_SENSOR -> new HomeAssistantSensorAsset(friendlyName, entityId);
                case ENTITY_TYPE_SWITCH -> new HomeAssistantSwitchAsset(friendlyName, entityId);
                default -> new HomeAssistantBaseAsset(friendlyName, entityId);
            };


            //handle state (text or boolean)
            var assetState = entity.getState();
            if (assetState.equals("on") || assetState.equals("off") || assetState.equals("true") || assetState.equals("false")) {
                Attribute<Boolean> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>("state", ValueType.BOOLEAN));
                attribute.setValue(assetState.equals("on") || assetState.equals("true"));
            } else {
                Attribute<String> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>("state", ValueType.TEXT));
                attribute.setValue(assetState);
            }

            //handle the attributes
            for (Map.Entry<String, Object> entry : homeAssistantAttributes.entrySet()) {
                var attributeValue = entry.getValue();
                var attributeKey = entry.getKey();

                //Skip empty attributes
                if (entry.getKey().isEmpty() || entry.getValue() == null)
                    continue;
                //Do not import Min, Max attributes
                if (attributeKey.contains("min") || attributeKey.contains("max"))
                    continue;

                //Integer type check
                if (attributeValue instanceof Integer) {
                    Attribute<Integer> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>(attributeKey, ValueType.POSITIVE_INTEGER));
                    attribute.setValue((Integer) attributeValue);
                    continue; // skip to next iteration of loop
                }

                //String based boolean check (true, false, on, off)
                if (attributeValue instanceof String) {
                    if (attributeValue.equals("on") || attributeValue.equals("off") || attributeValue.equals("true") || attributeValue.equals("false")) {
                        Attribute<Boolean> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>(attributeKey, ValueType.BOOLEAN));
                        attribute.setValue(attributeValue.equals("on") || attributeValue.equals("true"));
                    }
                }

                //TODO: Handle other types of attributes (String, RGB, Date, Arrays, etc.)
            }
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

        // handle attributes dynamically
        for (Map.Entry<String, Object> eventAttribute : event.getData().getNewBaseEntity().getAttributes().entrySet()) {
            var assetAttribute = asset.getAttributes().get(eventAttribute.getKey());
            if (assetAttribute.isEmpty())
                continue; // skip if the attribute doesn't exist
            if (assetAttribute.get().getValue().equals(eventAttribute.getValue()))
                continue; // skip if the attribute value is the same
            AttributeEvent attributeEvent = new AttributeEvent(asset.getId(), assetAttribute.get().getName(), eventAttribute.getValue());
            protocol.handleExternalAttributeChange(attributeEvent);
        }

        //state needs to be handled separately, its part of the attributes parent obj.
        var stateAttribute = asset.getAttribute("state");
        if (stateAttribute.isPresent()) {
            AttributeEvent attributeEvent;
            if (isAttributeAssignableFrom(stateAttribute.get(), Boolean.class)) {
                Attribute<Boolean> attribute = (Attribute<Boolean>) stateAttribute.get();
                boolean value = event.getData().getNewBaseEntity().getState().equals("on") || event.getData().getNewBaseEntity().getState().equals("true");
                attributeEvent = new AttributeEvent(asset.getId(), attribute.getName(), value);
                protocol.handleExternalAttributeChange(attributeEvent);
            } else if (isAttributeAssignableFrom(stateAttribute.get(), String.class)) {
                Attribute<String> attribute = (Attribute<String>) stateAttribute.get();
                attributeEvent = new AttributeEvent(asset.getId(), attribute.getName(), event.getData().getNewBaseEntity().getState());
                protocol.handleExternalAttributeChange(attributeEvent);
            }
        }

    }

    // Checks whether the attribute<?> can be assigned from the given class, allowing safe casting
    private Boolean isAttributeAssignableFrom(Attribute<?> attribute, Class<?> clazz) {
        return attribute.getType().getType().isAssignableFrom(clazz);
    }

    // Retrieves the appropriate asset based on the given home assistant entity id
    private Asset<?> findAssetByEntityId(String homeAssistantEntityId) {

        return protocolAssetService.findAssets(agentId, new AssetQuery().attributeName("HomeAssistantEntityId")).stream()
                .filter(asset -> asset.getAttributes().getValue("HomeAssistantEntityId").orElseThrow().toString().equals(homeAssistantEntityId)).findFirst().orElse(null);


    }

    // Retrieves the entity type from the given home assistant entity id (format <entity_type>.<entity_id>)
    private String getEntityTypeFromEntityId(String entityId) {
        String[] parts = entityId.split("\\.");
        return parts[0];
    }

}
