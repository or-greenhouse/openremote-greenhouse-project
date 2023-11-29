package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

@Entity
public class HomeAssistantLightAsset extends HomeAssistantBaseAsset {

    public static final AssetDescriptor<HomeAssistantLightAsset> DESCRIPTOR = new AssetDescriptor<>("lightbulb", null, HomeAssistantLightAsset.class);
    public static final AttributeDescriptor<Boolean> ONOFF = new AttributeDescriptor<>("lightStatus", ValueType.BOOLEAN);
    public static final AttributeDescriptor<Integer> LIGHT_BRIGHTNESS = new AttributeDescriptor<>("brightness", ValueType.POSITIVE_INTEGER);

    protected HomeAssistantLightAsset() {
    }

    public HomeAssistantLightAsset(String name) {
        super(name);
    }

    public HomeAssistantLightAsset setLightOnOff(Boolean value) {
        getAttributes().getOrCreate(ONOFF).setValue(value);
        return this;
    }

    public HomeAssistantLightAsset setLightBrightness(Integer value) {
        getAttributes().getOrCreate(LIGHT_BRIGHTNESS).setValue(value);
        return this;
    }

}
