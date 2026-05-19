/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.mcpcleanup;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.mcpcleanup.MCPCleanup.Mode;

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
        OptionSpec<Boolean> fmlCasesCleanupO = parser.accepts("fml-cases", "Fixed blank lines around case statements when doing FMLCleanup").withOptionalArg().ofType(Boolean.class);
        OptionSpec<Boolean> astyleO = parser.accepts("astyle", "Runs AStyle formatter").withOptionalArg().ofType(Boolean.class);

        OptionSpec<Mode> modeO = parser.accepts("mode", "What set of fixes we should be running").withRequiredArg().withValuesConvertedBy(Mode.MODERN);
        OptionSpec<String> ignorePatchesO = parser.accepts("ignore-patches", "Patch files to ingore").withRequiredArg();


        OptionSpec<File> patchesO = parser.accepts("patches", "Archive containing patch files to apply after doing the FernFlower cleanup").withRequiredArg().ofType(File.class);
        OptionSpec<String> patchesPrefixO = parser.accepts("patches-prefix", "Prefix of patch files in the specified archive").withRequiredArg();

        OptionSet options = parser.parse(args);

        File input = options.valueOf(inputO);
        File output  = options.valueOf(outputO);
        Mode mode = options.valueOf(modeO);
        boolean filterFML       = get(options, false, filterFMLO);
        boolean fixParams       = get(options, false, fixGeneric);
        boolean ffCleanup       = get(options, false, ffCleanupO);
        boolean basicCleanup    = get(options, true,  basicCleanupO);
        boolean glCleanup       = get(options, true,  glCleanupO);
        boolean fmlCleanup      = get(options, false, fmlCleanupO);
        boolean fmlCasesCleanup = get(options, true,  fmlCasesCleanupO);
        boolean astyle          = get(options, true,  astyleO);
        File patches = options.has(patchesO) ? options.valueOf(patchesO) : null;
        String prefix = options.has(patchesPrefixO) ? options.valueOf(patchesPrefixO) : null;
        Set<String> ignores = new HashSet<String>();

        log("MCPCleanup: ");
        log("  Input:     " + input);
        log("  Output:    " + output);
        log("  FilterFML: " + filterFML);
        log("  Abstract:  " + fixParams);
        log("  FF:        " + ffCleanup);
        log("  Mode:      " + mode);
        log("  Basic:     " + basicCleanup);
        log("  GL:        " + glCleanup);
        log("  FML:       " + fmlCleanup);
        log("  FML Case:  " + fmlCasesCleanup);
        log("  Astyle:    " + astyle);
        if (patches != null) {
            log("  Patches:   " + patches.getAbsolutePath());
            if (prefix != null)
                log("  Prefix:    " + prefix);
            for (String value : options.valuesOf(ignorePatchesO)) {
                log("  Ignore:    " + value);
                ignores.add(value.replace('/', '.'));
            }
        }

        MCPCleanup cleanup = MCPCleanup.create(input, output)
            .filterFML(filterFML)
            .fixParams(fixParams)
            .fernflower(ffCleanup)
            .mode(mode)
            .basic(basicCleanup)
            .gl(glCleanup)
            .fml(fmlCleanup)
            .fmlCases(fmlCasesCleanup)
            .astyle(astyle)
            ;
        if (patches != null)
            cleanup.patches(patches, prefix, ignores);
        cleanup.process();
    }

    public static void log(String message) {
        System.out.println(message);
    }
}
