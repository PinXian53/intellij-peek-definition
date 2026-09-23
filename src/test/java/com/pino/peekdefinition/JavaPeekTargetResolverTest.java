package com.pino.peekdefinition;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.pino.peekdefinition.model.PeekTarget;
import com.pino.peekdefinition.resolve.JavaPeekTargetResolver;
import com.pino.peekdefinition.resolve.PeekResolveResult;

/**
 * Resolution goes through IntelliJ's own resolve, never by method name.
 * The light fixture here has no JDK, so overloads use project types instead of Long / String.
 */
public class JavaPeekTargetResolverTest extends LightJavaCodeInsightFixtureTestCase {

    private static final String SERVICE = """
            class Id {}
            class Name {}
            class UserService {
                /** By id. */
                User getUser(Id id) { return null; }
                User getUser(Name name) { return null; }
                User getUser(Id id, boolean cache) { return null; }
            }
            class User {
                User() {}
                User(Name name) {}
            }
            interface Repo {
                User find(Id id);
            }
            """;

    public void testOverloadId() {
        assertTargetStartsWith("new UserService().getU<caret>ser(new Id());", "/** By id. */\n    User getUser(Id id)");
    }

    public void testOverloadName() {
        assertTargetStartsWith("new UserService().getU<caret>ser(new Name());", "User getUser(Name name)");
    }

    public void testOverloadIdBoolean() {
        assertTargetStartsWith("new UserService().getU<caret>ser(new Id(), true);", "User getUser(Id id, boolean cache)");
    }

    public void testConstructor() {
        assertTargetStartsWith("new Us<caret>er(new Name());", "User(Name name)");
    }

    public void testInterfaceMethodWithoutBody() {
        assertTargetStartsWith("Repo r = null; r.fi<caret>nd(new Id());", "User find(Id id);");
    }

    public void testTitle() {
        PeekTarget target = resolveFound("new UserService().getU<caret>ser(new Name());");
        assertEquals("UserService.java — UserService.getUser(Name)", target.title());
    }

    public void testNothingUnderCaret() {
        assertFailed("new UserService().getUser(new Id());  <caret>  ", JavaPeekTargetResolver.NO_DEFINITION);
    }

    public void testUnresolvedReference() {
        assertFailed("new UserService().miss<caret>ing();", JavaPeekTargetResolver.NO_DEFINITION);
    }

    public void testCaretOnDeclaration() {
        myFixture.configureByText("Decl.java", "class Decl { void he<caret>llo() {} }");
        assertEquals(new PeekResolveResult.Failed(JavaPeekTargetResolver.AT_DEFINITION), resolve());
    }

    private void assertTargetStartsWith(String statement, String expectedPrefix) {
        PeekTarget target = resolveFound(statement);
        String text = target.range().substring(target.pointer().getElement().getContainingFile().getText());
        assertTrue("Unexpected target:\n" + text, text.startsWith(expectedPrefix));
    }

    private void assertFailed(String statement, String message) {
        configure(statement);
        assertEquals(new PeekResolveResult.Failed(message), resolve());
    }

    private PeekTarget resolveFound(String statement) {
        configure(statement);
        PeekResolveResult result = resolve();
        assertTrue(String.valueOf(result), result instanceof PeekResolveResult.Found);
        return ((PeekResolveResult.Found) result).target();
    }

    private void configure(String statement) {
        myFixture.configureByText("UserService.java", SERVICE);
        myFixture.configureByText("Caller.java", "class Caller { void run() { " + statement + " } }");
    }

    private PeekResolveResult resolve() {
        return new JavaPeekTargetResolver().resolve(myFixture.getEditor(), myFixture.getCaretOffset());
    }
}
