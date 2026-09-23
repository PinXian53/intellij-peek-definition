package com.pino.peekdefinition.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.pino.peekdefinition.model.PeekTarget;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

/**
 * Creates the read-only editor inside a Peek and switches its view options.
 * Callers must release it via {@link EditorFactory#releaseEditor}.
 */
final class PeekViewerFactory {

    /** Marks the definition's lines in whole-file mode. Falls back to the identifier-under-caret background. */
    static final TextAttributesKey TARGET_RANGE = TextAttributesKey.createTextAttributesKey(
            "PEEK_DEFINITION_TARGET_RANGE", EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES);

    /**
     * Content background: the editor background nudged a little lighter (dark themes) or darker (light themes),
     * so the Peek reads as separate from the surrounding code. Resolved on each paint to follow scheme changes.
     */
    static final JBColor CONTENT_BACKGROUND = JBColor.lazy(() -> {
        Color background = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
        return ColorUtil.shift(background, ColorUtil.isDark(background) ? 1.25 : 0.97);
    });

    /** The definition's range, kept up to date as the file is edited. */
    private static final Key<RangeMarker> TARGET = Key.create("com.pino.peekdefinition.target");
    private static final Key<RangeHighlighter> TARGET_HIGHLIGHTER = Key.create("com.pino.peekdefinition.highlighter");

    private PeekViewerFactory() {
    }

    static @NotNull EditorEx create(@NotNull Project project, @NotNull Document document, @NotNull PeekTarget target,
                                    boolean methodOnly, boolean lineNumbers) {
        // The real document, not a copy: edits elsewhere show up live and Ctrl+Click keeps working.
        EditorEx viewer = (EditorEx) EditorFactory.getInstance().createViewer(document, project, EditorKind.PREVIEW);
        viewer.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, target.file()));
        viewer.setBorder(JBUI.Borders.emptyLeft(4));
        viewer.setBackgroundColor(CONTENT_BACKGROUND);
        viewer.setHorizontalScrollbarVisible(true);
        viewer.setVerticalScrollbarVisible(true);
        viewer.getScrollingModel().disableAnimation();

        EditorSettings settings = viewer.getSettings();
        settings.setLineMarkerAreaShown(false);
        settings.setFoldingOutlineShown(false);
        settings.setAdditionalLinesCount(0);
        settings.setAdditionalColumnsCount(1);
        settings.setCaretRowShown(false);
        settings.setRightMarginShown(false);
        settings.setUseSoftWraps(false);
        settings.setAnimatedScrolling(false);

        viewer.putUserData(TARGET, document.createRangeMarker(target.range()));
        setLineNumbersShown(viewer, lineNumbers);
        setMethodOnly(viewer, methodOnly);
        viewer.getCaretModel().moveToOffset(target.navigationOffset());
        return viewer;
    }

    /**
     * Method only: folds away everything outside the definition's lines, so only the definition is visible.
     * Whole file: shows the whole file and marks the definition's lines instead.
     */
    static void setMethodOnly(@NotNull EditorEx viewer, boolean methodOnly) {
        RangeMarker target = viewer.getUserData(TARGET);
        if (target == null || !target.isValid()) {
            return;
        }
        Document document = viewer.getDocument();
        int firstLineStart = document.getLineStartOffset(document.getLineNumber(target.getStartOffset()));
        int lastLineEnd = document.getLineEndOffset(document.getLineNumber(target.getEndOffset()));

        FoldingModelEx folding = viewer.getFoldingModel();
        folding.runBatchFoldingOperation(() -> {
            for (FoldRegion region : folding.getAllFoldRegions()) {
                folding.removeFoldRegion(region);
            }
            if (methodOnly) {
                hide(folding, 0, firstLineStart);
                hide(folding, lastLineEnd, document.getTextLength());
            }
        });

        RangeHighlighter highlighter = viewer.getUserData(TARGET_HIGHLIGHTER);
        if (highlighter != null) {
            viewer.getMarkupModel().removeHighlighter(highlighter);
            viewer.putUserData(TARGET_HIGHLIGHTER, null);
        }
        if (!methodOnly) {
            viewer.putUserData(TARGET_HIGHLIGHTER, viewer.getMarkupModel().addRangeHighlighter(TARGET_RANGE,
                    target.getStartOffset(), target.getEndOffset(),
                    HighlighterLayer.CARET_ROW - 1, HighlighterTargetArea.LINES_IN_RANGE));
        }
    }

    static void setLineNumbersShown(@NotNull EditorEx viewer, boolean shown) {
        viewer.getSettings().setLineNumbersShown(shown);
    }

    /** Number of lines the definition spans, or 1 if it is gone. */
    static int targetLineCount(@NotNull EditorEx viewer) {
        RangeMarker target = viewer.getUserData(TARGET);
        if (target == null || !target.isValid()) {
            return 1;
        }
        Document document = viewer.getDocument();
        return document.getLineNumber(target.getEndOffset()) - document.getLineNumber(target.getStartOffset()) + 1;
    }

    /** Scrolls so the definition's first line is at the top of the viewer. */
    static void revealTarget(@NotNull EditorEx viewer) {
        RangeMarker target = viewer.getUserData(TARGET);
        if (target == null || !target.isValid()) {
            return;
        }
        int startLine = viewer.getDocument().getLineNumber(target.getStartOffset());
        viewer.getScrollingModel().scrollVertically(viewer.logicalPositionToXY(new LogicalPosition(startLine, 0)).y);
    }

    private static void hide(FoldingModelEx folding, int start, int end) {
        if (start >= end) {
            return;
        }
        // Empty placeholder and never expandable: the hidden text leaves no trace and cannot be clicked open.
        FoldRegion region = folding.createFoldRegion(start, end, "", null, true);
        if (region != null) {
            region.setExpanded(false);
        }
    }
}
