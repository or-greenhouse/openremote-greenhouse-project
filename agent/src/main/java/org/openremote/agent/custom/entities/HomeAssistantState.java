package org.openremote.agent.custom.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeAssistantState {
    @JsonProperty("type")
    private String type;

    @JsonProperty("event")
    private HomeAssistantStateEvent event;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public HomeAssistantStateEvent getEvent() {
        return event;
    }

    public void setEvent(HomeAssistantStateEvent event) {
        this.event = event;
    }

}

