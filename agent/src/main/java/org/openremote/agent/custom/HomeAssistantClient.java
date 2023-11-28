package org.openremote.agent.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openremote.agent.custom.entities.HomeAssistantBaseEntity;
import org.openremote.model.syslog.SyslogCategory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;

public class HomeAssistantClient {

    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, HomeAssistantClient.class);
    private final String HomeAssistantUrl;
    private final String Token;

    public HomeAssistantClient(String homeAssistantUrl, String token) {
        this.HomeAssistantUrl = homeAssistantUrl;
        this.Token = token;
    }

    public Optional<List<HomeAssistantBaseEntity>> getEntities() {
        Optional<String> response = sendGetRequest("/api/states");
        if (response.isPresent()) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                List<HomeAssistantBaseEntity> entities = mapper.readValue(response.get(), new TypeReference<>() {
                });
                return Optional.of(entities);
            } catch (JsonProcessingException e) {
                LOG.warning("Error parsing response: " + e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }


    public boolean isConnectionSuccessful() {
        Optional<String> response = sendGetRequest("/api");
        return response.isPresent();
    }

    public void setEntityState(String domain, String service, String entity_id, String setting) {
        if (setting.isBlank()) {
            sendPostRequest("/api/services/" + domain + "/" + service, "'{\"entity_id\": \"" + entity_id + "\"}'");
            return;
        }
        sendPostRequest("/api/services/" + domain + "/" + service, "'{\"entity_id\": \"" + entity_id + "\", " + setting + "}'");
    }

    public void sendPostRequest(String path, String json) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HomeAssistantUrl + path))
                .header("Authorization", "Bearer " + Token)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            LOG.info("Sending request to: " + request.uri());
            client.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (Exception e) {
            LOG.warning("Error sending request: " + e.getMessage());
        }
    }

    private Optional<String> sendGetRequest(String path) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HomeAssistantUrl + path))
                .header("Authorization", "Bearer " + Token)
                .GET()
                .build();

        try {
            LOG.info("Sending request to: " + request.uri());
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return Optional.of(response.body());

        } catch (Exception e) {
            LOG.warning("Error sending request: " + e.getMessage());
        }
        return Optional.empty();
    }


}
