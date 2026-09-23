package com.pino.peekdefinition.model;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * A resolved definition, captured inside a read action so it can be handed to the UI thread.
 *
 * @param pointer          survives PSI reparses; the UI must not hold the raw {@link PsiElement}
 * @param file             file whose whole document the Peek Viewer shows
 * @param range            the definition's full range (JavaDoc + annotations + signature + body)
 * @param navigationOffset where the viewer caret goes, normally the name identifier
 * @param title            header text, e.g. {@code getUser(Long)}
 * @param icon             the definition's own icon (method, class, field ...), shown before the title
 * @param location         module or library the file belongs to, shown in the header; null when unknown
 * @param locationIcon     icon shown next to {@code location}
 */
public record PeekTarget(
        @NotNull SmartPsiElementPointer<PsiElement> pointer,
        @NotNull VirtualFile file,
        @NotNull TextRange range,
        int navigationOffset,
        @NotNull String title,
        @Nullable Icon icon,
        @Nullable String location,
        @Nullable Icon locationIcon
) {
}
