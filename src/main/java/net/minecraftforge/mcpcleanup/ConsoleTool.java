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

        try {
            OptionSet options = parser.parse(args);

            File input = options.valueOf(inputO);
            File output  = options.valueOf(outputO);
            boolean filterFML = options.has(filterFMLO);

            log("MCPCleanup: ");
            log("  Input:     " + input);
            log("  Output:    " + output);
            log("  FilterFML: " + filterFML);

            MCPCleanup cleanup = MCPCleanup.create(input, output);
            if (filterFML)
                cleanup.filterFML();
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
