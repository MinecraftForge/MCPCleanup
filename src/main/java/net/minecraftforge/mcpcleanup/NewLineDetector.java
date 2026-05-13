/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.mcpcleanup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

// The normal BufferedReader doesn't have a way to determine between a
// file with a newline at the end and a file with no newline at the end.
// So we detect that and add an extra empty result from 'readLine'
public class NewLineDetector implements AutoCloseable {
    @SuppressWarnings("unchecked")
    private static <R, E extends Throwable> R sneak(Throwable t) throws E {
        throw (E) t;
    }
    public static List<String> readLines(String text) {
        try (NewLineDetector reader = new NewLineDetector(new StringReader(text))) {
            List<String> lines = new ArrayList<String>();
            for (String line; (line = reader.readLine()) != null; )
                lines.add(line);
            return lines;
        } catch (IOException e) { // Should never happen as we're reading from a string
            return sneak(e);
        }
    }

    private final BufferedReader buffered;
    private final Detector detector;

    public NewLineDetector(Reader reader) {
        this.detector = new Detector(reader);
        this.buffered = new BufferedReader(this.detector);
    }

    public String readLine() throws IOException {
        String ret = this.buffered.readLine();
        if (ret == null && this.detector.lastWasNewline) {
            this.detector.lastWasNewline = false;
            return "";
        }
        return ret;
    }

    @Override
    public void close() throws IOException {
        this.buffered.close();
    }

    private static class Detector extends Reader {
        private final Reader wrapped;
        private boolean lastWasNewline = false;

        public Detector(Reader wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            int ret = this.wrapped.read(cbuf, off, len);
            if (ret > 0) {
                int c = cbuf[off + ret - 1];
                lastWasNewline = c == '\n';
            }
            return ret;
        }

        @Override
        public void close() throws IOException {
            this.wrapped.close();
        }
    }
}
