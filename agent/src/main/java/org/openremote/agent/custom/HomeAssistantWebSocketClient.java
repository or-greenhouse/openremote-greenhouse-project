package org.openremote.agent.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import org.openremote.agent.custom.entities.HomeAssistantEntityState;
import org.openremote.agent.protocol.io.AbstractNettyIOClient;
import org.openremote.agent.protocol.websocket.WebsocketIOClient;
import org.openremote.model.asset.agent.ConnectionStatus;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.util.ValueUtil;

import java.net.URI;
import java.util.Map;
import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;

public class HomeAssistantWebSocketClient extends WebsocketIOClient<String> {

    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, HomeAssistantHttpClient.class);
    private final HomeAssistantProtocol protocol;

    public HomeAssistantWebSocketClient(HomeAssistantProtocol protocol, URI homeAssistantWebSocketUrl) {
        super(homeAssistantWebSocketUrl, null, null);
        this.protocol = protocol;

        setEncoderDecoderProvider(() ->
            new ChannelHandler[] {new AbstractNettyIOClient.MessageToMessageDecoder<>(String.class, this)}
        );

        addMessageConsumer(this::onExternalMessageReceived);
        connect();
    }


    private void onExternalMessageReceived(String message) {
        LOG.info("Received message from Home Assistant WebSocket Endpoint: " + message);
        tryHandleEntityStateChange(message);
    }


    @Override
    protected void onConnectionStatusChanged(ConnectionStatus connectionStatus) {
        super.onConnectionStatusChanged(connectionStatus);
        LOG.info("Connection status changed to: " + connectionStatus);

        if (connectionStatus == ConnectionStatus.CONNECTED) {
            var authMessage = ValueUtil.asJSON(Map.of("type", "auth", "access_token", this.protocol.getAgent().getAccessToken().orElse("")));
            if(authMessage.isPresent())
            {
                LOG.info("Sending auth message to Home Assistant WebSocket Endpoint: " + authMessage.get());
                sendMessage(authMessage.get());
                subscribeToEntityStateChanges();
            }
        }
    }


    private void tryHandleEntityStateChange(String message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            HomeAssistantEntityState event = mapper.readValue(message, HomeAssistantEntityState.class);
            protocol.entityProcessor.handleEntityStateEvent(event.getEvent());
        } catch (Exception e) {
            // ignore - not an entity state change event
        }
    }

    // Subscribe to state changes for all entities within Home Assistant
    private void subscribeToEntityStateChanges() {
        sendMessage("{\"id\": 1, \"type\": \"subscribe_events\", \"event_type\": \"state_changed\"}");
    }
}
