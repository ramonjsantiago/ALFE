package com.fileexplorer.ui;

/**
 * Backward-compatible wrapper. Prefer using com.fileexplorer.service.CursorService directly.
 * This wrapper exists only to prevent old code from referencing IcoCurDecoder incorrectly.
 */
public final class CursorService {

    private final com.fileexplorer.service.CursorService delegate;

    public CursorService() {
        this.delegate = new com.fileexplorer.service.CursorService();
    }

    public com.fileexplorer.service.CursorService delegate() {
        return delegate;
    }
}
