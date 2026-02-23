package com.fileexplorer.service.ops.conflict;

import com.fileexplorer.service.ops.FileOperationRequest;
import com.fileexplorer.service.ops.FileOperationType;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Phase 4.2.0: Deterministic conflict policy engine.
 *
 * <p>This is intentionally conservative: it only auto-resolves when the profile clearly specifies an action.
 * Otherwise it returns {@link ConflictPolicyAction#PROMPT} to escalate to the Conflict Queue UI.</p>
 *
 * <p>Phase 7.0.0 adds ordered CUSTOM rules (first match wins). Rules are evaluated against the destination
 * file name and the normalized destination path.</p>
 */
public final class ConflictPolicyEngine {

    /**
     * Decision with explanation for UI/audit purposes.
     */
    public record Decision(ConflictPolicyAction action, String matchedRuleId) {}

    public ConflictPolicyAction decide(ConflictPolicyConfig cfg,
                                      FileOperationRequest req,
                                      Path source,
                                      Path destination,
                                      boolean overwriteDefault) {
        return decideDetailed(cfg, req, source, destination, overwriteDefault).action();
    }

    public Decision decideDetailed(ConflictPolicyConfig cfg,
                                   FileOperationRequest req,
                                   Path source,
                                   Path destination,
                                   boolean overwriteDefault) {
        if (cfg == null) return new Decision(ConflictPolicyAction.PROMPT, null);
        if (destination == null) return new Decision(ConflictPolicyAction.PROMPT, null);

        // If destination does not exist, no conflict.
        try {
            if (!Files.exists(destination)) return new Decision(ConflictPolicyAction.PROMPT, null);
        } catch (Exception ignored) {
            // If we cannot check, be safe.
            return new Decision(ConflictPolicyAction.PROMPT, null);
        }

        // If request already forced overwrite (handled earlier), nothing to decide here.
        if (overwriteDefault) {
            return new Decision(ConflictPolicyAction.OVERWRITE, "overwriteDefault");
        }

        ConflictPolicyProfile profile = cfg.profile();
        if (profile == null) profile = ConflictPolicyProfile.DEFAULT;

        return switch (profile) {
            case DEFAULT -> new Decision(ConflictPolicyAction.PROMPT, null);
            case CONSERVATIVE -> new Decision(ConflictPolicyAction.SKIP, "CONSERVATIVE");
            case AGGRESSIVE -> new Decision(ConflictPolicyAction.OVERWRITE, "AGGRESSIVE");
            case MIRROR -> new Decision(decideMirror(req), "MIRROR");
            case CUSTOM -> decideCustom(cfg, destination);
        };
    }

    private Decision decideCustom(ConflictPolicyConfig cfg, Path destination) {
        // Rules first (first match wins)
        List<ConflictRule> rules = (cfg == null) ? List.of() : cfg.customRules();
        String destName = (destination.getFileName() == null) ? "" : destination.getFileName().toString();
        String destNorm = normalize(destination);

        if (rules != null) {
            for (ConflictRule r : rules) {
                if (r == null) continue;
                String pat = r.pattern();
                if (pat == null || pat.isBlank()) continue;

                if (matches(pat, destName, destNorm)) {
                    ConflictPolicyAction a = r.action();
                    if (a == null) a = ConflictPolicyAction.PROMPT;
                    // Never auto-resolve PROMPT (keep it for UI)
                    if (a == ConflictPolicyAction.PROMPT) break;
                    return new Decision(a, r.id());
                }
            }
        }

        ConflictPolicyAction a = (cfg == null) ? ConflictPolicyAction.PROMPT : cfg.customDefaultAction();
        if (a == null) a = ConflictPolicyAction.PROMPT;
        return new Decision(a, "CUSTOM_DEFAULT");
    }

    private static boolean matches(String pattern, String fileName, String normalizedDestPath) {
        String p = pattern.trim();
        String mode = "glob";
        String expr = p;

        int idx = p.indexOf(':');
        if (idx > 0) {
            String prefix = p.substring(0, idx).trim().toLowerCase();
            if (prefix.equals("glob") || prefix.equals("regex")) {
                mode = prefix;
                expr = p.substring(idx + 1);
            }
        }

        try {
            if (mode.equals("regex")) {
                return fileName.matches(expr) || normalizedDestPath.matches(expr);
            }
            // glob
            var matcher = FileSystems.getDefault().getPathMatcher("glob:" + expr);
            // Try against filename and the full normalized path.
            if (!fileName.isEmpty() && matcher.matches(Path.of(fileName))) return true;
            return matcher.matches(Path.of(normalizedDestPath));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalize(Path p) {
        String s = String.valueOf(p);
        // Normalize Windows separators for matching.
        return s.replace('\\', '/');
    }

    private ConflictPolicyAction decideMirror(FileOperationRequest req) {
        if (req == null) return ConflictPolicyAction.PROMPT;
        FileOperationType t = req.type();
        if (t == FileOperationType.COPY || t == FileOperationType.MOVE) {
            return ConflictPolicyAction.OVERWRITE;
        }
        return ConflictPolicyAction.PROMPT;
    }
}
