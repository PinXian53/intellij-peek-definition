package com.pino.peekdefinition.resolve;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.pino.peekdefinition.model.PeekTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public final class JavaPeekTargetResolver implements PeekTargetResolver {

    public static final String NO_DEFINITION = "No definition found";
    public static final String NOT_AVAILABLE = "Definition not available";
    public static final String AT_DEFINITION = "Already at definition";

    @Override
    public @NotNull PeekResolveResult resolve(@NotNull Editor editor, int offset) {
        Project project = editor.getProject();
        if (project == null) {
            return new PeekResolveResult.Failed(NO_DEFINITION);
        }

        // Same flags as Go to Declaration, so overloads, `new Foo()` and static imports behave identically.
        TargetElementUtil util = TargetElementUtil.getInstance();
        PsiElement target = util.findTargetElement(editor, util.getDefinitionSearchFlags(), offset);
        if (target == null) {
            return new PeekResolveResult.Failed(NO_DEFINITION);
        }

        // Library classes navigate to attached sources; Lombok light elements to their annotated field.
        PsiElement definition = target.getNavigationElement();
        PsiFile file = definition.getContainingFile();
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        if (virtualFile == null || !definition.isPhysical()) {
            return new PeekResolveResult.Failed(NOT_AVAILABLE);
        }

        if (isCaretOnName(project, editor, offset, definition)) {
            return new PeekResolveResult.Failed(AT_DEFINITION);
        }

        TextRange range = definition.getTextRange();
        if (range == null) {
            return new PeekResolveResult.Failed(NOT_AVAILABLE);
        }

        Owner owner = ownerOf(project, virtualFile);
        return new PeekResolveResult.Found(new PeekTarget(
                SmartPointerManager.createPointer(definition),
                virtualFile,
                range,
                definition.getTextOffset(),
                describe(definition),
                definition.getIcon(Iconable.ICON_FLAG_VISIBILITY),
                owner == null ? null : owner.name(),
                owner == null ? null : owner.icon()));
    }

    private static boolean isCaretOnName(Project project, Editor editor, int offset, PsiElement definition) {
        PsiFile current = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (current == null || !current.equals(definition.getContainingFile())) {
            return false;
        }
        if (!(definition instanceof PsiNameIdentifierOwner owner) || owner.getNameIdentifier() == null) {
            return false;
        }
        return owner.getNameIdentifier().getTextRange().containsOffset(offset);
    }

    /** Same shape as Quick Definition's header: {@code getUser(Long)}, or the plain name of anything else. */
    private static String describe(PsiElement element) {
        if (element instanceof PsiMethod method) {
            return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                    PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                    PsiFormatUtilBase.SHOW_TYPE);
        }
        if (element instanceof PsiNamedElement named && named.getName() != null) {
            return named.getName();
        }
        return "";
    }

    private record Owner(String name, Icon icon) {
    }

    /** The module a source file belongs to, or the library / JDK a library file comes from. */
    private static @Nullable Owner ownerOf(Project project, VirtualFile file) {
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        Module module = index.getModuleForFile(file);
        if (module != null) {
            return new Owner(module.getName(), AllIcons.Nodes.Module);
        }
        for (OrderEntry entry : index.getOrderEntriesForFile(file)) {
            if (entry instanceof LibraryOrderEntry) {
                return new Owner(entry.getPresentableName(), AllIcons.Nodes.PpLib);
            }
            if (entry instanceof JdkOrderEntry) {
                return new Owner(entry.getPresentableName(), AllIcons.Nodes.PpJdk);
            }
        }
        return null;
    }
}
