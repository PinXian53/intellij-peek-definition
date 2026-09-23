package com.pino.peekdefinition;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.pino.peekdefinition.model.PeekTarget;
import com.pino.peekdefinition.resolve.JavaPeekTargetResolver;
import com.pino.peekdefinition.resolve.PeekResolveResult;

import java.util.List;

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

    public void testInterfaceMethodOffersItsImplementations() {
        myFixture.addClass("class RepoB implements Repo { public User find(Id id) { return null; } }");
        myFixture.addClass("class RepoA implements Repo { public User find(Id id) { return new User(); } }");

        PeekResolveResult.Found found = resolveFoundResult("Repo r = null; r.fi<caret>nd(new Id());");

        assertEquals(List.of("Repo", "RepoA", "RepoB"),
                found.candidates().stream().map(PeekTarget::container).toList());
        assertSame("the declaration itself comes first", found.target(), found.candidates().get(0));
        assertEquals("find(Id)", found.candidates().get(1).title());
    }

    public void testAbstractMethodOffersItsImplementations() {
        myFixture.addClass("abstract class Base { abstract void run(); }");
        myFixture.addClass("class Impl extends Base { void run() {} }");

        PeekResolveResult.Found found = resolveFoundResult("Base b = null; b.r<caret>un();");

        assertEquals(List.of("Base", "Impl"), found.candidates().stream().map(PeekTarget::container).toList());
    }

    public void testInterfaceMethodWithoutImplementationsOffersNothing() {
        assertEmpty(resolveFoundResult("Repo r = null; r.fi<caret>nd(new Id());").candidates());
    }

    public void testConcreteMethodOffersNothing() {
        assertEmpty(resolveFoundResult("new UserService().getU<caret>ser(new Id());").candidates());
    }

    public void testTitleIsSignatureLikeQuickDefinition() {
        PeekTarget target = resolveFound("new UserService().getU<caret>ser(new Name());");
        assertEquals("getUser(Name)", target.title());
    }

    public void testLocationIsModuleName() {
        PeekTarget target = resolveFound("new UserService().getU<caret>ser(new Name());");
        assertEquals(getModule().getName(), target.location());
        assertNotNull(target.locationIcon());
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
        return resolveFoundResult(statement).target();
    }

    private PeekResolveResult.Found resolveFoundResult(String statement) {
        configure(statement);
        PeekResolveResult result = resolve();
        assertTrue(String.valueOf(result), result instanceof PeekResolveResult.Found);
        return (PeekResolveResult.Found) result;
    }

    private void configure(String statement) {
        myFixture.configureByText("UserService.java", SERVICE);
        myFixture.configureByText("Caller.java", "class Caller { void run() { " + statement + " } }");
    }

    private PeekResolveResult resolve() {
        return new JavaPeekTargetResolver().resolve(myFixture.getEditor(), myFixture.getCaretOffset());
    }
}
