/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.mcpcleanup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// FG 1.0/Python version of FmlCleanup
// This has the issue of not renaming inner classes
class FmlCleanup1 {
    private static final Pattern METHOD_REG = Pattern.compile("^ {4}(\\w+\\s+\\S.*\\(.*|static)$");
    private static final Pattern CATCH_REG = Pattern.compile("catch \\((.*)\\)$");
    private static final Pattern NESTED_PERINTH = Pattern.compile("\\(.*\\(");
    private static final Pattern METHOD_PARAMS = Pattern.compile("\\((.+)\\)");
    private static final Pattern METHOD_DEC_END = Pattern.compile("(}|\\);|throws .+?;)$");
    private static final Pattern METHOD_END = Pattern.compile("^ {4}\\}$");
    private static final Pattern CAPS_START = Pattern.compile("^[A-Z]");
    private static final Pattern ARRAY = Pattern.compile("(\\[|\\.\\.\\.)");
    private static final Pattern VAR_CALL = Pattern.compile("(?i)[a-z_$][a-z0-9_\\[\\]]+ var\\d+");
    private static final Pattern VAR = Pattern.compile("var\\d+");

    private static final Comparator<String> COMPARATOR = (str1, str2) -> str2.length() - str1.length();

    public static String cleanup(String text) {
        String[] lines = text.split("(\r\n|\r|\n)");
        String output = "";

        boolean insideMethod = false;
        String method = "";
        ArrayList<String> methodVars = new ArrayList<String>();
        boolean skip = false;

        for (String line : lines) {
            // if re.search(METHOD_REG, line) and not re.search('=', line) and not re.search(r'\(.*\(', line):
            if (METHOD_REG.matcher(line).find() && !line.contains("=") && !NESTED_PERINTH.matcher(line).find()) {
                // if re.search(r'\(.+\)', line):
                Matcher match = METHOD_PARAMS.matcher(line);
                if (match.find()) {
                    // method_variables += [s.strip() for s in re.search(r'\((.+)\)', line).group(1).split(',')]
                    String[] pts = match.group(1).split(",");
                    for (String str : pts) {
                        str = str.trim();
                        methodVars.add(str);
                    }
                }

                method += line + '\n';
                // method += line

                // single line method?
                skip = true;

                // if not re.search(r'(}|\);|throws .+?;)$', line):
                if (!METHOD_DEC_END.matcher(line).find())
                    insideMethod = true;
            //elif re.search(r'^ {%s}}$' % indent, line):
            } else if (METHOD_END.matcher(line).find()) {
                //inside_method = False
                insideMethod = false;
            }

            // inside method actions now.
            if (insideMethod) {
                if (skip) {
                    skip = false;
                    continue;
                }

                method += line + '\n';

                Matcher matcher = CATCH_REG.matcher(line);
                if (matcher.find())
                    methodVars.add(matcher.group(1));
                else {
                    Matcher match = VAR_CALL.matcher(line);
                    while (match.find()) {
                        if (!match.group().startsWith("return") && !match.group().startsWith("throw"))
                            methodVars.add(match.group());
                    }
                }
            } else {
                if (method != null && !method.isEmpty()){
                    FmlCleanup1 namer = new FmlCleanup1();
                    HashMap<String, String> todo = new HashMap<String, String>();

                    for (String var : methodVars) {
                        String[] split = var.split(" ");
                        if (split.length > 1)
                            todo.put(split[1], namer.getName(split[0], split[1]));
                        else
                            System.out.printf("Unknown thing : %s (%s)\n", var, method);
                    }

                    List<String> sortedKeys = new ArrayList<String>(todo.keySet());
                    Collections.sort(sortedKeys, COMPARATOR);

                    // closure changes the sort, to sort by the return value of the closure.
                    for (String key : sortedKeys) {
                        if (VAR.matcher(key).matches())
                            method = method.replace(key, todo.get(key));
                    }

                    output += method;

                    // clear methods.
                    methodVars.clear();
                    method = "";
                }

                if (skip) {
                    skip = false;
                    continue;
                }

                output += line + '\n';
            }
        }

        return output;
    }

    private final HashMap<String, Holder> last = new HashMap<String, Holder>();
    private final HashMap<String, String> remap = new HashMap<String, String>();

    private FmlCleanup1() {
        last.put("byte", new Holder(0, false, "b"));
        last.put("char", new Holder(0, false, "c"));
        last.put("short", new Holder(1, false, "short"));
        last.put("int", new Holder(0, true, "i", "j", "k", "l"));
        last.put("boolean", new Holder(0, true, "flag"));
        last.put("double", new Holder(0, false, "d"));
        last.put("float", new Holder(0, true, "f"));
        last.put("File", new Holder(1, true, "file"));
        last.put("String", new Holder(0, true, "s"));
        last.put("Class", new Holder(0, true, "oclass"));
        last.put("Long", new Holder(0, true, "olong"));
        last.put("Byte", new Holder(0, true, "obyte"));
        last.put("Short", new Holder(0, true, "oshort"));
        last.put("Boolean", new Holder(0, true, "obool"));
        last.put("Package", new Holder(0, true, "opackage"));
        remap.put("long", "int");
    }

    private String getName(String type, String var) {
        String index = null;
        if (last.containsKey(type))
            index = type;
        else if (remap.containsKey(type))
            index = remap.get(type);

        if ((index == null || index.isEmpty()) && (CAPS_START.matcher(type).find() || ARRAY.matcher(type).find())) {
            // replace multi things with arrays.
            type = type.replace("...", "[]");

            while (type.contains("[][]"))
                type = type.replaceAll("\\[\\]\\[\\]", "[]");

            String name = lower(type);
            boolean skip_zero = true;

            //if (Pattern.compile("\\[").matcher(type).find()) {
            if (type.indexOf('[') != -1) {
                skip_zero = true;
                name = "a" + name;
                name = name.replace("[]", "").replace("...", "");
            }

            last.put(type, new Holder(0, skip_zero, name));
            index = type;
        }

        if (index == null || index.isEmpty()) {
            //Debug: System.out.println("NO DATA FOR TYPE " + type + " " + var);
            return lower(type);
        }

        Holder holder = last.get(index);
        int id = holder.id;
        List<String> data = holder.data;

        int ammount = data.size();

        String name;
        if (ammount == 1) {
            name = data.get(0) + (id == 0 && holder.skip_zero ? "" : id);
        } else {
            int num = id / ammount;
            name = data.get(id % ammount) + (id < ammount && holder.skip_zero ? "" : num);
        }

        holder.id++;
        return name;
    }

    private static String lower(String string) {
        return string.toLowerCase(Locale.ENGLISH);
    }

    private class Holder {
        int id;
        final public boolean skip_zero;
        final ArrayList<String> data = new ArrayList<String>();

        public Holder(int t1, boolean skip_zero, String... stuff) {
            this.id = t1;
            this.skip_zero = skip_zero;
            Collections.addAll(this.data, stuff);
        }
    }
}
