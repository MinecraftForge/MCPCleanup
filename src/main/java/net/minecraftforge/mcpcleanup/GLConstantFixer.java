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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class GLConstantFixer {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    //@formatter:off
    private static final String[] PACKAGES = {
        "GL11",
        "GL12",
        "GL13",
        "GL14",
        "GL15",
        "GL20",
        "GL21",
        "ARBMultitexture",
        "ARBOcclusionQuery",
        "ARBVertexBufferObject",
        "ARBShaderObjects"
    };
    //@formatter:on

    private final List<GLConstantGroup> json;
    private static final Pattern        CALL_REGEX     = Pattern.compile("(" + String.join("|", PACKAGES) + ")\\.([\\w]+)\\(.+\\)");
    private static final Pattern        CONSTANT_REGEX = Pattern.compile("(?<![-.\\w])\\d+(?![.\\w])");
    private static final String         ADD_AFTER      = "org.lwjgl.opengl.GL11";
    private static final String         CHECK          = "org.lwjgl.opengl.";
    private static final String         IMPORT_CHECK   = "import " + CHECK;
    private static final String         IMPORT_REPLACE = "import " + ADD_AFTER + ";";

    public GLConstantFixer() throws IOException {
        String text = new String(getBytes(GLConstantFixer.class.getResourceAsStream("/gl.json")), StandardCharsets.UTF_8);
        json = GSON.fromJson(text, new TypeToken<List<GLConstantGroup>>() {}.getType());
    }

    public String fixOGL(String text) {
        // if it never uses openGL, ignore it.
        if (!text.contains(IMPORT_CHECK))
            return text;

        text = annotateConstants(text);

        for (String pack : PACKAGES) {
            if (text.contains(pack + "."))
                text = updateImports(text, CHECK + pack);
        }

        return text;
    }

    private String annotateConstants(String text) {
        Matcher rootMatch = CALL_REGEX.matcher(text);
        String pack, method, fullCall;
        StringBuffer out = new StringBuffer(text.length());
        StringBuffer innerOut;

        // search with regex.
        while (rootMatch.find()) {
            // helper variables
            fullCall = rootMatch.group();
            pack = rootMatch.group(1);
            method = rootMatch.group(2);

            Matcher constantMatcher = CONSTANT_REGEX.matcher(fullCall);
            innerOut = new StringBuffer(fullCall.length());

            // search for hardcoded numbers
            while (constantMatcher.find()) {
                // helper variables and return variable.
                String constant = constantMatcher.group();
                String answer = null;

                // iterrate over the JSON
                for (GLConstantGroup group : json) {
                    // ensure that the package and method are defined
                    if (group.functions.containsKey(pack) && group.functions.get(pack).contains(method)) {
                        // itterrate through the map.
                        for (Map.Entry<String, Map<String, String>> entry : group.constants.entrySet()) {
                            // find the actual constant for the number from the regex
                            if (entry.getValue().containsKey(constant)) // construct the final line
                                answer = entry.getKey() + "." + entry.getValue().get(constant);
                        }
                    }
                }

                // replace the final line.
                if (answer != null)
                    constantMatcher.appendReplacement(innerOut, Matcher.quoteReplacement(answer));
            }
            constantMatcher.appendTail(innerOut);

            // replace the final line.
            if (fullCall != null)
                rootMatch.appendReplacement(out, Matcher.quoteReplacement(innerOut.toString()));
        }
        rootMatch.appendTail(out);

        return out.toString();
    }

    private static String updateImports(String text, String imp) {
        if (!text.contains("import " + imp + ";"))
            text = text.replace(IMPORT_REPLACE, IMPORT_REPLACE + BasicCleanups.NEWLINE + "import " + imp + ";");

        return text;
    }


    private byte[] BUFFER = new byte[1024];
    private byte[] getBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(stream, out);
        return  out.toByteArray();
    }
    private void copy(InputStream input, OutputStream output) throws IOException {
        int read = -1;
        while ((read = input.read(BUFFER)) != -1)
            output.write(BUFFER, 0, read);
    }
}
