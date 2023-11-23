package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

import java.util.Map;

@Entity
public class HomeAssistantBaseAsset extends Asset<HomeAssistantBaseAsset> {

    public static final AttributeDescriptor<String> ASSET_TYPE = new AttributeDescriptor<>("assetType", ValueType.TEXT);
    public static final AttributeDescriptor<String> STATE = new AttributeDescriptor<>("state", ValueType.TEXT);
    public static AssetDescriptor<HomeAssistantBaseAsset> ICON = new AssetDescriptor<>("cube-outline", null, HomeAssistantBaseAsset.class);

    protected HomeAssistantBaseAsset() {

    }

    public HomeAssistantBaseAsset(String name) {
        super(name);
    }

    public HomeAssistantBaseAsset setAssetType(String value) {
        getAttributes().getOrCreate(ASSET_TYPE).setValue(value);
        return this;
    }

    public HomeAssistantBaseAsset setState(String value) {
        getAttributes().getOrCreate(STATE).setValue(value);
        return this;
    }

    public HomeAssistantBaseAsset setHomeAssistantTextAttribute(String name, String value) {
        AttributeDescriptor<String> homeAssistantAttribute = new AttributeDescriptor<>(name, ValueType.TEXT);
        getAttributes().getOrCreate(homeAssistantAttribute).setValue(value);
        return this;
    }

    public HomeAssistantBaseAsset setHomeAssistantTextAttributes(Map<String, Object> homeAssistantAttributes) {
        for (Map.Entry<String, Object> entry : homeAssistantAttributes.entrySet()) {
            AttributeDescriptor<String> homeAssistantAttribute = new AttributeDescriptor<>(entry.getKey(), ValueType.TEXT);
            if (entry.getValue() == null) {
                getAttributes().getOrCreate(homeAssistantAttribute).setValue("");
            } else {
                getAttributes().getOrCreate(homeAssistantAttribute).setValue(entry.getValue().toString());
            }
        }
        return this;
    }

    public HomeAssistantBaseAsset setIcon(String value) {
        ICON = new AssetDescriptor<>(value, null, HomeAssistantBaseAsset.class);
        return this;
    }
}
