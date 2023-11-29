package org.openremote.agent.custom;

import org.openremote.agent.custom.assets.HomeAssistantBaseAsset;
import org.openremote.agent.custom.assets.HomeAssistantLightAsset;
import org.openremote.agent.custom.entities.HomeAssistantBaseEntity;
import org.openremote.agent.custom.entities.HomeAssistantEntityStateEvent;
import org.openremote.agent.custom.helpers.HomeAssistantJsonHelper;
import org.openremote.agent.protocol.ProtocolAssetService;
import org.openremote.container.util.UniqueIdentifierGenerator;
import org.openremote.model.asset.Asset;
import org.openremote.model.query.AssetQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        var entityTypeId = HomeAssistantJsonHelper.getTypeFromEntityId(entityId);

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

    public Optional<List<Asset<?>>> processBaseEntities(List<HomeAssistantBaseEntity> entities) {
        List<String> currentAssets = protocolAssetService.findAssets(agentId, new AssetQuery().types(Asset.class)).stream().map(Asset::getName).toList();
        List<Asset<?>> assets = new ArrayList<>();

        for (HomeAssistantBaseEntity entity : entities) {
            String entityId = entity.getEntityId();
            String entityType = HomeAssistantJsonHelper.getTypeFromEntityId(entityId);
            if (!SUPPORTED_ENTITY_TYPES.contains(entityType) || currentAssets.contains(entityId)) {
                continue; // skip unsupported entity types and already discovered assets
            }
            Asset<?> asset;
            switch (entityType) {
                case ENTITY_TYPE_LIGHT:
                    asset = new HomeAssistantLightAsset(entity.getEntityId())
                            .setLightBrightness(entity.getAttributes().get("brightness") != null ? (int) entity.getAttributes().get("brightness") : 0)
                            .setLightOnOff(getBooleanStateFromEntityState(entity.getState()))
                            .setState(entity.getState())
                            .setAssetType(ENTITY_TYPE_LIGHT);
                    break;
                case ENTITY_TYPE_SWITCH:
                    asset = new HomeAssistantBaseAsset(entity.getEntityId())
                            .setAssetType(ENTITY_TYPE_SWITCH)
                            .setState(entity.getState());

                    break;
                case ENTITY_TYPE_BINARY_SENSOR:
                    asset = new HomeAssistantBaseAsset(entity.getEntityId())
                            .setAssetType(ENTITY_TYPE_BINARY_SENSOR)
                            .setState(entity.getState());
                    break;
                case ENTITY_TYPE_SENSOR:
                    asset = new HomeAssistantBaseAsset(entity.getEntityId())
                            .setAssetType(ENTITY_TYPE_SENSOR)
                            .setState(entity.getState());
                    break;
                default:
                    continue; // skip unsupported entity types
            }

            if (asset == null)
                continue;

            asset.setId(UniqueIdentifierGenerator.generateId());
            assets.add(asset);
        }
        return Optional.of(assets);
    }


    private void processLightEntityStateChange(Asset<?> asset, HomeAssistantEntityStateEvent event) {
        var lightAssetStateAttribute = asset.getAttribute(HomeAssistantBaseAsset.STATE);
        var lightAssetOnOffAttribute = asset.getAttribute(HomeAssistantLightAsset.ONOFF);
        var lightAssetBrightnessAttribute = asset.getAttribute(HomeAssistantLightAsset.LIGHT_BRIGHTNESS);

        if (lightAssetStateAttribute.isEmpty() || lightAssetOnOffAttribute.isEmpty() || lightAssetBrightnessAttribute.isEmpty())
            return;

        lightAssetStateAttribute.get().setValue(event.getData().getNewBaseEntity().getState());
        lightAssetOnOffAttribute.get().setValue(getBooleanStateFromEntityState(event.getData().getNewBaseEntity().getState()));
        lightAssetBrightnessAttribute.get().setValue(event.getData().getNewBaseEntity().getAttributes().get("brightness") != null ? (int) event.getData().getNewBaseEntity().getAttributes().get("brightness") : 0);


        asset.addOrReplaceAttributes(lightAssetStateAttribute.get());
        asset.addOrReplaceAttributes(lightAssetOnOffAttribute.get());
        asset.addOrReplaceAttributes(lightAssetBrightnessAttribute.get());
        this.protocolAssetService.mergeAsset(asset);
    }

    private void processBaseEntityStateChange(Asset<?> asset, HomeAssistantEntityStateEvent event) {
        var baseAssetStateAttribute = asset.getAttribute(HomeAssistantBaseAsset.STATE);

        if (baseAssetStateAttribute.isEmpty())
            return;

        baseAssetStateAttribute.get().setValue(event.getData().getNewBaseEntity().getState());
        asset.addOrReplaceAttributes(baseAssetStateAttribute.get());
        this.protocolAssetService.mergeAsset(asset);
    }

    private boolean getBooleanStateFromEntityState(String state) {
        return state.equals("on");
    }

    private Asset<?> findAssetByHomeAssistantEntityId(String homeAssistantEntityId) {
        return protocolAssetService.findAssets(agentId, new AssetQuery().types(Asset.class)).stream()
                .filter(asset -> asset.getName().equals(homeAssistantEntityId))
                .findFirst()
                .orElse(null);
    }

}
