package com.cognition.devinops.devin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record DevinSelf(String id, String name, String email, String raw) {

    private static final List<String> ID_KEYS = List.of("id", "user_id", "service_user_id", "sub");
    private static final List<String> NAME_KEYS = List.of("name", "username", "display_name");
    private static final List<String> EMAIL_KEYS = List.of("email", "email_address");

    public static DevinSelf from(JsonNode node) {
        if (node == null) {
            return new DevinSelf(null, null, null, null);
        }
        return new DevinSelf(first(node, ID_KEYS), first(node, NAME_KEYS), first(node, EMAIL_KEYS),
                node.toString());
    }

    private static String first(JsonNode node, List<String> keys) {
        for (String key : keys) {
            if (node.hasNonNull(key) && node.get(key).isValueNode()) {
                return node.get(key).asText();
            }
        }
        return null;
    }
}
