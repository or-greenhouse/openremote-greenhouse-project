package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.model.asset.AssetDescriptor;

@Entity
public class HomeAssistantLightAsset extends HomeAssistantBaseAsset {

    public static final AssetDescriptor<HomeAssistantLightAsset> DESCRIPTOR = new AssetDescriptor<>("lightbulb", null, HomeAssistantLightAsset.class);


    protected HomeAssistantLightAsset() {

    }
    public HomeAssistantLightAsset(String name) {
        super(name);
    }


}
