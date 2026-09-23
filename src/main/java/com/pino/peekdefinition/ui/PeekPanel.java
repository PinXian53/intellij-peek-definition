package com.pino.peekdefinition.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.pino.peekdefinition.model.PeekTarget;
import org.jetbrains.annotations.NotNull;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Header above the viewer editor, laid out like Quick Definition's (signature on the left, module or library
 * and a "more" menu on the right) plus collapse and close buttons. Its preferred height drives the inlay height.
 */
final class PeekPanel extends JPanel {

    static final int MAX_VISIBLE_LINES = 15;
    /** In whole-file mode a short definition still gets some surrounding code. */
    static final int MIN_WHOLE_FILE_LINES = 5;
    /** Header title is this many points larger than the default label font. */
    static final float TITLE_FONT_INCREASE = 2f;
    /** Header icons are drawn this much larger than the standard 16px action icons. */
    static final float ICON_SCALE = 1.25f;
    /** Outline width, so the Peek stands out from the surrounding code that shares its background. */
    static final int OUTLINE_THICKNESS = 2;
    /** The theme's focus-ring blue; resolved on each paint so it follows theme switches. */
    static final JBColor OUTLINE_COLOR = JBColor.lazy(JBUI.CurrentTheme.Focus::focusColor);

    private final EditorEx viewer;
    private final JPanel header;
    private final InplaceButton collapseButton;
    private final Runnable onClose;
    private final Runnable onPromote;
    private boolean collapsed;
    private boolean methodOnly;
    private boolean lineNumbersShown;

    PeekPanel(@NotNull EditorEx viewer, @NotNull PeekTarget target, boolean methodOnly, boolean lineNumbersShown,
              @NotNull Runnable onClose, @NotNull Runnable onPromote) {
        super(new BorderLayout());
        this.viewer = viewer;
        this.methodOnly = methodOnly;
        this.lineNumbersShown = lineNumbersShown;
        this.onClose = onClose;
        this.onPromote = onPromote;

        collapseButton = new InplaceButton(collapseIcon(), e -> setCollapsed(!collapsed));

        JBLabel titleLabel = new JBLabel(target.title());
        titleLabel.setFont(JBFont.label().biggerOn(TITLE_FONT_INCREASE));
        titleLabel.setToolTipText(target.file().getName()
                + " — click to collapse / expand, double-click to open in editor");

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        if (target.location() != null) {
            JBLabel locationLabel = new JBLabel(target.location(), target.locationIcon(), JBLabel.LEFT);
            locationLabel.setForeground(NamedColorUtil.getInactiveTextColor());
            locationLabel.setBorder(JBUI.Borders.emptyRight(8));
            right.add(locationLabel);
        }
        // InplaceButton reports the MouseEvent, not itself, as the ActionEvent source, so keep a reference.
        InplaceButton[] moreButton = new InplaceButton[1];
        moreButton[0] = new InplaceButton(iconButton("More", AllIcons.Actions.More, AllIcons.Actions.More),
                e -> showMenu(moreButton[0]));
        right.add(moreButton[0]);
        right.add(new InplaceButton(
                iconButton("Close (Esc)", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered), e -> onClose.run()));

        header = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        header.setBackground(JBColor.lazy(viewer::getBackgroundColor));
        header.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.empty(4, 6)));
        header.add(collapseButton, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
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

        setBorder(JBUI.Borders.customLine(OUTLINE_COLOR, OUTLINE_THICKNESS));
        add(header, BorderLayout.NORTH);
        add(viewer.getComponent(), BorderLayout.CENTER);
    }

    void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        viewer.getComponent().setVisible(!collapsed);
        collapseButton.setIcons(collapseIcon());
        relayout();
    }

    boolean isCollapsed() {
        return collapsed;
    }

    /** Switches between the definition alone and the whole file, and makes it the default for new Peeks. */
    void setMethodOnly(boolean methodOnly) {
        this.methodOnly = methodOnly;
        PeekSettings.setMethodOnly(methodOnly);
        PeekViewerFactory.setMethodOnly(viewer, methodOnly);
        relayout();
        // Scroll once the new height is laid out, or the scroll position gets clamped.
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!viewer.isDisposed()) {
                PeekViewerFactory.revealTarget(viewer);
            }
        });
    }

    boolean isMethodOnly() {
        return methodOnly;
    }

    /** Shows or hides line numbers, and makes it the default for new Peeks. */
    void setLineNumbersShown(boolean shown) {
        this.lineNumbersShown = shown;
        PeekSettings.setLineNumbersShown(shown);
        PeekViewerFactory.setLineNumbersShown(viewer, shown);
    }

    boolean isLineNumbersShown() {
        return lineNumbersShown;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        int height = header.getPreferredSize().height + getInsets().top + getInsets().bottom;
        if (!collapsed) {
            height += visibleLines() * viewer.getLineHeight()
                    + viewer.getScrollPane().getHorizontalScrollBar().getPreferredSize().height;
        }
        return new Dimension(size.width, height);
    }

    private int visibleLines() {
        int minimum = methodOnly ? 1 : MIN_WHOLE_FILE_LINES;
        return Math.min(MAX_VISIBLE_LINES, Math.max(minimum, PeekViewerFactory.targetLineCount(viewer)));
    }

    /** Built on each click so the entries reflect the current state. */
    DefaultActionGroup menuActions() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(DumbAwareAction.create("Open in Editor", e -> onPromote.run()));
        group.add(DumbAwareAction.create(collapsed ? "Expand" : "Collapse", e -> setCollapsed(!collapsed)));
        group.addSeparator();
        group.add(toggle("Method Only", () -> methodOnly, selected -> {
            if (selected) {
                setMethodOnly(true);
            }
        }));
        group.add(toggle("Whole File", () -> !methodOnly, selected -> {
            if (selected) {
                setMethodOnly(false);
            }
        }));
        group.addSeparator();
        group.add(toggle("Show Line Numbers", () -> lineNumbersShown, this::setLineNumbersShown));
        group.addSeparator();
        group.add(DumbAwareAction.create("Close", e -> onClose.run()));
        return group;
    }

    private static DumbAwareToggleAction toggle(String text, BooleanSupplier selected,
                                                Consumer<Boolean> onChange) {
        return new DumbAwareToggleAction(text) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return selected.getAsBoolean();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                onChange.accept(state);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    private void showMenu(InplaceButton anchor) {
        JBPopupFactory.getInstance()
                .createActionGroupPopup(null, menuActions(), DataManager.getInstance().getDataContext(anchor),
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
                .showUnderneathOf(anchor);
    }

    private void relayout() {
        // The inlay container is a validate root; revalidating makes it re-measure and resize the inlay.
        revalidate();
        repaint();
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
