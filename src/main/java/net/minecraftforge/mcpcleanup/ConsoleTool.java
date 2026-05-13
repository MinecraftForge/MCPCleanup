/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.mcpcleanup;

import java.io.File;
import java.io.IOException;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class ConsoleTool {
    private static boolean get(OptionSet options, boolean _default, OptionSpec<Boolean> spec) {
        // If unspecified assume default
        if (!options.has(spec))
            return _default;
        // If specified with no value assume true
        Boolean val = options.valueOf(spec);
        return val == null ? true : val;
    }

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> inputO = parser.accepts("input").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();
        OptionSpec<Boolean> filterFMLO = parser.accepts("filter-fml", "Filter out net.minecraftforge and cpw.mods.fml package, in the cases where we inject the Side annotations.").withOptionalArg().ofType(Boolean.class);
        OptionSpec<Boolean> fixGeneric = parser.accepts("fix-generic-params", "Fix parameters of generic and interface methods, who get parameters named var{x} rather than SRG IDs.").withOptionalArg().ofType(Boolean.class);
        OptionSpec<Boolean> ffCleanupO = parser.accepts("fernflower", "Runs the full FFPatcher set").withOptionalArg().ofType(Boolean.class);
        OptionSpec<Boolean> basicCleanupO = parser.accepts("basic", "Rus the BasicCleanups set").withOptionalArg().ofType(Boolean.class);
        OptionSpec<Boolean> glCleanupO = parser.accepts("gl", "Runs the GLConstantFixer set").withOptionalArg().ofType(Boolean.class);
        OptionSpec<Boolean> fmlCleanupO = parser.accepts("fml", "Runs the FMLCleanup set").withOptionalArg().ofType(Boolean.class);
        OptionSpec<Boolean> astyleO = parser.accepts("astyle", "Runs AStyle formatter").withOptionalArg().ofType(Boolean.class);

        try {
            OptionSet options = parser.parse(args);

            File input = options.valueOf(inputO);
            File output  = options.valueOf(outputO);
            boolean filterFML    = get(options, false, filterFMLO);
            boolean fixParams    = get(options, false, fixGeneric);
            boolean ffCleanup    = get(options, false, ffCleanupO);
            boolean basicCleanup = get(options, true,  basicCleanupO);
            boolean glCleanup    = get(options, true,  glCleanupO);
            boolean fmlCleanup   = get(options, false, fmlCleanupO);
            boolean astyle       = get(options, true,  astyleO);

            log("MCPCleanup: ");
            log("  Input:     " + input);
            log("  Output:    " + output);
            log("  FilterFML: " + filterFML);
            log("  Abstract:  " + fixParams);
            log("  FF:        " + ffCleanup);
            log("  Basic:     " + basicCleanup);
            log("  GL:        " + glCleanup);
            log("  FML:       " + fmlCleanup);
            log("  Astyle:    " + astyle);

            MCPCleanup cleanup = MCPCleanup.create(input, output)
                .filterFML(filterFML)
                .fixParams(fixParams)
                .fernflower(ffCleanup)
                .basic(basicCleanup)
                .gl(glCleanup)
                .fml(fmlCleanup)
                .astyle(astyle)
                ;
            cleanup.process();
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }


    public static void log(String message) {
        System.out.println(message);
    }
}
