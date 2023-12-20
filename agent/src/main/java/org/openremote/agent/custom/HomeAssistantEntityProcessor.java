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

import static org.openremote.agent.custom.entities.HomeAssistantEntityType.*;
import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;
import static org.openremote.model.value.MetaItemType.AGENT_LINK;

public class HomeAssistantEntityProcessor {

    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, HomeAssistantProtocol.class);


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

        if (entityCanBeImported(entityTypeId)) {
            return;
        }

        var asset = findAssetByEntityId(entityId);
        if (asset == null)
            return;

        processEntityStateEvent(asset, event);
    }

    // Converts a list of Home Assistant entities to a list of OpenRemote assets
    public Optional<List<Asset<?>>> convertEntitiesToAssets(List<HomeAssistantBaseEntity> entities) {
        List<String> currentAssets = protocolAssetService.findAssets(agentId, new AssetQuery().attributeName("HomeAssistantEntityId")).stream()
                .map(asset -> asset.getAttributes().getValue("HomeAssistantEntityId").orElseThrow().toString())
                .toList();
        List<Asset<?>> assets = new ArrayList<>();

        for (HomeAssistantBaseEntity entity : entities) {
            Map<String, Object> homeAssistantAttributes = entity.getAttributes();
            String entityId = entity.getEntityId();
            String entityType = getEntityTypeFromEntityId(entityId);

            if (currentAssets.contains(entityId) || entityCanBeImported(entityType)) {
                continue;
            }

            Asset<?> asset = initiateAssetClass(homeAssistantAttributes, entityType, entityId);

            handleStateConversion(entity, asset);

            for (Map.Entry<String, Object> entry : homeAssistantAttributes.entrySet()) {
                handleAttributeConversion(entry, asset);
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

    // Initiates the appropriate asset class based on the given entity type
    private Asset<?> initiateAssetClass(Map<String, Object> homeAssistantAttributes, String entityType, String entityId) {
        var friendlyName = (String) homeAssistantAttributes.get("friendly_name");
        return switch (entityType) {
            case ENTITY_TYPE_LIGHT -> new HomeAssistantLightAsset(friendlyName, entityId);
            case ENTITY_TYPE_BINARY_SENSOR, ENTITY_TYPE_SENSOR -> new HomeAssistantSensorAsset(friendlyName, entityId);
            case ENTITY_TYPE_SWITCH -> new HomeAssistantSwitchAsset(friendlyName, entityId);
            default -> new HomeAssistantBaseAsset(friendlyName, entityId);
        };
    }


    // Handles the conversion of Home Assistant attributes to OpenRemote attributes
    private void handleAttributeConversion(Map.Entry<String, Object> entry, Asset<?> asset) {
        LOG.info("Processing attribute: " + entry.getKey() + " with value: " + entry.getValue());
        var attributeValue = entry.getValue();
        var attributeKey = entry.getKey();

        if (entry.getKey().isEmpty() || entry.getValue() == null)
            return;

        //Do not import attribute keys that contain min, max, or supported_ (these are not useful for the user)
        if (attributeKey.contains("min") || attributeKey.contains("max") || attributeKey.contains("supported_features"))
            return;

        if (attributeValue instanceof Integer) {
            Attribute<Integer> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>(attributeKey, ValueType.POSITIVE_INTEGER));
            attribute.setValue((Integer) attributeValue);

            if (attributeKey.equals("off_brightness")) { //
                attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>("brightness", ValueType.POSITIVE_INTEGER));
                attribute.setValue((Integer) attributeValue);
            }
            return; // skip the rest of the checks
        }

        //String based boolean check (true, false, on, off)
        if (attributeValue instanceof String) {
            if (attributeValue.equals("on") || attributeValue.equals("off") || attributeValue.equals("true") || attributeValue.equals("false")) {
                Attribute<Boolean> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>(attributeKey, ValueType.BOOLEAN));
                attribute.setValue(attributeValue.equals("on") || attributeValue.equals("true"));
            }
        }
    }

    // Handles the conversion of Home Assistant state to OpenRemote state
    private void handleStateConversion(HomeAssistantBaseEntity entity, Asset<?> asset) {
        var assetState = entity.getState();
        if (assetState.equals("on") || assetState.equals("off") || assetState.equals("true") || assetState.equals("false")) {
            Attribute<Boolean> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>("state", ValueType.BOOLEAN));
            attribute.setValue(assetState.equals("on") || assetState.equals("true"));
        } else {
            Attribute<String> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>("state", ValueType.TEXT));
            attribute.setValue(assetState);
        }
    }


    @SuppressWarnings("unchecked") // suppress unchecked cast warnings for attribute.get() calls
    private void processEntityStateEvent(Asset<?> asset, HomeAssistantEntityStateEvent event) {

        for (Map.Entry<String, Object> eventAttribute : event.getData().getNewBaseEntity().getAttributes().entrySet()) {
            var assetAttribute = asset.getAttributes().get(eventAttribute.getKey());
            if (assetAttribute.isEmpty())
                continue;
            if (assetAttribute.get().getValue().equals(eventAttribute.getValue()))
                continue;

            AttributeEvent attributeEvent = new AttributeEvent(asset.getId(), assetAttribute.get().getName(), eventAttribute.getValue());
            protocol.handleExternalAttributeChange(attributeEvent);
        }

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
    public static String getEntityTypeFromEntityId(String entityId) {
        String[] parts = entityId.split("\\.");
        return parts[0];
    }

    private boolean entityCanBeImported(String entityType) {
        //split get imported entity types string by comma
        //check if the entity type is in the list
        var importedEntityTypes = protocol.getAgent().getImportedEntityTypes().orElse("").split(",");
        for (String importedEntityType : importedEntityTypes) {
            if (importedEntityType.equals(entityType))
                return false;
        }
        return true;
    }

}
