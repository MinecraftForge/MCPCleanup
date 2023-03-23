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
    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> inputO = parser.accepts("input").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();
        OptionSpec<Void> filterFMLO = parser.accepts("filter-fml", "Filter out net.minecraftforge and cpw.mods.fml package, in the cases where we inject the Side annotations.");
        OptionSpec<Void> fixGeneric = parser.accepts("fix-generic-params", "Fix parameters of generic and interface methods, who get parameters named var{x} rather than SRG IDs.");

        try {
            OptionSet options = parser.parse(args);

            File input = options.valueOf(inputO);
            File output  = options.valueOf(outputO);
            boolean filterFML = options.has(filterFMLO);
            boolean fixParams = options.has(fixGeneric);

            log("MCPCleanup: ");
            log("  Input:     " + input);
            log("  Output:    " + output);
            log("  FilterFML: " + filterFML);
            log("  FixGenericParams: " + fixParams);

            MCPCleanup cleanup = MCPCleanup.create(input, output);
            if (filterFML)
                cleanup.filterFML();
            if (fixParams)
                cleanup.fixParams();
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
