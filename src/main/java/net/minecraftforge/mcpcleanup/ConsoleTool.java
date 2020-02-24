/*
 * MCPCleanup
 * Copyright (c) 2020+.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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
