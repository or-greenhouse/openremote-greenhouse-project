package org.openremote.agent.custom.assets;

import jakarta.persistence.Entity;
import org.openremote.agent.custom.HomeAssistantAgentLink;
import org.openremote.agent.protocol.tradfri.device.Device;
import org.openremote.agent.protocol.tradfri.device.event.EventHandler;
import org.openremote.agent.protocol.tradfri.device.event.LightChangeColourTemperatureEvent;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.asset.agent.AgentLink;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.openremote.model.value.MetaItemType.AGENT_LINK;
import static org.openremote.model.value.MetaItemType.READ_ONLY;

@Entity
public class HomeAssistantBaseAsset extends Asset<HomeAssistantBaseAsset> {

    public static AssetDescriptor<HomeAssistantBaseAsset> DESCRIPTOR = new AssetDescriptor<>("cube-outline", "03a6f0", HomeAssistantBaseAsset.class);

    protected HomeAssistantBaseAsset() {
    }

    public HomeAssistantBaseAsset(String name) {
        super(name);
    }

    public void addEventHandlers(Consumer<AttributeEvent> attributeEventConsumer)
    {
        attributes.forEach((key, value) -> {
            addEventHandlers(attributeEventConsumer);
        });
    }




}
