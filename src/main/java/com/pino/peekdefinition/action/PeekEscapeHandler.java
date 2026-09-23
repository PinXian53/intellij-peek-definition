package com.pino.peekdefinition.action;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.pino.peekdefinition.model.PeekSession;
import com.pino.peekdefinition.ui.PeekInlayManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Esc closes Peeks: inside a viewer only that Peek, in the host every Peek it has.
 * Only once the editor's own Esc behaviour (clear selection, drop extra carets) has nothing left to do.
 */
public final class PeekEscapeHandler extends EditorActionHandler {

    private final EditorActionHandler original;

    public PeekEscapeHandler(EditorActionHandler original) {
        this.original = original;
    }

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
        return shouldClose(editor) || original.isEnabled(editor, caret, dataContext);
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        if (!shouldClose(editor)) {
            original.execute(editor, caret, dataContext);
        } else if (PeekSession.ofViewer(editor) != null) {
            PeekInlayManager.close(editor);
        } else {
            PeekInlayManager.closeAll(editor);
        }
    }

    private static boolean shouldClose(Editor editor) {
        return PeekSession.owning(editor) != null
                && LookupManager.getActiveLookup(editor) == null
                && !editor.getSelectionModel().hasSelection()
                && editor.getCaretModel().getCaretCount() == 1;
    }
}
