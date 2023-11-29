package org.openremote.agent.custom.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeAssistantStateEvent {
    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("data")
    private HomeAssistantStateEventData data;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public HomeAssistantStateEventData getData() {
        return data;
    }

    public void setData(HomeAssistantStateEventData data) {
        this.data = data;
    }


}

