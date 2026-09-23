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
import com.intellij.ui.SimpleListCellRenderer;
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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
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
    static final float OUTLINE_THICKNESS = 1.5f;
    /** The theme's focus-ring blue; resolved on each paint so it follows theme switches. */
    static final JBColor OUTLINE_COLOR = JBColor.lazy(JBUI.CurrentTheme.Focus::focusColor);
    /** Corner rounding of the outline, in unscaled pixels. */
    static final int CORNER_ARC = 12;
    /** Same color as tool window headers, so the header reads as chrome rather than code. */
    static final JBColor HEADER_BACKGROUND = JBColor.lazy(JBUI.CurrentTheme.ToolWindow::headerBackground);

    private final EditorEx viewer;
    private final JPanel header;
    private final InplaceButton collapseButton;
    private final Runnable onClose;
    private final Runnable onPromote;
    private boolean collapsed;
    private boolean methodOnly;
    private boolean lineNumbersShown;

    /**
     * @param candidates an interface method and its implementations; when there is more than one, the header
     *                   offers them in a dropdown and {@code onSwitch} is called with the one picked
     */
    PeekPanel(@NotNull EditorEx viewer, @NotNull PeekTarget target, @NotNull List<PeekTarget> candidates,
              boolean methodOnly, boolean lineNumbersShown,
              @NotNull Runnable onClose, @NotNull Runnable onPromote, @NotNull Consumer<PeekTarget> onSwitch) {
        super(new BorderLayout());
        this.viewer = viewer;
        this.methodOnly = methodOnly;
        this.lineNumbersShown = lineNumbersShown;
        this.onClose = onClose;
        this.onPromote = onPromote;

        collapseButton = new InplaceButton(collapseIcon(), e -> setCollapsed(!collapsed));

        JBLabel titleLabel = new JBLabel(target.title(), target.icon(), JBLabel.LEFT);
        titleLabel.setFont(JBFont.label().biggerOn(TITLE_FONT_INCREASE));
        titleLabel.setToolTipText(target.file().getName() + " — click to collapse / expand");

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        if (candidates.size() > 1) {
            right.add(candidateChooser(target, candidates, onSwitch));
        }
        if (target.location() != null) {
            JBLabel locationLabel = new JBLabel(target.location(), target.locationIcon(), JBLabel.LEFT);
            locationLabel.setForeground(NamedColorUtil.getInactiveTextColor());
            locationLabel.setBorder(JBUI.Borders.emptyRight(8));
            right.add(locationLabel);
        }
        right.add(new InplaceButton(
                iconButton("Open in Editor", AllIcons.Actions.EditSource, AllIcons.Actions.EditSource),
                e -> onPromote.run()));
        // InplaceButton reports the MouseEvent, not itself, as the ActionEvent source, so keep a reference.
        InplaceButton[] moreButton = new InplaceButton[1];
        moreButton[0] = new InplaceButton(iconButton("More", AllIcons.Actions.More, AllIcons.Actions.More),
                e -> showMenu(moreButton[0]));
        right.add(moreButton[0]);
        right.add(new InplaceButton(
                iconButton("Close (Esc)", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered), e -> onClose.run()));

        header = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        header.setBackground(HEADER_BACKGROUND);
        header.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.empty(4, 6)));
        header.add(collapseButton, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        // The label needs its own listener: its tooltip makes it a mouse target, so clicks never reach the header.
        // Pressed rather than clicked: a click is dropped if the mouse moves between press and release.
        // Every press toggles, so a double click collapses and expands again rather than doing anything else;
        // opening the file has its own button.
        MouseAdapter headerClicks = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    setCollapsed(!collapsed);
                }
            }
        };
        header.addMouseListener(headerClicks);
        titleLabel.addMouseListener(headerClicks);

        // Only reserves room for the outline, which paintChildren draws on top of the children.
        setBorder(JBUI.Borders.empty((int) Math.ceil(OUTLINE_THICKNESS)));
        setOpaque(false);
        add(header, BorderLayout.NORTH);
        add(viewer.getComponent(), BorderLayout.CENTER);
    }

    /**
     * Children paint first, clipped to the rounded inside of the outline, then the outline goes on top, so the
     * square corners of the header and the editor never show outside the curve.
     */
    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D clipped = (Graphics2D) g.create();
        try {
            float inset = JBUI.scale(OUTLINE_THICKNESS);
            float arc = JBUI.scale(CORNER_ARC);
            clipped.clip(new RoundRectangle2D.Float(inset, inset, getWidth() - 2 * inset, getHeight() - 2 * inset,
                    arc - inset, arc - inset));
            super.paintChildren(clipped);
        } finally {
            clipped.dispose();
        }

        Graphics2D outline = (Graphics2D) g.create();
        try {
            float thickness = JBUI.scale(OUTLINE_THICKNESS);
            float arc = JBUI.scale(CORNER_ARC);
            outline.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            outline.setColor(OUTLINE_COLOR);
            outline.setStroke(new BasicStroke(thickness));
            outline.draw(new RoundRectangle2D.Float(thickness / 2, thickness / 2,
                    getWidth() - thickness, getHeight() - thickness, arc, arc));
        } finally {
            outline.dispose();
        }
    }

    /**
     * Forces repaints of a child (the editor's caret, a scrollbar hover) to go through this panel, so they are
     * clipped to the rounded corners too instead of painting straight over them.
     */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
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

    /** Dropdown naming the class shown, e.g. {@code UserServiceImpl (2/3) ▾}; opens the list of candidates. */
    private static JBLabel candidateChooser(PeekTarget current, List<PeekTarget> candidates,
                                            Consumer<PeekTarget> onSwitch) {
        int index = candidates.indexOf(current);
        JBLabel chooser = new JBLabel(current.container() + " (" + (index + 1) + "/" + candidates.size() + ")",
                AllIcons.General.ArrowDown, JBLabel.LEFT);
        chooser.setHorizontalTextPosition(JBLabel.LEFT);
        chooser.setToolTipText("Choose implementation (" + (candidates.size() - 1) + " found)");
        chooser.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chooser.setBorder(JBUI.Borders.emptyRight(8));
        chooser.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                JBPopupFactory.getInstance()
                        .createPopupChooserBuilder(candidates)
                        .setRenderer(SimpleListCellRenderer.<PeekTarget>create((label, candidate, i) -> {
                            label.setIcon(candidate.icon());
                            label.setText(candidate.location() == null
                                    ? candidate.container()
                                    : candidate.container() + "  —  " + candidate.location());
                        }))
                        .setSelectedValue(current, true)
                        .setItemChosenCallback(chosen -> {
                            if (chosen != current) {
                                onSwitch.accept(chosen);
                            }
                        })
                        .createPopup()
                        .showUnderneathOf(chooser);
            }
        });
        return chooser;
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
