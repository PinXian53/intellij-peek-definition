package com.pino.peekdefinition.model;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One Peek embedded in a host editor. A host can hold several (one per line, Spec FR-4); each is
 * also registered under its viewer, so an action invoked from inside the viewer finds its session.
 */
public final class PeekSession {

    private static final Key<PeekSession> VIEWER_KEY = Key.create("com.pino.peekdefinition.PeekSession.viewer");
    private static final Key<List<PeekSession>> HOST_KEY = Key.create("com.pino.peekdefinition.PeekSession.host");

    private final Editor host;
    private final EditorEx viewer;
    private final Inlay<?> inlay;
    private final Disposable disposable;
    private boolean disposed;

    public PeekSession(@NotNull Editor host, @NotNull EditorEx viewer, @NotNull Inlay<?> inlay,
                       @NotNull Disposable disposable) {
        this.host = host;
        this.viewer = viewer;
        this.inlay = inlay;
        this.disposable = disposable;

        List<PeekSession> sessions = host.getUserData(HOST_KEY);
        if (sessions == null) {
            sessions = new ArrayList<>();
            host.putUserData(HOST_KEY, sessions);
        }
        sessions.add(this);
        viewer.putUserData(VIEWER_KEY, this);

        List<PeekSession> registered = sessions;
        Disposer.register(disposable, () -> {
            disposed = true;
            registered.remove(this);
            if (registered.isEmpty() && host.getUserData(HOST_KEY) == registered) {
                host.putUserData(HOST_KEY, null);
            }
            viewer.putUserData(VIEWER_KEY, null);
        });
    }

    /** The session whose viewer is {@code editor}, if any. */
    public static @Nullable PeekSession ofViewer(@NotNull Editor editor) {
        return editor.getUserData(VIEWER_KEY);
    }

    /** The sessions {@code editor} hosts, oldest first. */
    public static @NotNull List<PeekSession> ofHost(@NotNull Editor editor) {
        List<PeekSession> sessions = editor.getUserData(HOST_KEY);
        return sessions == null ? List.of() : List.copyOf(sessions);
    }

    /**
     * The session an action on {@code editor} applies to: the viewer's own session,
     * or else the most recently opened Peek of the host.
     */
    public static @Nullable PeekSession owning(@NotNull Editor editor) {
        PeekSession session = ofViewer(editor);
        if (session != null) {
            return session;
        }
        List<PeekSession> sessions = ofHost(editor);
        return sessions.isEmpty() ? null : sessions.get(sessions.size() - 1);
    }

    public @NotNull Editor host() {
        return host;
    }

    public @NotNull EditorEx viewer() {
        return viewer;
    }

    public @NotNull Inlay<?> inlay() {
        return inlay;
    }

    /** Host line the Peek is shown below, or -1 once its inlay is gone. */
    public int anchorLine() {
        return inlay.isValid() ? host.getDocument().getLineNumber(inlay.getOffset()) : -1;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /** Removes the inlay and releases the viewer editor. Safe to call more than once. */
    public void close() {
        Disposer.dispose(disposable);
    }
}
