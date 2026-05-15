// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// just a thin wrapper around SLF4J so I don't have to mess with LoggerFactory everywhere
public final class LoggerUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerUtil.class);

    private LoggerUtil() {

    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void info(String format, Object... arguments) {
        LOGGER.info(format, arguments);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    } // not currently used

    public static void warn(String format, Object... arguments) {
        LOGGER.warn(format, arguments);
    }

    public static void error(String message) {
        LOGGER.error(message);
    } // not currently used

    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    public static void error(String format, Object... arguments) {
        LOGGER.error(format, arguments);
    }

    public static void debug(String message) {
        LOGGER.debug(message);
    } // not currently used

    public static void debug(String format, Object... arguments) {
        LOGGER.debug(format, arguments);
    }
}