package com.pino.peekdefinition.action;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.pino.peekdefinition.model.PeekSession;
import com.pino.peekdefinition.resolve.JavaPeekTargetResolver;
import com.pino.peekdefinition.resolve.PeekResolveResult;
import com.pino.peekdefinition.resolve.PeekTargetResolver;
import com.pino.peekdefinition.ui.PeekInlayManager;
import org.jetbrains.annotations.NotNull;

public final class PeekDefinitionAction extends AnAction {

    static final String INDEXING = "Peek Definition is not available during indexing";

    private final PeekTargetResolver resolver = new JavaPeekTargetResolver();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(project != null && editor != null
                && PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) instanceof PsiJavaFile);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        if (DumbService.isDumb(project)) {
            HintManager.getInstance().showErrorHint(editor, INDEXING);
            return;
        }

        // Invoked inside a Peek: the new definition replaces that Peek, anchored where it was (Spec FR-6).
        PeekSession session = PeekSession.ofViewer(editor);
        boolean fromViewer = session != null;
        Editor host = fromViewer ? session.host() : editor;
        int anchorOffset = fromViewer ? session.inlay().getOffset() : editor.getCaretModel().getOffset();
        int caretOffset = editor.getCaretModel().getOffset();

        ReadAction.nonBlocking(() -> resolver.resolve(editor, caretOffset))
                .inSmartMode(project)
                .expireWhen(() -> editor.isDisposed() || host.isDisposed())
                .finishOnUiThread(ModalityState.defaultModalityState(), result -> {
                    switch (result) {
                        case PeekResolveResult.Found found -> PeekInlayManager.show(host, anchorOffset, found.target(), session);
                        case PeekResolveResult.Failed failed -> HintManager.getInstance().showErrorHint(editor, failed.message());
                    }
                })
                .submit(AppExecutorUtil.getAppExecutorService());
    }
}
