package com.pino.peekdefinition.resolve;

import com.pino.peekdefinition.model.PeekTarget;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public sealed interface PeekResolveResult {

    /**
     * @param candidates for an interface or abstract method: the declaration itself first, then its
     *                   implementations, to switch between in the Peek; empty otherwise
     */
    record Found(@NotNull PeekTarget target, @NotNull List<PeekTarget> candidates) implements PeekResolveResult {

        public Found(@NotNull PeekTarget target) {
            this(target, List.of());
        }
    }

    /** Nothing to peek; {@code message} is shown as an editor hint. */
    record Failed(@NotNull String message) implements PeekResolveResult {
    }
}
