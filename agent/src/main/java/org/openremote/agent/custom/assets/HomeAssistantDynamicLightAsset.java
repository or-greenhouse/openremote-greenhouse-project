package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;

@Entity
public class HomeAssistantDynamicLightAsset extends Asset<HomeAssistantDynamicLightAsset> {
    public static AssetDescriptor<HomeAssistantDynamicLightAsset> DESCRIPTOR = new AssetDescriptor<>("lightbulb", null, HomeAssistantDynamicLightAsset.class);

    protected HomeAssistantDynamicLightAsset() {
    }

    public HomeAssistantDynamicLightAsset(String name) {
        super(name);
    }
}
