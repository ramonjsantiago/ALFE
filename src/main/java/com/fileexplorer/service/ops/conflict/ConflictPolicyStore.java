package com.fileexplorer.service.ops.conflict;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Phase 4.2.0: Preferences-backed store for conflict policy selection.
 */
public final class ConflictPolicyStore {

    public static final String KEY_PROFILE = "fileexplorer.conflictPolicy.profile";
    public static final String KEY_CUSTOM_DEFAULT = "fileexplorer.conflictPolicy.customDefault";
    public static final String KEY_CUSTOM_RULES = "fileexplorer.conflictPolicy.customRules";

    private final Preferences prefs;

/**
 * ConflictPolicyStore.
 *
 * @param prefs TODO
 * @return TODO
 */
    public ConflictPolicyStore(Preferences prefs) {
        this.prefs = Objects.requireNonNull(prefs, "prefs");
    }

    public ConflictPolicyConfig snapshot() {
        return new ConflictPolicyConfig(getProfile(), getCustomDefaultAction(), getCustomRules());
    }

    /**
     * Phase 7.0.0: Returns the persisted ordered CUSTOM rules.
     *
     * <p>Stored in preferences as newline-delimited rule lines. Each line is:
     * {@code id|pattern|ACTION}. Empty lines and comment lines starting with {@code #} are ignored.</p>
     */
    public List<ConflictRule> getCustomRules() {
        String raw = prefs.get(KEY_CUSTOM_RULES, "");
        if (raw == null || raw.isBlank()) return List.of();

        String[] lines = raw.split("\\r?\\n");
        List<ConflictRule> out = new ArrayList<>();
        int n = 0;
        for (String line : lines) {
            if (line == null) continue;
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("#")) continue;

            String[] parts = t.split("\\|", -1);
            try {
                if (parts.length >= 3) {
                    String id = parts[0].trim();
                    String pattern = parts[1].trim();
                    String actionRaw = parts[2].trim();
                    if (id.isEmpty()) id = "R" + (++n);
                    ConflictPolicyAction a = ConflictPolicyAction.valueOf(actionRaw.toUpperCase(Locale.ROOT));
                    if (!pattern.isEmpty()) out.add(new ConflictRule(id, pattern, a));
                } else if (parts.length == 2) {
                    String id = "R" + (++n);
                    String pattern = parts[0].trim();
                    String actionRaw = parts[1].trim();
                    ConflictPolicyAction a = ConflictPolicyAction.valueOf(actionRaw.toUpperCase(Locale.ROOT));
                    if (!pattern.isEmpty()) out.add(new ConflictRule(id, pattern, a));
                }
            } catch (Exception ignored) {
                // ignore invalid
            }
        }
        return List.copyOf(out);
    }

    /**
     * Phase 7.0.0: Persists ordered CUSTOM rules.
     */
    public void setCustomRules(List<ConflictRule> rules) {
        if (rules == null || rules.isEmpty()) {
            prefs.remove(KEY_CUSTOM_RULES);
            return;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ConflictRule r : rules) {
            if (r == null) continue;
            String id = r.id();
            if (id == null || id.isBlank()) id = "R";
            String pattern = r.pattern() == null ? "" : r.pattern().trim();
            if (pattern.isEmpty()) continue;
            ConflictPolicyAction a = r.action() == null ? ConflictPolicyAction.PROMPT : r.action();

            if (!first) sb.append('\n');
            first = false;
            sb.append(id.trim()).append('|').append(pattern).append('|').append(a.name());
        }
        prefs.put(KEY_CUSTOM_RULES, sb.toString());
    }

/**
 * getProfile.
 *
 * @return TODO
 */
    public ConflictPolicyProfile getProfile() {
        String raw = prefs.get(KEY_PROFILE, ConflictPolicyProfile.DEFAULT.name());
        try {
            return ConflictPolicyProfile.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return ConflictPolicyProfile.DEFAULT;
        }
    }

/**
 * setProfile.
 *
 * @param profile TODO
 */
    public void setProfile(ConflictPolicyProfile profile) {
        if (profile == null) profile = ConflictPolicyProfile.DEFAULT;
        prefs.put(KEY_PROFILE, profile.name());
    }

/**
 * getCustomDefaultAction.
 *
 * @return TODO
 */
    public ConflictPolicyAction getCustomDefaultAction() {
        String raw = prefs.get(KEY_CUSTOM_DEFAULT, ConflictPolicyAction.PROMPT.name());
        try {
            return ConflictPolicyAction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return ConflictPolicyAction.PROMPT;
        }
    }

/**
 * setCustomDefaultAction.
 *
 * @param action TODO
 */
    public void setCustomDefaultAction(ConflictPolicyAction action) {
        if (action == null) action = ConflictPolicyAction.PROMPT;
        prefs.put(KEY_CUSTOM_DEFAULT, action.name());
    }
}
