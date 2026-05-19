/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.mcpcleanup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.mcpcleanup.ContextualPatch.HunkReport;
import net.minecraftforge.mcpcleanup.ContextualPatch.PatchReport;
import net.minecraftforge.mcpcleanup.ContextualPatch.PatchStatus;
import net.minecraftforge.mcpcleanup.ContextualPatch.SingleFileContext;

class Patches {
    private final Map<String, List<ContextualPatch>> patches = new HashMap<>();
    private final List<PatchReport> patchErrors = new ArrayList<PatchReport>();

    @SuppressWarnings("unchecked")
    private static <R, E extends Throwable> R sneak(Throwable t) throws E {
        throw (E) t;
    }

    Patches(File archive, String prefix, Set<String> ignore) {
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(archive))) {
            for (ZipEntry entry; (entry = zin.getNextEntry()) != null;) {
                String name = entry.getName();
                if (prefix != null) {
                    if (!name.startsWith(prefix))
                        continue;
                    name = name.substring(prefix.length());
                }

                int patchIndex = name.indexOf(".patch");
                // 6 is the length of ".patch" + 3 to account for .## at the end of the file.
                if (patchIndex < 0 || patchIndex < name.length() - 9)
                    continue;

                String filename = name.substring(0, patchIndex).replace('/', '.');
                if (ignore.contains(filename))
                    continue;

                @SuppressWarnings("resource")
                ContextualPatch patch = ContextualPatch.create(zin)
                    .setAccessC14N(true);

                patches.computeIfAbsent(filename, x -> new ArrayList<>()).add(patch);
            }
        } catch (IOException e) {
            sneak(e);
        }
    }

    public String apply(String filename, String text) {
        List<ContextualPatch> candidates = this.patches.get(filename.replace('/', '.'));
        if (candidates == null || candidates.isEmpty())
            return text;

        log("patching");
        SingleFileContext ctx = ContextualPatch.context(text);

        ContextualPatch patch = null;
        for (ContextualPatch candidate : candidates) {
            patch = candidate;
            List<PatchReport> errors = patch.patch(true, ctx);

            boolean success = true;
            for (PatchReport rep : errors) {
                if (!rep.getStatus().isSuccess())
                    success = false;
            }
            if (success)
                break;
        }

        List<PatchReport> reports = patch.patch(false, ctx);
        for (PatchReport report : reports) {
            if (!report.getStatus().isSuccess()) {
                log("  patching failed: " + report.getTarget());

                for (HunkReport hunk : report.getHunks()) {
                    if (!hunk.getStatus().isSuccess())
                        log("    hunk " + hunk.getHunkID() + " failed!");
                }

                //log("Target: \n" + text);

                sneak(report.getFailure());
            } else if (report.getStatus() == PatchStatus.Fuzzed) { // catch fuzzed patches
                log("  patching fuzzed: " + report.getTarget() + (report.getFailure() == null ? "" : report.getFailure().getMessage()));

                for (HunkReport hunk : report.getHunks()) {
                    if (!hunk.getStatus().isSuccess())
                        log("    hunk " + hunk.getHunkID() + " fuzzed " + hunk.getFuzz() + "!");
                }
            }
        }

        patchErrors.addAll(reports);
        return String.join("\n", ctx.getData());
    }

    public static void log(String message) {
        System.out.println("  " + message);
    }
}
