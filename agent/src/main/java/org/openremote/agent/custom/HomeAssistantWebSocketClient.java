package org.openremote.agent.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.openremote.agent.custom.entities.HomeAssistantState;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.syslog.SyslogCategory;

import java.net.URI;
import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;

@ClientEndpoint
public class HomeAssistantWebSocketClient {

    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, HomeAssistantClient.class);
    private final String WebSocketUrl;
    private final HomeAssistantProtocol protocol;
    private Session session;

    public HomeAssistantWebSocketClient(HomeAssistantProtocol protocol) {
//        var webSocketUrl = homeAssistantUrl + "/api/websocket";
//        webSocketUrl = webSocketUrl.replace("https", "ws");

        this.WebSocketUrl = "ws://192.168.178.22:8123/api/websocket";
        this.protocol = protocol;
    }

    public void connect() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            LOG.info("Connecting to Home Assistant WebSocket Endpoint: " + WebSocketUrl);
            container.connectToServer(this, URI.create(WebSocketUrl));
        } catch (Exception e) {
            LOG.warning("Error establishing connection to Home Assistant WebSocket Endpoint: " + e.getMessage());
            throw new RuntimeException(e);
        }

        subscribeToStateChanges();
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
        LOG.warning("Error on Home Assistant WebSocket Endpoint: " + t.getMessage());
    }

    @OnMessage
    private void onMessage(String message) {
        LOG.info("Received message from Home Assistant WebSocket Endpoint: " + message);

        try {
            ObjectMapper mapper = new ObjectMapper();
            HomeAssistantState event = mapper.readValue(message, HomeAssistantState.class);
            String entityId = event.getEvent().getData().getEntityId();
            var asset = protocol.getAssetFromHomeAssistantEntityId(entityId);

            if (asset != null) {
                var attribute = asset.getAttribute("state");

                if (attribute.isPresent()) {
                    Attribute<String> stringAttribute = (Attribute<String>) attribute.get();
                    LOG.info("Updating attribute: " + attribute.get().getName() + " with value: " + event.getEvent().getData().getNewState().getState());
                    stringAttribute.setValue(event.getEvent().getData().getNewState().getState());
                    asset.addOrReplaceAttributes(stringAttribute);
                    protocol.saveAssetChanges(asset);
                }
            }
        } catch (JsonProcessingException e) {
            LOG.warning("Error parsing message from Home Assistant WebSocket Endpoint: " + e.getMessage());
        }
    }

    private void subscribeToStateChanges() {
        sendMessage("{\"id\": 1, \"type\": \"subscribe_events\", \"event_type\": \"state_changed\"}");
    }
}
