package com.pino.peekdefinition.resolve;

import com.pino.peekdefinition.model.PeekTarget;
import org.jetbrains.annotations.NotNull;

public sealed interface PeekResolveResult {

    record Found(@NotNull PeekTarget target) implements PeekResolveResult {
    }

    /** Nothing to peek; {@code message} is shown as an editor hint. */
    record Failed(@NotNull String message) implements PeekResolveResult {
    }
}
