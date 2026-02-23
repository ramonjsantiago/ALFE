package com.fileexplorer.service.ops.journal;

import java.util.List;

/**
 * Phase 4.5.0: Recovery scanner + helper facade.
 */
public final class OperationRecoveryService {

    private final OperationJournalService journalService;

/**
 * OperationRecoveryService.
 *
 * @return TODO
 */
    public OperationRecoveryService() {
        this(new OperationJournalService());
    }

/**
 * OperationRecoveryService.
 *
 * @param journalService TODO
 * @return TODO
 */
    public OperationRecoveryService(OperationJournalService journalService) {
        this.journalService = journalService;
    }

    public OperationJournalService journalService() {
        return journalService;
    }

    /**
     * Returns incomplete (non-completed) journals.
     */
    public List<OperationJournalService.RecoveryCandidate> findRecoveryCandidates() {
        return journalService.findIncomplete();
    }
}
