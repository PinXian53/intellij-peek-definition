package com.pino.peekdefinition.ui;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;

import javax.swing.JComponent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;

public class PeekPanelTest extends BasePlatformTestCase {

    private EditorEx viewer;
    private final AtomicInteger promoted = new AtomicInteger();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        viewer = (EditorEx) EditorFactory.getInstance().createViewer(
                EditorFactory.getInstance().createDocument("class A {}"), getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            EditorFactory.getInstance().releaseEditor(viewer);
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

    private PeekPanel panel() {
        return new PeekPanel(viewer, "A.java — A", 1, () -> {}, promoted::incrementAndGet);
    }

    private static void press(JComponent target, int clickCount) {
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, 5, 5, clickCount, false, MouseEvent.BUTTON1));
    }
}
