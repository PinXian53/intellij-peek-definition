package com.pino.peekdefinition;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.pino.peekdefinition.model.PeekSession;
import com.pino.peekdefinition.model.PeekTarget;
import com.pino.peekdefinition.resolve.JavaPeekTargetResolver;
import com.pino.peekdefinition.resolve.PeekResolveResult;
import com.pino.peekdefinition.ui.PeekInlayManager;

import java.util.Arrays;
import java.util.List;

/**
 * Lifecycle checks: the document stays untouched, inlays are removed and viewers released.
 * The light test framework also fails tearDown if any editor is left unreleased.
 */
public class PeekInlayManagerTest extends LightJavaCodeInsightFixtureTestCase {

    private static final String CALLER = """
            class Caller {
                void run() {
                    new Service().work();
                    new Service().rest();
                    System.out.println();
                }
            }
            class Service {
                void work() {
                    int a = 1;
                }
                void rest() {
                }
            }
            """;

    private static final int WORK_LINE = 2;
    private static final int REST_LINE = 3;

    public void testShowCreatesInlayBelowCaretLineWithoutTouchingDocument() {
        Editor host = configure();
        long stamp = host.getDocument().getModificationStamp();

        PeekSession session = show(host, WORK_LINE);

        assertNotNull(session);
        assertEquals(List.of(session), PeekSession.ofHost(host));
        assertSame(session, PeekSession.ofViewer(session.viewer()));
        assertTrue(session.inlay().isValid());
        assertEquals(WORK_LINE, session.anchorLine());
        assertSame("viewer must show the real document", host.getDocument(), session.viewer().getDocument());
        assertTrue(session.viewer().isViewer());
        assertEquals(stamp, host.getDocument().getModificationStamp());
    }

    public void testViewerShowsOnlyTheDefinitionLines() {
        Editor host = configure();
        PeekSession session = show(host, WORK_LINE);
        var viewer = session.viewer();
        var document = viewer.getDocument();
        int workStart = document.getText().indexOf("    void work()");
        int workEnd = document.getText().indexOf("}", workStart) + 1;

        assertTrue("text above the method is hidden", viewer.getFoldingModel().isOffsetCollapsed(0));
        assertTrue("text below the method is hidden",
                viewer.getFoldingModel().isOffsetCollapsed(document.getTextLength() - 1));
        assertEquals(0, viewer.offsetToVisualPosition(workStart).line);
        assertEquals(2, viewer.offsetToVisualPosition(workEnd).line);
        assertEquals("nothing is visible after the method's last line",
                2, viewer.offsetToVisualPosition(document.getTextLength()).line);
        for (var region : viewer.getFoldingModel().getAllFoldRegions()) {
            assertTrue("hidden text cannot be expanded", region.shouldNeverExpand());
        }
    }

    public void testCloseRemovesInlayAndReleasesViewer() {
        Editor host = configure();
        PeekSession session = show(host, WORK_LINE);

        assertTrue(PeekInlayManager.close(host));

        assertEmpty(PeekSession.ofHost(host));
        assertFalse(session.inlay().isValid());
        assertTrue(session.viewer().isDisposed());
        assertFalse(Arrays.asList(EditorFactory.getInstance().getAllEditors()).contains(session.viewer()));
        assertFalse("second close is a no-op", PeekInlayManager.close(host));
    }

    public void testPeeksOnDifferentLinesStayOpen() {
        Editor host = configure();
        PeekSession work = show(host, WORK_LINE);
        PeekSession rest = show(host, REST_LINE);

        assertFalse(work.isDisposed());
        assertEquals(List.of(work, rest), PeekSession.ofHost(host));
        assertEquals(2, blockInlayCount(host));
    }

    public void testPeekOnSameLineReplacesThatPeekOnly() {
        Editor host = configure();
        PeekSession work = show(host, WORK_LINE);
        PeekSession rest = show(host, REST_LINE);
        PeekSession workAgain = show(host, WORK_LINE);

        assertTrue(work.isDisposed());
        assertTrue(work.viewer().isDisposed());
        assertFalse(rest.isDisposed());
        assertEquals(List.of(rest, workAgain), PeekSession.ofHost(host));
        assertEquals(2, blockInlayCount(host));
    }

    public void testExplicitReplacementKeepsOtherPeeks() {
        Editor host = configure();
        PeekSession work = show(host, WORK_LINE);
        PeekSession rest = show(host, REST_LINE);

        // What Peek-from-inside-a-viewer does: replace that Peek, anchored where it was.
        PeekSession replaced = PeekInlayManager.show(host, work.inlay().getOffset(), resolve(host, REST_LINE), work,
                List.of());

        assertTrue(work.isDisposed());
        assertEquals(WORK_LINE, replaced.anchorLine());
        assertEquals(List.of(rest, replaced), PeekSession.ofHost(host));
    }

    public void testCloseFromViewerClosesOnlyItsPeek() {
        Editor host = configure();
        PeekSession work = show(host, WORK_LINE);
        PeekSession rest = show(host, REST_LINE);

        assertTrue(PeekInlayManager.close(work.viewer()));

        assertTrue(work.isDisposed());
        assertEquals(List.of(rest), PeekSession.ofHost(host));
    }

    public void testEscapeInHostClosesAllPeeks() {
        Editor host = configure();
        PeekSession work = show(host, WORK_LINE);
        PeekSession rest = show(host, REST_LINE);

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);

        assertTrue(work.isDisposed());
        assertTrue(rest.isDisposed());
        assertEmpty(PeekSession.ofHost(host));
    }

    public void testEscapeInViewerClosesOnlyItsPeek() {
        Editor host = configure();
        PeekSession work = show(host, WORK_LINE);
        PeekSession rest = show(host, REST_LINE);

        EditorTestUtil.executeAction(work.viewer(), IdeActions.ACTION_EDITOR_ESCAPE);

        assertTrue(work.isDisposed());
        assertEquals(List.of(rest), PeekSession.ofHost(host));
    }

    public void testEscapeClearsSelectionBeforeClosingPeek() {
        Editor host = configure();
        show(host, WORK_LINE);
        host.getSelectionModel().setSelection(0, 5);

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);

        assertFalse(host.getSelectionModel().hasSelection());
        assertSize(1, PeekSession.ofHost(host));

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);

        assertEmpty(PeekSession.ofHost(host));
    }

    public void testReleasingHostEditorClosesAllPeeks() {
        Editor host = EditorFactory.getInstance().createEditor(configure().getDocument(), getProject());
        PeekSession work = show(host, WORK_LINE);
        PeekSession rest = show(host, REST_LINE);

        EditorFactory.getInstance().releaseEditor(host);

        assertTrue(work.isDisposed());
        assertTrue(rest.isDisposed());
        assertTrue(work.viewer().isDisposed());
        assertTrue(rest.viewer().isDisposed());
    }

    private Editor configure() {
        myFixture.configureByText("Caller.java", CALLER);
        return myFixture.getEditor();
    }

    /** Peeks the call on {@code line} of {@link #CALLER}. */
    private PeekSession show(Editor host, int line) {
        return PeekInlayManager.show(host, callOffset(host, line), resolve(host, line));
    }

    private static PeekTarget resolve(Editor host, int line) {
        PeekResolveResult result = new JavaPeekTargetResolver().resolve(host, callOffset(host, line));
        return ((PeekResolveResult.Found) result).target();
    }

    /** Offset inside the method name called on {@code line}, i.e. just after {@code "new Service()."}. */
    private static int callOffset(Editor host, int line) {
        int lineStart = host.getDocument().getLineStartOffset(line);
        String text = host.getDocument().getText().substring(lineStart);
        return lineStart + text.indexOf("().") + 4;
    }

    private static int blockInlayCount(Editor host) {
        return host.getInlayModel().getBlockElementsInRange(0, host.getDocument().getTextLength()).size();
    }
}
