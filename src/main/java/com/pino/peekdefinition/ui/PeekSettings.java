package com.pino.peekdefinition.ui;

import com.intellij.ide.util.PropertiesComponent;

/** View options chosen from a Peek's menu. The last choice becomes the default for new Peeks. */
final class PeekSettings {

    private static final String METHOD_ONLY = "com.pino.peekdefinition.methodOnly";
    private static final String LINE_NUMBERS = "com.pino.peekdefinition.lineNumbers";
    private static final boolean METHOD_ONLY_DEFAULT = true;
    private static final boolean LINE_NUMBERS_DEFAULT = false;

    private PeekSettings() {
    }

    static boolean isMethodOnly() {
        return PropertiesComponent.getInstance().getBoolean(METHOD_ONLY, METHOD_ONLY_DEFAULT);
    }

    static void setMethodOnly(boolean methodOnly) {
        PropertiesComponent.getInstance().setValue(METHOD_ONLY, methodOnly, METHOD_ONLY_DEFAULT);
    }

    static boolean isLineNumbersShown() {
        return PropertiesComponent.getInstance().getBoolean(LINE_NUMBERS, LINE_NUMBERS_DEFAULT);
    }

    static void setLineNumbersShown(boolean shown) {
        PropertiesComponent.getInstance().setValue(LINE_NUMBERS, shown, LINE_NUMBERS_DEFAULT);
    }
}
