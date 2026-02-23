package com.fileexplorer.service.ops.conflict;

import java.util.Objects;

/**
 * Phase 7.0.0: Ordered conflict rule used by the CUSTOM policy profile.
 *
 * <p>Rules are evaluated in order. The first matching rule wins.</p>
 *
 * <p>Matching is performed against the destination path (normalized with '/' separators) and the destination
 * file name. Rule patterns use either {@code glob:} or {@code regex:} syntax. If no prefix is provided,
 * {@code glob:} is assumed.</p>
 */
public record ConflictRule(
        String id,
        String pattern,
        ConflictPolicyAction action
) {
    public ConflictRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(action, "action");
    }
}
