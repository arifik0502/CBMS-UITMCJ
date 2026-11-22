# CBMS Admin v0.5.1

## Updater hotfix

- Fixed APK downloads from GitHub Releases when GitHub redirects the browser download URL to its release-assets CDN.
- Accepts Android APK, ZIP and generic binary MIME types only from GitHub-controlled download hosts, or a GitHub APK path.
- Downloads to a temporary file, checks that it is a valid APK for `com.crispyburger.admin`, and verifies its version code is newer before opening Android's installer.
- Removes incomplete temporary downloads after errors.

## Android APK

- Package: `com.crispyburger.admin`
- Version: `0.5.1`
- Version code: `15`
- Signed with the same CBMS Admin release certificate as v0.5.0.
- SHA-256: `BCAD587772C82C8D47446C45AF58E7C638A0E807983FAE5FEC93803B8724A99B`

## Verification

- 89 Android unit tests passed; 0 failures and 0 errors.
- The five new updater tests cover GitHub CDN redirects, Android APK MIME type, generic binary MIME type, HTML rejection, and untrusted host rejection.
- APK signature verification passed.
- Existing v0.5.0 installations can install this update over the existing package. Android may ask the user to allow CBMS Admin to install unknown apps and to confirm the installation.
