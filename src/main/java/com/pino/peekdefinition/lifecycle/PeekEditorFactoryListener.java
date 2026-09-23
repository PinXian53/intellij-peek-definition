package com.pino.peekdefinition.lifecycle;

import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.pino.peekdefinition.ui.PeekInlayManager;
import org.jetbrains.annotations.NotNull;

/** Closes the Peeks of a host editor when the editor itself goes away (tab closed, file deleted, ...). */
public final class PeekEditorFactoryListener implements EditorFactoryListener {

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        PeekInlayManager.closeAll(event.getEditor());
    }
}
