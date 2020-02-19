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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.github.abrarsyed.jastyle.ASFormatter;
import com.github.abrarsyed.jastyle.OptParser;
import com.github.abrarsyed.jastyle.exceptions.MalformedOptionException;

public class MCPCleanup {
    private final File input;
    private final File output;
    private final Set<String> dirs = new HashSet<>();
    private final ASFormatter formatter = new ASFormatter();
    private final GLConstantFixer oglFixer;

    public static MCPCleanup create(File input, File output) {
        return new MCPCleanup(input, output);
    }

    private MCPCleanup(File input, File output) {
        this.input = input;
        this.output = output;

        OptParser options = new OptParser(formatter);
        for (String opt : new String[] {
            "style=allman",
            "add-brackets",
            "break-closing-brackets",
            "indent-switches",
            "max-instatement-indent=40",
            "pad-oper",
            "pad-header",
            "unpad-paren",
            "break-blocks",
            "delete-empty-lines",
            "proper-inner-class-indenting=false"
        }) {
            try {
                options.parseOption(opt);
            } catch (MalformedOptionException e) {
                e.printStackTrace();
            }
        }
        try {
			oglFixer = new GLConstantFixer();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    }

    public void process() throws IOException {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT")); //Fix Java stupidity that causes timestamps in zips to depend on user's timezone!
        if (output.exists() && !output.delete()) throw new IOException("Could not delete file: " + output);
        File parent = output.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Could not make prent folders: " + parent);

        dirs.clear();

        try (ZipInputStream zinput = new ZipInputStream(new FileInputStream(input));
             ZipOutputStream zoutput = new ZipOutputStream(new FileOutputStream(output))) {

           ZipEntry entry;
           while ((entry = zinput.getNextEntry()) != null) {
               if (entry.getName().contains("META-INF") || entry.isDirectory())
                   continue;

               newZipEntry(entry.getName(), zoutput);

               if (entry.getName().endsWith(".java")) {
                   zoutput.write(processClass(entry.getName(), new String(getBytes(zinput), StandardCharsets.UTF_8)).getBytes());
               } else {
                   log("  Data  " + entry.getName());
                   copy(zinput, zoutput);
               }

               zoutput.closeEntry();
           }
        }
    }

    public String processClass(String name, String file) throws IOException {
        log("Processing file: " + name);
        file = FFPatcher.processFile(file);

        log("  processing comments");
        file = BasicCleanups.stripComments(file);

        log("  fixing imports comments");
        file = BasicCleanups.fixImports(file);

        log("  various other cleanup");
        file = BasicCleanups.cleanup(file);

        log("  fixing OGL constants");
        file = oglFixer.fixOGL(file);

        log("  formatting source");
        Reader reader = new StringReader(file);
        Writer writer = new StringWriter();
        formatter.format(reader, writer);
        reader.close();
        writer.flush();
        writer.close();

        file = writer.toString();

        return file;
    }


    public static void log(String message) {
        System.out.println(message);
    }

    private byte[] BUFFER = new byte[1024];
    private ZipEntry newZipEntry(String name, ZipOutputStream output) throws IOException {
        int idx = name.lastIndexOf('/');
        if (idx == name.length() - 1)
            idx = name.lastIndexOf('/', name.length() - 2);

        if (idx != -1)
            newZipEntry(name.substring(0, idx + 1), output);

        if (name.endsWith("/")) {
            if (dirs.contains(name))
                return null;
            dirs.add(name);
        }

        ZipEntry _new = new ZipEntry(name);
        _new.setTime(628041600000L); //Java8 screws up on 0 time, so use another static time.
        output.putNextEntry(_new);
        return _new;
    }
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
