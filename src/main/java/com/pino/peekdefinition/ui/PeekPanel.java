package com.pino.peekdefinition.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Header (title, collapse, close) above the viewer editor. Its preferred height drives the inlay height. */
final class PeekPanel extends JPanel {

    static final int MAX_VISIBLE_LINES = 15;
    static final int MIN_VISIBLE_LINES = 5;
    /** Header title is this many points larger than the default label font. */
    static final float TITLE_FONT_INCREASE = 2f;
    /** Header icons are drawn this much larger than the standard 16px action icons. */
    static final float ICON_SCALE = 1.25f;

    private final EditorEx viewer;
    private final JPanel header;
    private final int visibleLines;
    private final InplaceButton collapseButton;
    private boolean collapsed;

    PeekPanel(@NotNull EditorEx viewer, @NotNull String title, int targetLines,
              @NotNull Runnable onClose, @NotNull Runnable onPromote) {
        super(new BorderLayout());
        this.viewer = viewer;
        this.visibleLines = Math.max(MIN_VISIBLE_LINES, Math.min(MAX_VISIBLE_LINES, targetLines));

        collapseButton = new InplaceButton(collapseIcon(), e -> setCollapsed(!collapsed));
        InplaceButton closeButton = new InplaceButton(
                iconButton("Close (Esc)", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered), e -> onClose.run());

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(closeButton);

        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(JBFont.label().biggerOn(TITLE_FONT_INCREASE));
        titleLabel.setToolTipText("Click to collapse / expand, double-click to open in editor");

        header = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        header.setBorder(JBUI.Borders.empty(4, 6));
        header.add(collapseButton, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(buttons, BorderLayout.EAST);
        // The label needs its own listener: its tooltip makes it a mouse target, so clicks never reach the header.
        // Pressed rather than clicked: a click is dropped if the mouse moves between press and release.
        // Single click toggles right away instead of waiting out the double-click interval; on a double click
        // the first press collapses and the second promotes, which closes the Peek anyway.
        MouseAdapter headerClicks = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (e.getClickCount() == 1) {
                    setCollapsed(!collapsed);
                } else if (e.getClickCount() == 2) {
                    onPromote.run();
                }
            }
        };
        header.addMouseListener(headerClicks);
        titleLabel.addMouseListener(headerClicks);

        setBorder(JBUI.Borders.customLine(JBColor.border(), 1));
        add(header, BorderLayout.NORTH);
        add(viewer.getComponent(), BorderLayout.CENTER);
    }

    void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        viewer.getComponent().setVisible(!collapsed);
        collapseButton.setIcons(collapseIcon());
        // The inlay container is a validate root; revalidating makes it re-measure and resize the inlay.
        revalidate();
        repaint();
    }

    boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        int height = header.getPreferredSize().height + getInsets().top + getInsets().bottom;
        if (!collapsed) {
            height += visibleLines * viewer.getLineHeight()
                    + viewer.getScrollPane().getHorizontalScrollBar().getPreferredSize().height;
        }
        return new Dimension(size.width, height);
    }

    private IconButton collapseIcon() {
        return collapsed
                ? iconButton("Expand", AllIcons.General.ArrowRight, AllIcons.General.ArrowRight)
                : iconButton("Collapse", AllIcons.General.ArrowDown, AllIcons.General.ArrowDown);
    }

    private static IconButton iconButton(String tooltip, Icon icon, Icon hovered) {
        return new IconButton(tooltip,
                IconUtil.scale(icon, null, ICON_SCALE), IconUtil.scale(hovered, null, ICON_SCALE));
    }
}
