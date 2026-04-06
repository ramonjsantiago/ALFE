package com.fileexplorer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSupportTest {

    @Test
    void thumbnailCandidatesIncludeWebpAndAvifFamilies() {
        assertTrue(ImageSupport.isThumbCandidateExtension("webp"));
        assertTrue(ImageSupport.isThumbCandidateExtension("avif"));
        assertTrue(ImageSupport.isThumbCandidateExtension("heif"));
        assertTrue(ImageSupport.isThumbCandidateExtension("heic"));
    }

    @Test
    void webpAndAvifFamiliesRouteThroughImageIoNotJavaFxNative() {
        assertTrue(ImageSupport.isImageIoManagedExtension("webp"));
        assertTrue(ImageSupport.isImageIoManagedExtension("avif"));
        assertTrue(ImageSupport.isImageIoManagedExtension("heif"));
        assertTrue(ImageSupport.isImageIoManagedExtension("heic"));

        assertFalse(ImageSupport.isJavaFxNativeExtension("webp"));
        assertFalse(ImageSupport.isJavaFxNativeExtension("avif"));
        assertFalse(ImageSupport.isJavaFxNativeExtension("heif"));
        assertFalse(ImageSupport.isJavaFxNativeExtension("heic"));
    }
}
