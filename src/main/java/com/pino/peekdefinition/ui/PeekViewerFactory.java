package com.pino.peekdefinition.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import com.pino.peekdefinition.model.PeekTarget;
import org.jetbrains.annotations.NotNull;

/** Creates the read-only editor inside a Peek. Callers must release it via {@link EditorFactory#releaseEditor}. */
final class PeekViewerFactory {

    /** Marks the definition's lines. Falls back to the scheme's identifier-under-caret background. */
    static final TextAttributesKey TARGET_RANGE = TextAttributesKey.createTextAttributesKey(
            "PEEK_DEFINITION_TARGET_RANGE", EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES);

    private PeekViewerFactory() {
    }

    static @NotNull EditorEx create(@NotNull Project project, @NotNull Document document, @NotNull PeekTarget target) {
        // The real document, not a copy: edits elsewhere show up live and Ctrl+Click keeps working.
        EditorEx viewer = (EditorEx) EditorFactory.getInstance().createViewer(document, project, EditorKind.PREVIEW);
        viewer.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, target.file()));
        viewer.setBorder(JBUI.Borders.empty());
        viewer.setHorizontalScrollbarVisible(true);
        viewer.setVerticalScrollbarVisible(true);
        viewer.getScrollingModel().disableAnimation();

        EditorSettings settings = viewer.getSettings();
        settings.setLineNumbersShown(true);
        settings.setLineMarkerAreaShown(false);
        settings.setFoldingOutlineShown(false);
        settings.setAdditionalLinesCount(0);
        settings.setAdditionalColumnsCount(1);
        settings.setCaretRowShown(false);
        settings.setRightMarginShown(false);
        settings.setUseSoftWraps(false);
        settings.setAnimatedScrolling(false);

        viewer.getMarkupModel().addRangeHighlighter(TARGET_RANGE,
                target.range().getStartOffset(), target.range().getEndOffset(),
                HighlighterLayer.CARET_ROW - 1, HighlighterTargetArea.LINES_IN_RANGE);
        viewer.getCaretModel().moveToOffset(target.navigationOffset());
        return viewer;
    }
}
