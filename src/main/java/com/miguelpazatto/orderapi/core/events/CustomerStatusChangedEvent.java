package com.miguelpazatto.orderapi.core.events;

import java.util.UUID;

public record CustomerStatusChangedEvent(UUID userId, boolean isActive) {
}
