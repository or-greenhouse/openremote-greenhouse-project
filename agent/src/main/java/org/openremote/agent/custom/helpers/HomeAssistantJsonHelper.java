package org.openremote.agent.custom.helpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class HomeAssistantJsonHelper {

    private static final String TEST_JSON_FILE = "hass.json";

    public static Optional<String> readTestJsonFile() {
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(TEST_JSON_FILE);
        assert input != null;
        InputStreamReader streamReader = new InputStreamReader(input, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(streamReader);
        StringBuilder output = new StringBuilder();
        try {
            for (String line; (line = reader.readLine()) != null; ) {
                output.append(line);
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.of(output.toString());
    }

    public static String getTypeFromEntityId(String entityId) {
        String[] parts = entityId.split("\\.");
        return parts[0];
    }
}
