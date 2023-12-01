package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.asset.impl.LightAsset;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

@Entity
public class HomeAssistantSensorAsset extends HomeAssistantBaseAsset {

    public static final AssetDescriptor<HomeAssistantSensorAsset> DESCRIPTOR = new AssetDescriptor<>("motion-sensor", "2386f0", HomeAssistantSensorAsset.class);

    protected HomeAssistantSensorAsset() {
    }

    public HomeAssistantSensorAsset(String name) {
        super(name);
    }

}
