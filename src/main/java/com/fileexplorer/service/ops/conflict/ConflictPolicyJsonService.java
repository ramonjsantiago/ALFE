package com.fileexplorer.service.ops.conflict;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 4.2.1: Minimal JSON import/export for conflict policy configuration.
 *
 * <p>We intentionally avoid adding external JSON dependencies. The format is small and versioned.</p>
 *
 * <p>Version 2 adds an optional {@code customRules} array containing strings in the form
 * {@code id|pattern|ACTION} (id optional):</p>
 * <pre>
 * {
 *   "version": 2,
 *   "profile": "CUSTOM",
 *   "customDefaultAction": "PROMPT",
 *   "customRules": [
 *     "R1|glob:**&#47;*.tmp|SKIP",
 *     "glob:**&#47;Downloads&#47;**|OVERWRITE"
 *   ]
 * }
 * </pre>
 */
public final class ConflictPolicyJsonService {

    private static final int VERSION = 2;

    // Minimal patterns for our tiny JSON format (no external JSON deps)
    private static final Pattern PROFILE = Pattern.compile("\"profile\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CUSTOM = Pattern.compile("\"customDefaultAction\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RULES_BLOCK = Pattern.compile("\"customRules\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern RULE_ITEM = Pattern.compile("\"([^\"]*)\"");

    private ConflictPolicyJsonService() {}

    public static String exportJson(ConflictPolicyConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        ConflictPolicyProfile p = cfg.profile();
        ConflictPolicyAction a = cfg.customDefaultAction();
        if (p == null) p = ConflictPolicyProfile.DEFAULT;
        if (a == null) a = ConflictPolicyAction.PROMPT;

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": ").append(VERSION).append(",\n");
        sb.append("  \"profile\": \"").append(p.name()).append("\",\n");
        sb.append("  \"customDefaultAction\": \"").append(a.name()).append("\"");

        List<ConflictRule> rules = cfg.customRules();
        if (rules != null && !rules.isEmpty()) {
            sb.append(",\n  \"customRules\": [\n");
            boolean first = true;
            for (ConflictRule r : rules) {
                if (r == null) continue;
                if (!first) sb.append(",\n");
                first = false;
                String line = (r.id() == null || r.id().isBlank())
                        ? (r.pattern() + "|" + r.action().name())
                        : (r.id() + "|" + r.pattern() + "|" + r.action().name());
                sb.append("    \"").append(escape(line)).append("\"");
            }
            sb.append("\n  ]");
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    public static ConflictPolicyConfig importJson(String json) {
        Objects.requireNonNull(json, "json");
        String profileRaw = groupOrNull(PROFILE, json);
        String customRaw = groupOrNull(CUSTOM, json);

        ConflictPolicyProfile profile = parseProfile(profileRaw);
        ConflictPolicyAction action = parseAction(customRaw);

        List<ConflictRule> rules = parseRules(json);
        return new ConflictPolicyConfig(profile, action, rules);
    }

    private static String groupOrNull(Pattern p, String s) {
        Matcher m = p.matcher(s);
        if (!m.find()) return null;
        return m.group(1);
    }

    private static ConflictPolicyProfile parseProfile(String raw) {
        if (raw == null) return ConflictPolicyProfile.DEFAULT;
        try {
            return ConflictPolicyProfile.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return ConflictPolicyProfile.DEFAULT;
        }
    }

    private static ConflictPolicyAction parseAction(String raw) {
        if (raw == null) return ConflictPolicyAction.PROMPT;
        try {
            return ConflictPolicyAction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return ConflictPolicyAction.PROMPT;
        }
    }

    private static List<ConflictRule> parseRules(String json) {
        Matcher block = RULES_BLOCK.matcher(json);
        if (!block.find()) return List.of();
        String inside = block.group(1);
        Matcher items = RULE_ITEM.matcher(inside);
        List<String> lines = new ArrayList<>();
        while (items.find()) {
            lines.add(unescape(items.group(1)));
        }
        if (lines.isEmpty()) return List.of();

        List<ConflictRule> out = new ArrayList<>();
        int n = 0;
        for (String line : lines) {
            if (line == null) continue;
            String t = line.trim();
            if (t.isEmpty()) continue;

            String[] parts = t.split("\\|", -1);
            try {
                if (parts.length >= 3) {
                    String id = parts[0].trim();
                    String pattern = parts[1].trim();
                    String actionRaw = parts[2].trim();
                    if (id.isEmpty()) id = "R" + (++n);
                    ConflictPolicyAction a = ConflictPolicyAction.valueOf(actionRaw.toUpperCase(Locale.ROOT));
                    out.add(new ConflictRule(id, pattern, a));
                } else if (parts.length == 2) {
                    String id = "R" + (++n);
                    String pattern = parts[0].trim();
                    String actionRaw = parts[1].trim();
                    ConflictPolicyAction a = ConflictPolicyAction.valueOf(actionRaw.toUpperCase(Locale.ROOT));
                    out.add(new ConflictRule(id, pattern, a));
                }
            } catch (Exception ignored) {
                // ignore invalid
            }
        }
        return List.copyOf(out);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        // minimal unescape for our own format
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
