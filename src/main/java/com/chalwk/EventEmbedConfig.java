// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk;

import java.awt.*;
import java.util.List;
import java.util.Map;

public record EventEmbedConfig(boolean enabled, String title, Color color, String description,
                               List<FieldConfig> fields,
                               Map<Integer, String> typeDescriptions,
                               String channelKey) {

    public record FieldConfig(String name, String value, boolean inline) {
    }
}