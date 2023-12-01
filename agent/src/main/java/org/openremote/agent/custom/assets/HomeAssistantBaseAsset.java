package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.attribute.AttributeEvent;

import java.util.function.Consumer;

@Entity
public class HomeAssistantBaseAsset extends Asset<HomeAssistantBaseAsset> {

    public static AssetDescriptor<HomeAssistantBaseAsset> DESCRIPTOR = new AssetDescriptor<>("cube-outline", "03a6f0", HomeAssistantBaseAsset.class);

    protected HomeAssistantBaseAsset() {
    }

    public HomeAssistantBaseAsset(String name) {
        super(name);
    }

}
