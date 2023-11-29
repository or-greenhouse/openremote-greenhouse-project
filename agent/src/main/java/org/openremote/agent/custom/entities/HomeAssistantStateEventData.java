package org.openremote.agent.custom.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeAssistantStateEventData {

    @JsonProperty("entity_id")
    private String entityId;

    @JsonProperty("new_state")
    private HomeAssistantStateEventDataState newState;


    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setNewState(HomeAssistantStateEventDataState newState) {
        this.newState = newState;
    }

    public HomeAssistantStateEventDataState getNewState() {
        return newState;
    }
}
