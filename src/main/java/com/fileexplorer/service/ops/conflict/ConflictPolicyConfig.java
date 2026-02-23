package com.fileexplorer.service.ops.conflict;

import java.util.List;
import java.util.Objects;

/**
 * Phase 4.2.0: Snapshot of the conflict policy configuration applied to an operation.
 *
 * <p>Phase 7.0.0 extends this with ordered CUSTOM rules.</p>
 */
public record ConflictPolicyConfig(
        ConflictPolicyProfile profile,
        ConflictPolicyAction customDefaultAction,
        List<ConflictRule> customRules
) {
    public ConflictPolicyConfig {
        Objects.requireNonNull(profile, "profile");
        if (customDefaultAction == null) {
            customDefaultAction = ConflictPolicyAction.PROMPT;
        }
        if (customRules == null) {
            customRules = List.of();
        }
    }

    public ConflictPolicyConfig(ConflictPolicyProfile profile, ConflictPolicyAction customDefaultAction) {
        this(profile, customDefaultAction, List.of());
    }
}
