package org.openremote.agent.custom;

import org.openremote.agent.custom.assets.HomeAssistantBaseAsset;
import org.openremote.agent.custom.assets.HomeAssistantLightAsset;
import org.openremote.agent.custom.entities.HomeAssistantBaseEntity;
import org.openremote.agent.custom.entities.HomeAssistantEntityStateEvent;
import org.openremote.agent.protocol.ProtocolAssetService;
import org.openremote.container.util.UniqueIdentifierGenerator;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
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
    //public static final String ENTITY_TYPE_SENSOR = "sensor";

    private final ProtocolAssetService protocolAssetService;
    private final String agentId;

    public HomeAssistantEntityProcessor(ProtocolAssetService protocolAssetService, String agentId) {
        this.protocolAssetService = protocolAssetService;
        this.agentId = agentId;
    }

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

            if (entityType.equals(ENTITY_TYPE_LIGHT)) {
                asset = new HomeAssistantLightAsset(entityId);
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
                    Attribute<Integer> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>(attributeKey, ValueType.INT_BYTE));
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
        var stateAttribute = asset.getAttribute("state");
        var brightnessAttribute = asset.getAttribute("brightness");

        LOG.info("Processing entity state event for asset: " + asset.getName() + " with state: " + event.getData().getNewBaseEntity().getState());

        //handle state (it attempts to cast to boolean first, then string)
        if (stateAttribute.isPresent()) {
            if (isAttributeAssignableFrom(stateAttribute.get(), Boolean.class)) {
                Attribute<Boolean> attribute = (Attribute<Boolean>) stateAttribute.get();
                attribute.setValue(event.getData().getNewBaseEntity().getState().equals("on") || event.getData().getNewBaseEntity().getState().equals("true"));
                asset.addOrReplaceAttributes(attribute);
            } else if (isAttributeAssignableFrom(stateAttribute.get(), String.class)) {
                Attribute<String> attribute = (Attribute<String>) stateAttribute.get();
                attribute.setValue(event.getData().getNewBaseEntity().getState());
                asset.addOrReplaceAttributes(attribute);
            }
        }

        //handle brightness (it attempts to cast to integer)
        if (brightnessAttribute.isPresent()) {
            if (isAttributeAssignableFrom(brightnessAttribute.get(), Integer.class)) {
                Attribute<Integer> attribute = (Attribute<Integer>) brightnessAttribute.get();
                attribute.setValue(event.getData().getNewBaseEntity().getAttributes().get("brightness") != null ? (int) event.getData().getNewBaseEntity().getAttributes().get("brightness") : 0);
                asset.addOrReplaceAttributes(attribute);
            }
        }

        this.protocolAssetService.mergeAsset(asset);
    }


    // Checks whether the attribute<?> can be assigned from the given class, allowing safe casting
    private Boolean isAttributeAssignableFrom(Attribute<?> attribute, Class<?> clazz) {
        return attribute.getType().getType().isAssignableFrom(clazz);
    }


    private Asset<?> findAssetByEntityId(String homeAssistantEntityId) {
        return protocolAssetService.findAssets(agentId, new AssetQuery().types(Asset.class)).stream()
                .filter(asset -> asset.getName().equals(homeAssistantEntityId))
                .findFirst()
                .orElse(null);
    }

    private String getEntityTypeFromEntityId(String entityId) {
        String[] parts = entityId.split("\\.");
        return parts[0];
    }

}
