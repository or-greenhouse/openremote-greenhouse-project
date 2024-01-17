package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.MetaItemType;
import org.openremote.model.value.ValueType;

@Entity
public class HomeAssistantBaseAsset extends Asset<HomeAssistantBaseAsset> {

    public static AssetDescriptor<HomeAssistantBaseAsset> DESCRIPTOR = new AssetDescriptor<>("cube-outline", "03a6f0", HomeAssistantBaseAsset.class);

    public static final AttributeDescriptor<String> ENTITY_ID = new AttributeDescriptor<>("HomeAssistantEntityId", ValueType.TEXT, new MetaItem<>(MetaItemType.SECRET));

    public static final AttributeDescriptor<Boolean> IS_GROUP = new AttributeDescriptor<>("IsGroup", ValueType.BOOLEAN, new MetaItem<>(MetaItemType.SECRET));

    protected HomeAssistantBaseAsset() {
    }

    public HomeAssistantBaseAsset(String name, String entityId) {
        super(name);
        setEntityId(entityId);

    }

    public Boolean getIsGroup() {
        return getAttributes().getValue(IS_GROUP).orElseThrow();
    }

    public void setIsGroup(Boolean isGroup) {
        getAttributes().setValue(IS_GROUP, isGroup);
    }

    public String getEntityId() {
        return getAttributes().getValue(ENTITY_ID).orElseThrow();
    }

    public void setEntityId(String entityId) {
        getAttributes().setValue(ENTITY_ID, entityId);
    }

}
