package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.agent.custom.entities.HomeAssistantBaseEntity;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.asset.impl.LightAsset;
import org.openremote.model.asset.impl.ThingAsset;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueFormat;
import org.openremote.model.value.ValueType;
import org.openremote.model.value.impl.ColourRGB;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;

@Entity
public class HomeAssistantBaseAsset extends Asset<HomeAssistantBaseAsset> {
    // public static AssetDescriptor<HomeAssistantBaseAsset> DESCRIPTOR = new AssetDescriptor<>("cube-outline", null, HomeAssistantBaseAsset.class);

    public static AssetDescriptor<HomeAssistantBaseAsset> descriptor = new AssetDescriptor<>("cube-outline", null, HomeAssistantBaseAsset.class);
    public static final AttributeDescriptor<String> ASSET_TYPE = new AttributeDescriptor<>("assetType", ValueType.TEXT);
    public static final AttributeDescriptor<String> STATE = new AttributeDescriptor<>("state", ValueType.TEXT);

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

    public HomeAssistantBaseAsset setHassTextAttribute(String name, String value) {
        AttributeDescriptor<String> hassAttribute = new AttributeDescriptor<>(name, ValueType.TEXT);
        getAttributes().getOrCreate(hassAttribute).setValue(value);
        return this;
    }

    public HomeAssistantBaseAsset setHassTextAttributes(Map<String, Object> hassAttributes) {
        List<AttributeDescriptor<String>> attributes = new ArrayList<>();
        for (Map.Entry<String, Object> entry : hassAttributes.entrySet()) {
            AttributeDescriptor<String> hassAttribute = new AttributeDescriptor<>(entry.getKey(), ValueType.TEXT);

            if (entry.getValue() == null) {
                getAttributes().getOrCreate(hassAttribute).setValue("");
            } else getAttributes().getOrCreate(hassAttribute).setValue(entry.getValue().toString());

            attributes.add(hassAttribute);
        }
        return this;
    }

    public HomeAssistantBaseAsset setIcon(String value) {
        descriptor = new AssetDescriptor<>(value, null, HomeAssistantBaseAsset.class);
        return this;
    }
}
