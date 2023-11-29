package org.openremote.agent.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.openremote.agent.custom.entities.HomeAssistantEntityState;
import org.openremote.model.syslog.SyslogCategory;

import java.net.URI;
import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;

@ClientEndpoint
public class HomeAssistantWebSocketClient {

    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, HomeAssistantClient.class);
    private final String webSocketEndpoint;
    private final HomeAssistantProtocol protocol;

    private Session session;

    public HomeAssistantWebSocketClient(HomeAssistantProtocol protocol) {
        var homeAssistantUrl = protocol.getAgent().getHomeAssistantUrl().orElseThrow();
        this.webSocketEndpoint = (homeAssistantUrl + "/api/websocket").replace("http", "ws");
        this.protocol = protocol;

    }

    public void connect() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            LOG.info("Connecting to Home Assistant WebSocket Endpoint: " + webSocketEndpoint);
            container.connectToServer(this, URI.create(webSocketEndpoint));
        } catch (Exception e) {
            LOG.warning("Error establishing connection to Home Assistant WebSocket Endpoint: " + e.getMessage());
            throw new RuntimeException(e);
        }
        subscribeToEntityStateChanges();
    }

    public void sendMessage(String message) {
        try {
            session.getBasicRemote().sendText(message);
            LOG.info("Sent message to Home Assistant WebSocket Endpoint: " + message);
        } catch (Exception e) {
            LOG.warning("Error sending message to Home Assistant WebSocket Endpoint: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @OnOpen
    private void onOpen(Session session) {
        this.session = session;
        LOG.info("Connected to Home Assistant WebSocket Endpoint");
        try {
            session.getBasicRemote().sendText("{\"type\": \"auth\",\"access_token\": \"" + this.protocol.getAgent().getAccessToken().orElse("") + "\"}");
        } catch (Exception e) {
            LOG.warning("Error sending authentication message to Home Assistant WebSocket Endpoint: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @OnError
    private void onError(Throwable t) {
        LOG.warning("Error occurred on Home Assistant WebSocket Endpoint: " + t.getMessage());
    }

    @OnMessage
    private void onMessage(String message) {
        LOG.info("Received message from Home Assistant WebSocket Endpoint: " + message);
        tryHandleEntityStateChange(message);
    }

    private void tryHandleEntityStateChange(String message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            HomeAssistantEntityState event = mapper.readValue(message, HomeAssistantEntityState.class);
            protocol.entityProcessor.processEntityStateEvent(event.getEvent());
        } catch (JsonProcessingException e) {
            LOG.warning("Error parsing message from Home Assistant WebSocket Endpoint: " + e.getMessage());
        }
    }

    // Subscribe to state changes for all entities within Home Assistant
    private void subscribeToEntityStateChanges() {
        sendMessage("{\"id\": 1, \"type\": \"subscribe_events\", \"event_type\": \"state_changed\"}");
    }
}
