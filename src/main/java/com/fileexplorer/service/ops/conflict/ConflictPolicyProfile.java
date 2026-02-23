package com.fileexplorer.service.ops.conflict;

/**
 * Phase 4.2.0: Named conflict policy profiles.
 */
public enum ConflictPolicyProfile {
    /** Prompt via Conflict Queue UI (default behavior). */
    DEFAULT,

    /** Prefer safety: skip conflicts unless request explicitly forces overwrite. */
    CONSERVATIVE,

    /** Prefer throughput: overwrite conflicts unless request explicitly disables overwrite. */
    AGGRESSIVE,

    /** Mirror semantics (like sync): overwrite file conflicts for COPY/MOVE; otherwise prompt. */
    MIRROR,

    /** User-configured policy using Preferences. */
    CUSTOM
}
