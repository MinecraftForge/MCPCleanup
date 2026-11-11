/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.github.abrarsyed.jastyle.ASFormatter;
import com.github.abrarsyed.jastyle.OptParser;
import com.github.abrarsyed.jastyle.exceptions.MalformedOptionException;

import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;

public class MCPCleanup {
    private final File input;
    private final File output;
    private final Set<String> dirs = new HashSet<>();
    private final ASFormatter formatter = new ASFormatter();
    private final GLConstantFixer oglFixer;
    private boolean filterFML = false;
    private boolean fixParams = false;
    private boolean fernflower = false;
    private Mode mode = Mode.MODERN;
    private boolean basic = true;
    private boolean gl = true;
    private boolean astyle = true;
    private int fml = 0;
    private boolean fmlCases = true;
    private Patches patches = null;

    private boolean fernflower_abstract = true;

    public static enum Mode implements ValueConverter<Mode> {
        MC_1_4_7("1.4.7"),
        MC_1_6_4("1.6.4"),
        MC_1_7_2("1.7.2"),
        // Cleanups used for Minecraft 1.8/old gradle. This does global replacements
        // instead of looping over every line
        MC_1_8("1.8"),
        MODERN("modern"); // Default, no abstract parameters

        private final String key;
        private Mode(String key) {
            this.key = key;
        }

        @Override
        public Mode convert(String value) {
            for (Mode m : Mode.class.getEnumConstants()) {
                if (m.key.equalsIgnoreCase(value))
                    return m;
            }
            throw new ValueConversionException("Unknown Mode: " + value);
        }
        @Override
        public Class<? extends Mode> valueType() {
            return Mode.class;
        }
        @Override
        public String valuePattern() {
            EnumSet<Mode> values = EnumSet.allOf(Mode.class);
            StringBuilder builder = new StringBuilder();
            builder.append('[');
            for (Iterator<Mode> i = values.iterator(); i.hasNext(); ) {
                builder.append(i.next().key);
                if (i.hasNext())
                    builder.append(',');
            }
            builder.append(']');
            return builder.toString();
        }
    }

    public static MCPCleanup create(File input, File output) {
        return new MCPCleanup(input, output);
    }

    private MCPCleanup(File input, File output) {
        this.input = input;
        this.output = output;

        OptParser options = new OptParser(formatter);
        for (String opt : new String[] {
            "style=allman",
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

    public MCPCleanup filterFML() {
        return filterFML(true);
    }
    public MCPCleanup filterFML(boolean value) {
        this.filterFML = value;
        return this;
    }

    public MCPCleanup fixParams() {
        return fixParams(true);
    }
    public MCPCleanup fixParams(boolean value) {
        this.fixParams = value;
        return this;
    }

    public MCPCleanup fernflower() {
        return fernflower(true);
    }
    public MCPCleanup fernflower(boolean value) {
        this.fernflower = value;
        return this;
    }

    public MCPCleanup mode(Mode value) {
        this.mode = value;
        return this;
    }

    public MCPCleanup basic() {
        return basic(true);
    }
    public MCPCleanup basic(boolean value) {
        this.basic = value;
        return this;
    }

    public MCPCleanup gl() {
        return gl(true);
    }
    public MCPCleanup gl(boolean value) {
        this.gl = value;
        return this;
    }

    public MCPCleanup astyle() {
        return astyle(true);
    }
    public MCPCleanup astyle(boolean value) {
        this.astyle = value;
        return this;
    }

    public MCPCleanup fml() {
        return fml(true);
    }
    public MCPCleanup fml(boolean value) {
        return fml(value ? 20 : 0);
    }
    public MCPCleanup fml(int value) {
        this.fml = value;
        return this;
    }

    public MCPCleanup fmlCases() {
        return fml(true);
    }
    public MCPCleanup fmlCases(boolean value) {
        this.fmlCases = value;
        return this;
    }

    public MCPCleanup patches(File file) {
        return patches(file, null, Collections.emptySet());
    }
    public MCPCleanup patches(File file, String prefix, Set<String> ignore) {
        this.patches = new Patches(file, prefix, ignore);
        return this;
    }

    private void setupMode() {
        if (mode == null)
            return;
        switch (this.mode) {
            case MC_1_4_7:
                this.fernflower = true;
                this.fernflower_abstract = false;
                this.basic = true;
                this.gl = true;
                this.fml = 0;
                this.astyle = true;
                break;
            case MC_1_6_4:
                this.fernflower = true;
                this.fernflower_abstract = false;
                this.basic = true;
                this.gl = true;
                this.fml = 10;
                this.astyle = true;
                break;
            case MC_1_7_2:
                this.fernflower = true;
                this.fernflower_abstract = false;
                this.basic = true;
                this.gl = true;
                this.fml = 20;
                this.astyle = true;
                break;
            default:
                break;
        }
    }

    public void process() throws IOException {
        setupMode();

        TimeZone.setDefault(TimeZone.getTimeZone("GMT")); //Fix Java stupidity that causes timestamps in zips to depend on user's timezone!
        if (output.exists() && !output.delete())
            throw new IOException("Could not delete file: " + output);
        File parent = output.getParentFile();
        if (parent != null) {
            if (!parent.exists() && !parent.mkdirs())
                throw new IOException("Could not make parent folders: " + parent);
        }

        dirs.clear();

        try (ZipInputStream zinput = new ZipInputStream(new FileInputStream(input));
             ZipOutputStream zoutput = new ZipOutputStream(new FileOutputStream(output))) {

           ZipEntry entry;
           while ((entry = zinput.getNextEntry()) != null) {
               String name = entry.getName();
               if (name.contains("META-INF") || entry.isDirectory())
                   continue;

               if (this.filterFML && (name.startsWith("net/minecraftforge/") || name.startsWith("cpw/mods/fml/"))) {
                   log("Filtering: " + name);
                   continue;
               }

               newZipEntry(name, zoutput);

               if (name.endsWith(".java")) {
                   zoutput.write(processClass(entry.getName(), new String(getBytes(zinput), StandardCharsets.UTF_8)).getBytes());
               } else {
                   //log("Data  " + name);
                   copy(zinput, zoutput);
               }

               zoutput.closeEntry();
           }
        }
    }

    public String processClass(String name, String file) throws IOException {
        log("Processing file: " + name);
        if (fernflower) {
            debug("  fernflower cleanup");
            file = FFPatcher.processFile(name, file, true, true);
            if (fernflower_abstract)
                file = FFPatcher.renameAbstracts(name, file);
        } else {
            debug("  new lines");
            file = FFPatcher.processSimple(file);
        }

        if (patches != null) {
            file = patches.apply(name, file);
        }

        if (basic) {
            debug("  processing comments");
            file = BasicCleanups.stripComments(file);

            debug("  fixing imports comments");
            file = BasicCleanups.fixImports(file);

            debug("  various other cleanup");
            file = BasicCleanups.cleanup(file);
        }

        if (fixParams) {
            debug("  fixing abstract parameter names");
            file = AbstractParameterRename.fixAbstractParameters(file);
        }

        if (gl) {
            debug("  fixing OGL constants");
            file = oglFixer.fixOGL(file);
        }

        if (astyle) {
            debug("  formatting source");
            Reader reader = new StringReader(file);
            Writer writer = new StringWriter();
            formatter.format(reader, writer);
            reader.close();
            writer.flush();
            writer.close();

            file = writer.toString();
        }

        if (fml == 10) {
            debug("  FML transformations");
            file = FmlCleanup1.cleanup(file);
        } else if (fml == 20) {
            debug("  FML transformations");
            file = FmlCleanup.cleanup(name, file, fmlCases);
        }

        return file;
    }

    public static void log(String message) {
        System.out.println(message);
    }
    public static void debug(String message) {
        //System.out.println(message);
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
