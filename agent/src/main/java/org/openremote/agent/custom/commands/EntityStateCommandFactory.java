package org.openremote.agent.custom.commands;

import org.openremote.agent.custom.HomeAssistantEntityProcessor;
import org.openremote.agent.custom.assets.HomeAssistantBaseAsset;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;

import java.util.Map;
import java.util.Optional;

import static org.openremote.agent.custom.entities.HomeAssistantEntityType.ENTITY_TYPE_LIGHT;
import static org.openremote.agent.custom.entities.HomeAssistantEntityType.ENTITY_TYPE_SWITCH;

public final class EntityStateCommandFactory {

    public static Optional<EntityStateCommand> createEntityStateCommand(Asset<?> asset, Attribute<?> attribute, String value) {

        if (!(asset instanceof HomeAssistantBaseAsset homeAssistantAsset)) {
            return Optional.empty();
        }

        var entityId = homeAssistantAsset.getEntityId();
        var entityType = HomeAssistantEntityProcessor.getEntityTypeFromEntityId(entityId);
        var attributeName = attribute.getName();
        var isStateAttribute = attributeName.equals("state");

        // currently the only entity types that actually need to handle state changes are lights and switches.
        return switch (entityType) {
            case ENTITY_TYPE_LIGHT, ENTITY_TYPE_SWITCH -> {
                if (isStateAttribute) {
                    yield Optional.of(new EntityStateCommand("toggle", entityId, null, null));
                }
                yield Optional.of(new EntityStateCommand("turn_on", entityId, attributeName, value));
            }
            default -> Optional.empty();
        };

    }


}
