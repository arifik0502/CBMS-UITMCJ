package com.crispyburger.admin.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterDownloadTest {
    @Test
    fun acceptsAndroidApkMimeTypeAfterGithubRedirectWithoutApkPath() {
        assertTrue(
            isAllowedApkDownloadResponse(
                "application/vnd.android.package-archive",
                "https://release-assets.githubusercontent.com/github-production-release-asset/123/abc"
            )
        )
    }

    @Test
    fun acceptsOctetStreamFromGithubReleaseAssets() {
        assertTrue(
            isAllowedApkDownloadResponse(
                "application/octet-stream",
                "https://release-assets.githubusercontent.com/github-production-release-asset/123/abc"
            )
        )
    }

    @Test
    fun acceptsApkFilePathFromGithubEvenWithGenericMimeType() {
        assertTrue(
            isAllowedApkDownloadResponse(
                "binary/octet-stream",
                "https://github.com/arifik0502/CBMS-UITMCJ/releases/download/v0.5.1/admin.apk"
            )
        )
    }

    @Test
    fun rejectsHtmlResponseFromGithub() {
        assertFalse(
            isAllowedApkDownloadResponse(
                "text/html",
                "https://release-assets.githubusercontent.com/github-production-release-asset/123/abc"
            )
        )
    }

    @Test
    fun rejectsApkFromUntrustedHost() {
        assertFalse(
            isAllowedApkDownloadResponse(
                "application/vnd.android.package-archive",
                "https://example.com/fake.apk"
            )
        )
    }
}
