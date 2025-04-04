package client;

import java.util.HashMap;
import java.util.Map;

public class ClientMap {
    // Mapping locations to server names
    private static final Map<String, String> SERVER_NAME_MAP = new HashMap<>();

    static {
        SERVER_NAME_MAP.put("NYK", "NewYork");
        SERVER_NAME_MAP.put("LON", "London");
        SERVER_NAME_MAP.put("TOK", "Tokyo");
    }

    // Extract location from userID (adminID or buyerID)
    public static String getLocation(String userID) {
        if (userID.length() < 3) {
            return null; // Invalid ID
        }
        String loc = userID.substring(0, 3); // Extract first 3 letters (NYK, LON, TOK)
        return SERVER_NAME_MAP.getOrDefault(loc, null); // Convert to full name
    }
}