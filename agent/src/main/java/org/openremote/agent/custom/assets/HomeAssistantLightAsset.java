package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

@Entity
public class HomeAssistantLightAsset extends HomeAssistantBaseAsset {

    public static final AssetDescriptor<HomeAssistantLightAsset> DESCRIPTOR = new AssetDescriptor<>("lightbulb", "e6688a", HomeAssistantLightAsset.class);

    protected HomeAssistantLightAsset() {
    }

    public HomeAssistantLightAsset(String name, String entityId) {
        super(name, entityId);
    }

}
