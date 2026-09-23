package com.pino.peekdefinition.resolve;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.pino.peekdefinition.model.PeekTarget;
import org.jetbrains.annotations.NotNull;

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

        return new PeekResolveResult.Found(new PeekTarget(
                SmartPointerManager.createPointer(definition),
                virtualFile,
                range,
                definition.getTextOffset(),
                virtualFile.getName() + " — " + describe(definition)));
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

    private static String describe(PsiElement element) {
        if (element instanceof PsiMethod method) {
            String signature = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                    PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                    PsiFormatUtilBase.SHOW_TYPE);
            return qualify(method, signature);
        }
        if (element instanceof PsiClass psiClass) {
            String qualifiedName = psiClass.getQualifiedName();
            return qualifiedName != null ? qualifiedName : String.valueOf(psiClass.getName());
        }
        if (element instanceof PsiMember member) {
            return qualify(member, String.valueOf(member.getName()));
        }
        if (element instanceof PsiNamedElement named) {
            return String.valueOf(named.getName());
        }
        return "";
    }

    private static String qualify(PsiMember member, String name) {
        PsiClass owner = member.getContainingClass();
        return owner == null || owner.getName() == null ? name : owner.getName() + "." + name;
    }
}
