package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.agent.custom.HomeAssistantAgentLink;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.asset.agent.AgentLink;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

import java.util.Map;

import static org.openremote.model.value.MetaItemType.AGENT_LINK;

@Entity
public class HomeAssistantBaseAsset extends Asset<HomeAssistantBaseAsset> {

    public static AssetDescriptor<HomeAssistantBaseAsset> DESCRIPTOR = new AssetDescriptor<>("cube-outline", null, HomeAssistantBaseAsset.class);
    public static final AttributeDescriptor<String> STATE = new AttributeDescriptor<>("state", ValueType.TEXT);
    public static final AttributeDescriptor<String> ASSET_TYPE = new AttributeDescriptor<>("assetType", ValueType.TEXT);


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
}
