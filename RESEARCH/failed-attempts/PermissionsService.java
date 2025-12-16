package com.fileexplorer.ui;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/**
 * PermissionsService
 *  - Reads file permissions on Windows/Mac/Linux
 *  - Normalizes output into a simple Map
 */
public class PermissionsService {

    public Map<String, Object> read(Path file) {
        Map<String, Object> info = new HashMap<>();

        try {
            Set<PosixFilePermission> posix = null;

            try {
                posix = Files.getPosixFilePermissions(file);
            } catch (UnsupportedOperationException ignored) {
            }

            if (posix != null) {
                info.put("type", "posix");
                info.put("permissions", PosixFilePermissions.toString(posix));
            } else {
                AclFileAttributeView aclView =
                    Files.getFileAttributeView(file, AclFileAttributeView.class);

                if (aclView != null) {
                    info.put("type", "acl");
                    info.put("acl", aclView.getAcl());
                } else {
                    info.put("type", "basic");
                    info.put("readable", Files.isReadable(file));
                    info.put("writable", Files.isWritable(file));
                    info.put("executable", Files.isExecutable(file));
                }
            }
        } catch (Exception e) {
            info.put("error", "Unable to read permissions");
        }

        return info;
    }
}