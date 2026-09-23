package com.pino.peekdefinition.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.BoxLayout;
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
                new IconButton("Close (Esc)", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered), e -> onClose.run());

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(closeButton);

        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setToolTipText("Double-click to open in editor");

        header = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        header.setBorder(JBUI.Borders.empty(2, 4));
        header.add(collapseButton, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(buttons, BorderLayout.EAST);
        // The label needs its own listener: its tooltip makes it a mouse target, so clicks never reach the header.
        // Pressed rather than clicked: a click is dropped if the mouse moves between press and release.
        MouseAdapter promoteOnDoubleClick = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    onPromote.run();
                }
            }
        };
        header.addMouseListener(promoteOnDoubleClick);
        titleLabel.addMouseListener(promoteOnDoubleClick);

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
                ? new IconButton("Expand", AllIcons.General.ArrowRight)
                : new IconButton("Collapse", AllIcons.General.ArrowDown);
    }
}
