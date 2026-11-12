package jsd.toiyeujava.minileetcode.plugin.actions;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Pluggable registry holding the active {@link CheckHandler}.
 * Allows Slice 5 submission logic to cleanly substitute the check strategy
 * without modifying UI action classes or plugin.xml declarations.
 */
public final class CheckHandlerRegistry {

    private static volatile CheckHandler handler = new DefaultCheckHandler();

    private CheckHandlerRegistry() {
    }

    @NotNull
    public static CheckHandler getHandler() {
        return handler;
    }

    public static void setHandler(@NotNull CheckHandler newHandler) {
        handler = Objects.requireNonNull(newHandler, "handler must not be null");
    }

    public static void reset() {
        handler = new DefaultCheckHandler();
    }
}
