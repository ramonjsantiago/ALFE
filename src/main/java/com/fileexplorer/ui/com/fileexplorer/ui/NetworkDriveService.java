package com.fileexplorer.ui;

import java.nio.file.Path;

/**
 * NetworkDriveService
 *  - Placeholder for SMB/WebDAV/mapped network drives
 *  - Extend with JCIFS or WebDAV client
 */
public class NetworkDriveService {

    public boolean isNetworkDrive(Path path) {
        // TODO: implement actual network detection
        return false;
    }

    public String getDriveName(Path path) {
        return path.getFileName().toString();
    }
}
