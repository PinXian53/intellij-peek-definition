# IntelliJ Peek Definition

Visual Studio style **Peek Definition** for IntelliJ IDEA: shows the definition of the symbol at the caret
inside the current editor, below the current line. Spec: [docs/Spec.md](docs/Spec.md).

Status: **POC** (Spec §10).

## Build & run

Requires JDK 17+ to run Gradle; the plugin compiles for Java 21 against IntelliJ IDEA 2024.2.

```bash
./gradlew test          # automated checks (resolve, lifecycle, Esc)
./gradlew runIde        # sandbox IDE with the plugin installed
./gradlew buildPlugin   # build/distributions/intellij-peek-definition-<version>.zip
```

## Try it

The action has no default shortcut. In the sandbox IDE:

1. Open any Java project.
2. Put the caret on a method call → right-click → **Peek Definition**, or **Navigate → Peek Definition**.
3. Optional: bind a key in **Settings → Keymap → Peek Definition** (Visual Studio uses `Alt+F12`,
   which IntelliJ already uses for Terminal).

Inside the Peek: **Esc** closes it, double-clicking the title opens the file in a normal tab,
and running Peek Definition again replaces the content with the new definition.

## Manual POC checklist

| # | Check |
| --- | --- |
| P1 | You can scroll, select text and give focus to the code inside the Peek |
| P3 | Keywords, strings and comments are coloured like the normal editor |
| P4 | Method / field / parameter colours: note the result with the target file open in a tab and with it closed |
| P5 | Ctrl/⌘+Click inside the Peek jumps to the right place |
| P6 | Opening and closing the Peek does not make the host editor jump |
| P8 | Switching Light ↔ Darcula updates the Peek immediately |
| P10 | The mouse wheel inside the Peek scrolls the Peek, not the host editor |
