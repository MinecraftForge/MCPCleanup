/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.mcpcleanup;

public class FFPatcher {
    static final String MODIFIERS = "public|protected|private|static|abstract|final|native|synchronized|transient|volatile|strictfp";

    // Remove TRAILING whitespace
    private static final String TRAILING = "(?m)[ \\t]+$";

    //Remove repeated blank lines
    private static final String NEWLINES = "(?m)^(\\r\\n|\\r|\\n){2,}";

    public static String processFile(String text) {
        text = text.replaceAll(TRAILING, "");
        text = text.replaceAll(NEWLINES, System.getProperty("line.separator"));
        return text;
    }
}
