package com.pino.peekdefinition.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ComponentInlayAlignment;
import com.intellij.openapi.editor.ComponentInlayKt;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayProperties;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.pino.peekdefinition.lifecycle.PeekProjectService;
import com.pino.peekdefinition.model.PeekSession;
import com.pino.peekdefinition.model.PeekTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/** Opens, replaces and closes the Peeks embedded in a host editor. EDT only. */
public final class PeekInlayManager {

    public static final String SOURCE_NOT_AVAILABLE = "Source not available";

    private PeekInlayManager() {
    }

    /** {@link #show(Editor, int, PeekTarget, PeekSession)} without an explicit Peek to replace. */
    public static @Nullable PeekSession show(@NotNull Editor host, int anchorOffset, @NotNull PeekTarget target) {
        return show(host, anchorOffset, target, null);
    }

    /**
     * Shows {@code target} below the line containing {@code anchorOffset} in {@code host}. Peeks on other
     * lines stay open (Spec FR-4); {@code replacing}, or else a Peek already on that line, is closed first.
     */
    public static @Nullable PeekSession show(@NotNull Editor host, int anchorOffset, @NotNull PeekTarget target,
                                             @Nullable PeekSession replacing) {
        Project project = host.getProject();
        if (project == null || host.isDisposed()) {
            return null;
        }
        Document document = FileDocumentManager.getInstance().getDocument(target.file());
        if (document == null) {
            HintManager.getInstance().showErrorHint(host, SOURCE_NOT_AVAILABLE);
            return null;
        }

        int anchorLine = host.getDocument().getLineNumber(anchorOffset);
        PeekSession previous = replacing != null ? replacing : PeekSession.ofHost(host).stream()
                .filter(session -> session.anchorLine() == anchorLine)
                .findFirst()
                .orElse(null);
        if (previous != null) {
            previous.close();
        }

        Disposable disposable = Disposer.newDisposable("PeekDefinition");
        Disposer.register(PeekProjectService.getInstance(project), disposable);

        EditorEx viewer = PeekViewerFactory.create(project, document, target);
        Disposer.register(disposable, () -> EditorFactory.getInstance().releaseEditor(viewer));

        int targetLines = document.getLineNumber(target.range().getEndOffset())
                - document.getLineNumber(target.range().getStartOffset()) + 1;
        PeekPanel panel = new PeekPanel(viewer, target.title(), targetLines,
                () -> close(viewer), () -> promote(viewer));

        int lineEnd = host.getDocument().getLineEndOffset(anchorLine);
        Inlay<?> inlay = ComponentInlayKt.addComponentInlay(host, lineEnd,
                new InlayProperties().relatesToPrecedingText(true).showAbove(false),
                panel, ComponentInlayAlignment.FIT_VIEWPORT_WIDTH);
        if (inlay == null) {
            Disposer.dispose(disposable);
            return null;
        }
        // Registered after the viewer, so the inlay (and its component) goes first on dispose.
        Disposer.register(disposable, () -> {
            if (inlay.isValid()) {
                Disposer.dispose(inlay);
            }
        });

        PeekSession session = new PeekSession(host, viewer, inlay, disposable);
        whenFirstSized(viewer.getScrollPane().getViewport(), () -> ApplicationManager.getApplication()
                .invokeLater(() -> reveal(session, target), __ -> session.isDisposed()));
        return session;
    }

    /**
     * Closes the Peek whose viewer is {@code editor}, or else the most recently opened Peek {@code editor}
     * hosts, returning focus to the host editor.
     */
    public static boolean close(@NotNull Editor editor) {
        PeekSession session = PeekSession.owning(editor);
        if (session == null) {
            return false;
        }
        boolean hadFocus = session.viewer().getContentComponent().hasFocus();
        session.close();
        Editor host = session.host();
        if (hadFocus && !host.isDisposed() && host.getProject() != null) {
            IdeFocusManager.getInstance(host.getProject()).requestFocus(host.getContentComponent(), true);
        }
        return true;
    }

    /** Closes every Peek {@code host} has. */
    public static void closeAll(@NotNull Editor host) {
        PeekSession.ofHost(host).forEach(PeekSession::close);
    }

    /** Opens the peeked file in a regular editor tab at the viewer caret and closes the Peek. */
    public static void promote(@NotNull Editor editor) {
        PeekSession session = PeekSession.owning(editor);
        Project project = editor.getProject();
        if (session == null || project == null) {
            return;
        }
        var file = FileDocumentManager.getInstance().getFile(session.viewer().getDocument());
        int offset = session.viewer().getCaretModel().getOffset();
        session.close();
        if (file != null) {
            FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, offset), true);
        }
    }

    /**
     * The inlay container lays the Peek out lazily, and until then the viewer's viewport has no height,
     * so any scroll would be clamped to the top of the file.
     */
    private static void whenFirstSized(JComponent component, Runnable action) {
        if (component.getHeight() > 0) {
            action.run();
            return;
        }
        component.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (component.getHeight() > 0) {
                    component.removeComponentListener(this);
                    action.run();
                }
            }
        });
    }

    private static void reveal(PeekSession session, PeekTarget target) {
        EditorEx viewer = session.viewer();
        int startLine = viewer.getDocument().getLineNumber(target.range().getStartOffset());
        viewer.getScrollingModel().scrollVertically(viewer.logicalPositionToXY(new LogicalPosition(startLine, 0)).y);

        Editor host = session.host();
        Rectangle bounds = session.inlay().getBounds();
        if (bounds != null) {
            // Minimal scroll: the host only moves when the Peek would otherwise be off-screen.
            host.getContentComponent().scrollRectToVisible(bounds);
        }
        if (host.getProject() != null) {
            IdeFocusManager.getInstance(host.getProject()).requestFocus(viewer.getContentComponent(), true);
        }
    }
}
