package com.pino.peekdefinition.resolve;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

/** Language-specific lookup of the definition under the caret. Called inside a read action. */
public interface PeekTargetResolver {

    @NotNull PeekResolveResult resolve(@NotNull Editor editor, int offset);
}
