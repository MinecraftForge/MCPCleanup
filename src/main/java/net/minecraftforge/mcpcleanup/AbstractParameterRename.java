/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.mcpcleanup;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbstractParameterRename {

    // Definitions of methods must contain both a non-empty parameter list and end with a semicolon.
    public static final Pattern METHOD_DECLARATION = Pattern.compile("(.*)\\(.+var\\d+.+\\);");

    // Bear with me here.
    // We need the number from the SRG function, so we read the whole method declaration.
    // The argument list is any number of:
    // ( [annotation] <type> <identifier> <separator> )
    // succeeded by an optional throws declaration, and a list of exceptions.
    public static final Pattern FUNCTION_DEFINITION = Pattern.compile(
            " (?<method>func_(?<number>\\d+)_[a-zA-Z_]+)\\((?<arguments>(?:(?<annotation>(?:\\@(?:[a-zA-Z_$][\\w_$\\.]*)(?:\\((?:.+)*\\))? ?))?(?<type>(?:[^ ,])+(?:<.*>)?(?: \\.\\.\\.)?) var(?<id>\\d+)(?<end>,? )?)+)\\)(?: throws (?:[\\w$.]+,? ?)+)?;$"
    );

    // Once we have parsed a method declaration, we need to be able to extract the parameter information:
    // As before:
    // The argument list is any number of:
    // ( [annotation] <type> <identifier> <separator> )
    // This time, we only want the IDs from the definition.
    public static final Pattern PARAMETER_LIST = Pattern.compile(
            "(?:(?<annotation>(?:\\@(?:[a-zA-Z_$][\\w_$\\.]*)(?:\\((?:.+)*\\))? ?))?(?<type>(?:[^ ,])+(?:<.*>)?(?: \\.\\.\\.)?) var(?<id>\\d+)(?<end>,? )?)"
    );

    // A drop-in replacement for Python's Regex.sub, including the lambda replacement.
    public static String sub(String original, Pattern tokenPattern, Function<Matcher, String> converter) {
        int lastIndex = 0;
        StringBuilder output = new StringBuilder();
        Matcher matcher = tokenPattern.matcher(original);
        while (matcher.find()) {
            output.append(original, lastIndex, matcher.start())
                    .append(converter.apply(matcher));

            lastIndex = matcher.end();
        }
        if (lastIndex < original.length()) {
            output.append(original, lastIndex, original.length());
        }
        return output.toString();
    }

    public static String fixAbstractParameters(String file) {
        // Filter for files that even contain an abstract method.
        //         if (line.endswith(";")):
        file = sub(file, METHOD_DECLARATION, outerMatch -> {
            String method = outerMatch.group(0);
            // Parse the method definition
            //             line = _REGEXP['abstract'].sub(abstract_match, line)
            method = sub(method, FUNCTION_DEFINITION, methodMatch -> {
                // Fetch arguments
                //             args = match.group('arguments')
                String arguments = methodMatch.group("arguments");
                // Rename the arguments accordingly.
                //             args = _REGEXP['params_var'].sub(lambda m: '%s p_%s_%s_%s' % (m.group('type'), match.group('number'), m.group('id'), m.group('end') if not m.group('end') is None else ''), args)
                arguments = sub(arguments, PARAMETER_LIST, param -> String.format("%s p_%s_%s_%s", param.group("type"), methodMatch.group("number"), param.group("id"), param.group("end") != null ? param.group("end") : ""));

                // return match.group(0).replace(match.group('arguments'), args)
                return methodMatch.group(0).replace(methodMatch.group("arguments"), arguments);
            });

            return method;
        });

        return file;
    }

}
