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
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.openremote.model.value.MetaItemType.AGENT_LINK;

public class HomeAssistantEntityProcessor {

    private final List<String> SUPPORTED_ENTITY_TYPES = new ArrayList<>(List.of(ENTITY_TYPE_LIGHT, ENTITY_TYPE_SWITCH, ENTITY_TYPE_BINARY_SENSOR, ENTITY_TYPE_SENSOR));
    public static final String ENTITY_TYPE_LIGHT = "light";
    public static final String ENTITY_TYPE_SWITCH = "switch";
    public static final String ENTITY_TYPE_BINARY_SENSOR = "binary_sensor";
    public static final String ENTITY_TYPE_SENSOR = "sensor";

    private final ProtocolAssetService protocolAssetService;
    private final String agentId;

    public HomeAssistantEntityProcessor(ProtocolAssetService protocolAssetService, String agentId) {
        this.protocolAssetService = protocolAssetService;
        this.agentId = agentId;
    }

    public void processEntityStateEvent(HomeAssistantEntityStateEvent event) {
        var entityId = event.getData().getEntityId();
        var entityTypeId = getTypeFromEntityId(entityId);

        if (!SUPPORTED_ENTITY_TYPES.contains(entityTypeId)) {
            return; // skip unsupported entity types
        }

        var asset = findAssetByHomeAssistantEntityId(entityId);
        if (asset == null)
            return;

        switch (entityTypeId) {
            case ENTITY_TYPE_LIGHT:
                processLightEntityStateChange(asset, event);
                break;
            case ENTITY_TYPE_SWITCH, ENTITY_TYPE_BINARY_SENSOR, ENTITY_TYPE_SENSOR:
                processBaseEntityStateChange(asset, event);
                break;
            default:
        }

    }


    public Optional<List<Asset<?>>> processBaseEntitiesDynamically(List<HomeAssistantBaseEntity> entities) {
        List<String> currentAssets = protocolAssetService.findAssets(agentId, new AssetQuery().types(Asset.class)).stream().map(Asset::getName).toList();
        List<Asset<?>> assets = new ArrayList<>();

        for (HomeAssistantBaseEntity entity : entities) {
            Map<String, Object> homeAssistantAttributes = entity.getAttributes();
            String entityId = entity.getEntityId();
            String entityType = getTypeFromEntityId(entityId);

            if (currentAssets.contains(entityId)) {
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
                        attribute.setValue(Boolean.parseBoolean((String) attributeValue));
                        continue; // skip to next iteration of loop
                    }
                }

                //String check (default) (text)
                Attribute<String> attribute = asset.getAttributes().getOrCreate(new AttributeDescriptor<>(attributeKey, ValueType.TEXT));
                attribute.setValue(attributeValue.toString());
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
    private void processLightEntityStateChange(Asset<?> asset, HomeAssistantEntityStateEvent event) {
        var lightAssetStateAttribute = asset.getAttribute("state");
        var lightAssetBrightnessAttribute = asset.getAttribute("brightness");

        if (lightAssetStateAttribute.isEmpty() || lightAssetBrightnessAttribute.isEmpty())
            return;

        if ((lightAssetBrightnessAttribute.get()).getClass().isAssignableFrom(Integer.class)) {
            Attribute<Integer> attribute = (Attribute<Integer>) lightAssetBrightnessAttribute.get();
            attribute.setValue(event.getData().getNewBaseEntity().getAttributes().get("brightness") != null ? (int) event.getData().getNewBaseEntity().getAttributes().get("brightness") : 0);
            asset.addOrReplaceAttributes(attribute);
        }

        if (lightAssetStateAttribute.get().getClass().isAssignableFrom(Boolean.class)) {
            Attribute<Boolean> attribute = (Attribute<Boolean>) lightAssetStateAttribute.get();
            attribute.setValue(event.getData().getNewBaseEntity().getState().equals("on") || event.getData().getNewBaseEntity().getState().equals("true"));
            asset.addOrReplaceAttributes(attribute);
        } else {
            Attribute<String> attribute = (Attribute<String>) lightAssetStateAttribute.get();
            attribute.setValue(event.getData().getNewBaseEntity().getState());
            asset.addOrReplaceAttributes(attribute);
        }

        this.protocolAssetService.mergeAsset(asset);
    }

    @SuppressWarnings("unchecked") // suppress unchecked cast warnings for attribute.get() calls
    private void processBaseEntityStateChange(Asset<?> asset, HomeAssistantEntityStateEvent event) {
        var baseAssetStateAttribute = asset.getAttribute("state");
        if (baseAssetStateAttribute.isEmpty())
            return;

        if (baseAssetStateAttribute.get().getClass().isAssignableFrom(Boolean.class)) {
            Attribute<Boolean> attribute = (Attribute<Boolean>) baseAssetStateAttribute.get();
            attribute.setValue(event.getData().getNewBaseEntity().getState().equals("on") || event.getData().getNewBaseEntity().getState().equals("true"));
            asset.addOrReplaceAttributes(attribute);
        } else {
            Attribute<String> attribute = (Attribute<String>) baseAssetStateAttribute.get();
            attribute.setValue(event.getData().getNewBaseEntity().getState());
            asset.addOrReplaceAttributes(attribute);
        }

        this.protocolAssetService.mergeAsset(asset);
    }


    private Asset<?> findAssetByHomeAssistantEntityId(String homeAssistantEntityId) {
        return protocolAssetService.findAssets(agentId, new AssetQuery().types(Asset.class)).stream()
                .filter(asset -> asset.getName().equals(homeAssistantEntityId))
                .findFirst()
                .orElse(null);
    }

    private String getTypeFromEntityId(String entityId) {
        String[] parts = entityId.split("\\.");
        return parts[0];
    }

}
