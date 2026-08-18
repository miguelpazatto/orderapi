package com.miguelpazatto.orderapi.core.events;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String name, String email, String phone) {
}
