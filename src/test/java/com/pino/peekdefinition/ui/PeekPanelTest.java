package com.pino.peekdefinition.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.pino.peekdefinition.model.PeekTarget;

import javax.swing.JComponent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PeekPanelTest extends BasePlatformTestCase {

    private static final String SOURCE = """
            class A {
                void a() {}
                void b() {
                    int x = 1;
                }
            }
            """;

    private EditorEx viewer;
    private PeekTarget target;
    private final AtomicInteger promoted = new AtomicInteger();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        PsiFile file = myFixture.configureByText("A.java", SOURCE);
        int start = SOURCE.indexOf("    void b()");
        int end = SOURCE.indexOf("    }", start) + 5;
        target = new PeekTarget(SmartPointerManager.createPointer((PsiElement) file), file.getVirtualFile(),
                new TextRange(start, end), start, "b()", "app.main", AllIcons.Nodes.Module);
        viewer = PeekViewerFactory.create(getProject(), myFixture.getEditor().getDocument(), target, true, false);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            EditorFactory.getInstance().releaseEditor(viewer);
            // View options are stored app-wide; put the defaults back for the next test.
            PeekSettings.setMethodOnly(true);
            PeekSettings.setLineNumbersShown(false);
        } finally {
            super.tearDown();
        }
    }

    public void testDoubleClickOnTitlePromotes() {
        JBLabel title = UIUtil.findComponentOfType(panel(), JBLabel.class);

        press(title, 2);

        assertEquals(1, promoted.get());
    }

    public void testDoubleClickOnHeaderBackgroundPromotes() {
        JBLabel title = UIUtil.findComponentOfType(panel(), JBLabel.class);

        press((JComponent) title.getParent(), 2);

        assertEquals(1, promoted.get());
    }

    public void testSingleClickOnTitleTogglesCollapse() {
        PeekPanel panel = panel();
        JBLabel title = UIUtil.findComponentOfType(panel, JBLabel.class);

        press(title, 1);

        assertTrue(panel.isCollapsed());
        assertFalse(viewer.getComponent().isVisible());
        assertEquals(0, promoted.get());

        press(title, 1);

        assertFalse(panel.isCollapsed());
        assertTrue(viewer.getComponent().isVisible());
    }

    public void testSingleClickOnHeaderBackgroundTogglesCollapse() {
        PeekPanel panel = panel();

        press((JComponent) UIUtil.findComponentOfType(panel, JBLabel.class).getParent(), 1);

        assertTrue(panel.isCollapsed());
    }

    public void testCollapsedPanelIsOnlyAsTallAsItsHeader() {
        PeekPanel panel = panel();
        int expanded = panel.getPreferredSize().height;

        panel.setCollapsed(true);

        assertTrue(panel.getPreferredSize().height < expanded);
    }

    public void testHeaderShowsSignatureAndLocation() {
        List<String> texts = UIUtil.findComponentsOfType(panel(), JBLabel.class).stream().map(JBLabel::getText).toList();

        assertEquals(List.of("b()", "app.main"), texts);
    }

    public void testOutlineIsThickerThanAHairline() {
        PeekPanel panel = panel();

        assertEquals(JBUI.scale(PeekPanel.OUTLINE_THICKNESS), panel.getInsets().top);
        assertEquals(JBUI.scale(PeekPanel.OUTLINE_THICKNESS), panel.getInsets().left);
    }

    public void testHeaderHasCollapseMoreAndCloseButtons() {
        List<String> tooltips = UIUtil.findComponentsOfType(panel(), InplaceButton.class).stream()
                .map(InplaceButton::getToolTipText)
                .toList();

        assertEquals(List.of("Collapse", "More", "Close (Esc)"), tooltips);
    }

    public void testMoreMenuEntries() {
        PeekPanel panel = panel();

        assertEquals(List.of("Open in Editor", "Collapse", "Method Only", "Whole File", "Show Line Numbers", "Close"),
                menuTexts(panel));

        panel.setCollapsed(true);

        assertEquals("Expand", menuTexts(panel).get(1));
    }

    public void testWholeFileShowsEverythingAndIsRemembered() {
        PeekPanel panel = panel();
        assertTrue("starts with only the method", viewer.getFoldingModel().isOffsetCollapsed(0));

        panel.setMethodOnly(false);

        assertFalse(panel.isMethodOnly());
        assertEmpty(viewer.getFoldingModel().getAllFoldRegions());
        assertFalse(PeekSettings.isMethodOnly());

        panel.setMethodOnly(true);

        assertTrue(viewer.getFoldingModel().isOffsetCollapsed(0));
        assertTrue(PeekSettings.isMethodOnly());
    }

    public void testWholeFileGetsTallerThanAShortMethod() {
        PeekPanel panel = panel();
        int methodOnly = panel.getPreferredSize().height;

        panel.setMethodOnly(false);

        assertEquals("3-line method, whole file shows at least 5 lines",
                methodOnly + 2 * viewer.getLineHeight(), panel.getPreferredSize().height);
    }

    public void testLineNumbersToggleAndAreRemembered() {
        PeekPanel panel = panel();
        assertFalse(viewer.getSettings().isLineNumbersShown());

        panel.setLineNumbersShown(true);

        assertTrue(viewer.getSettings().isLineNumbersShown());
        assertTrue(PeekSettings.isLineNumbersShown());
    }

    private static List<String> menuTexts(PeekPanel panel) {
        return Arrays.stream(panel.menuActions().getChildActionsOrStubs())
                .filter(action -> !(action instanceof Separator))
                .map(action -> action.getTemplatePresentation().getText())
                .toList();
    }

    private PeekPanel panel() {
        return new PeekPanel(viewer, target, true, false, () -> {}, promoted::incrementAndGet);
    }

    private static void press(JComponent target, int clickCount) {
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, 5, 5, clickCount, false, MouseEvent.BUTTON1));
    }
}
