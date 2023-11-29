package org.openremote.agent.custom.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class HomeAssistantStateEventState {
    @JsonProperty("entity_id")
    private String entityId;

    @JsonProperty("state")
    private String state;

    @JsonProperty("attributes")
    private Map<String, Object> homeAssistantAttributes;
}
