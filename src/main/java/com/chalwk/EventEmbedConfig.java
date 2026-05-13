// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk;

import java.awt.*;
import java.util.List;
import java.util.Map;

// Holds everything needed to build a Discord embed for a specific game event type.
public record EventEmbedConfig(boolean enabled, String title, Color color, String description,
                               List<FieldConfig> fields,
                               Map<Integer, String> typeDescriptions,
                               String channelKey) {

    // a single field inside the embed, with a name, value, and whether it sits inline with others
    public record FieldConfig(String name, String value, boolean inline) {
    }
}