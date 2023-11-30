package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;

@Entity
public class HomeAssistantDynamicAsset extends Asset<HomeAssistantDynamicAsset> {
    public static AssetDescriptor<HomeAssistantDynamicAsset> DESCRIPTOR = new AssetDescriptor<>("cube-outline", null, HomeAssistantDynamicAsset.class);

    protected HomeAssistantDynamicAsset() {
    }

    public HomeAssistantDynamicAsset(String name) {
        super(name);
    }


}
