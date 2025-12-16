package com.fileexplorer.ui;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * ChecksumService
 *  - Computes MD5, SHA-1, SHA-256
 *  - Useful for integrity info in metadata panels
 */
public class ChecksumService {

    public String checksum(Path file, String algorithm) {
        try {
            MessageDigest dig = MessageDigest.getInstance(algorithm);

            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) {
                    dig.update(buf, 0, r);
                }
            }

            return HexFormat.of().formatHex(dig.digest());
        } catch (Exception e) {
            return "error";
        }
    }

    public String md5(Path file) {
        return checksum(file, "MD5");
    }

    public String sha1(Path file) {
        return checksum(file, "SHA-1");
    }

    public String sha256(Path file) {
        return checksum(file, "SHA-256");
    }
}