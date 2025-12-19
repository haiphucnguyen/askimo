# macOS Code Signing Setup Guide

Complete guide for signing and notarizing the Askimo macOS application with an Apple Developer ID certificate.

**⚠️ Test locally first!** Use the [automated test script](#step-6-test-locally-before-using-github-actions) before setting up GitHub Actions.

## Prerequisites

- Apple Developer Account ($99/year)
- Developer ID Application certificate
- macOS machine with Xcode Command Line Tools

## Quick Start


### 1. Export Certificate

```bash
# Open Keychain Access on your Mac
# Find: "Developer ID Application: Your Name (Team ID)"
# Right-click → Export as certificate.p12
# Set a password (save it!)
```

### 2. Convert to Base64

```bash
base64 -i certificate.p12 -o certificate-base64.txt
```

### 3. Add GitHub Secrets

Go to: **Repository → Settings → Secrets and variables → Actions → New secret**

**Required:**
- `MACOS_CERTIFICATE_BASE64` → Content of `certificate-base64.txt`
- `MACOS_CERTIFICATE_PASSWORD` → Your .p12 password
- `APPLE_TEAM_ID` → Your 10-character Team ID

**Optional (for notarization):**
- `APPLE_ID` → Your Apple ID email
- `APPLE_PASSWORD` → App-specific password from appleid.apple.com

### 4. Test Release

```bash
git tag v0.3.1-test
git push origin v0.3.1-test
```

Watch in GitHub Actions tab!

### What Was Changed

✅ **`desktop/build.gradle.kts`** - Added signing and notarization configuration for macOS

✅ **`.github/workflows/desktop-release.yml`** - Added certificate import step that:
- Decodes your certificate
- Creates temporary keychain
- Imports certificate
- Signs the app during build

### Result

When you push a version tag:
1. GitHub Actions imports your certificate
2. Gradle signs the macOS app
3. (Optional) Apple notarizes it
4. Signed DMG uploaded to release

Users can install without security warnings! 🎉

---

## Detailed Setup Instructions

### Prerequisites

- Apple Developer Account with a paid membership
- Developer ID Application certificate
- Access to the Mac where the certificate was created

## Step 1: Export Certificate as .p12

1. Open **Keychain Access** on your Mac
2. Select the **login** keychain (left sidebar)
3. Select **My Certificates** category (left sidebar)
4. Find your certificate named **"Developer ID Application: Your Name (Team ID)"**
5. Right-click the certificate → **Export "Developer ID Application..."**
6. Choose a location and save as `.p12` format
7. Enter a **strong password** when prompted (you'll need this for GitHub Secrets)
8. Save the password securely

## Step 2: Convert Certificate to Base64

Open Terminal and run:

```bash
base64 -i /path/to/your/certificate.p12 -o certificate-base64.txt
```

This creates a text file with the base64-encoded certificate.

## Step 3: Find Your Apple Team ID

1. Go to [Apple Developer Account](https://developer.apple.com/account)
2. Click on **Membership** in the sidebar
3. Your **Team ID** is a 10-character alphanumeric code (e.g., `ABCDE12345`)
4. Copy this ID

## Step 4: Create App-Specific Password (for Notarization)

Notarization is optional but highly recommended for better user experience.

1. Go to [Apple ID Account Page](https://appleid.apple.com)
2. Sign in with your Apple Developer ID
3. Navigate to **Security** → **App-Specific Passwords**
4. Click **Generate an app-specific password**
5. Enter a label like "Askimo Notarization"
6. Copy the generated password (format: `xxxx-xxxx-xxxx-xxxx`)

## Step 5: Add GitHub Secrets

Go to your GitHub repository:

1. Click **Settings** → **Secrets and variables** → **Actions**
2. Click **New repository secret**
3. Add the following secrets:

### Required Secrets

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `MACOS_CERTIFICATE_BASE64` | Content of `certificate-base64.txt` | Your certificate in base64 format |
| `MACOS_CERTIFICATE_PASSWORD` | Your .p12 password | Password you set when exporting |
| `APPLE_TEAM_ID` | Your 10-character Team ID | Found in Apple Developer portal |

### Optional Secrets (for Notarization)

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `APPLE_ID` | Your Apple ID email | Email used for Apple Developer account |
| `APPLE_PASSWORD` | App-specific password | Generated in Step 4 |

**Important**: The `APPLE_PASSWORD` should be the app-specific password, NOT your regular Apple ID password.

## Step 6: Test Locally Before Using GitHub Actions

**⚠️ IMPORTANT**: Test signing and notarization locally BEFORE setting up GitHub Actions to avoid wasting time debugging in CI.

### Testing Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                    Local Testing Workflow                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │ 1. Get Developer │
                    │ ID Certificate   │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ 2. Export as .p12│
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ 3. Set up env    │
                    │ variables        │
                    └────────┬─────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
                ▼                         ▼
     ┌──────────────────┐      ┌──────────────────┐
     │ Automated Test   │      │ Manual Testing   │
     │ (Recommended)    │      │ (Step by step)   │
     └────────┬─────────┘      └────────┬─────────┘
              │                         │
              └────────────┬────────────┘
                           │
                           ▼
                  ┌────────────────┐
                  │ All tests pass?│
                  └────────┬───────┘
                           │
                 ┌─────────┴──────────┐
                 │                    │
              YES│                    │NO
                 │                    │
                 ▼                    ▼
        ┌────────────────┐   ┌────────────────┐
        │ Set up GitHub  │   │ Fix issues &   │
        │ Actions        │   │ retry          │
        └────────────────┘   └────────┬───────┘
                                      │
                                      └──────┐
                                             │
                                             ▼
                                  (Back to testing)
```

### Quick Automated Test (Recommended)

**First, install Apple's intermediate certificates (required for signing):**

```bash
# Install Developer ID Certification Authority (G1 - older certificates)
curl -O https://www.apple.com/certificateauthority/DeveloperIDCA.cer
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain DeveloperIDCA.cer
rm DeveloperIDCA.cer

# Install Developer ID Certification Authority (G2 - newer certificates)
curl -O https://www.apple.com/certificateauthority/DeveloperIDG2CA.cer
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain DeveloperIDG2CA.cer
rm DeveloperIDG2CA.cer

# Install Apple Root CA
curl -O https://www.apple.com/appleca/AppleIncRootCertificate.cer
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain AppleIncRootCertificate.cer
rm AppleIncRootCertificate.cer

echo "✅ Apple certificates installed"
```

**Note:** Apple now issues both G1 and G2 certificates. Install both to ensure compatibility.

**Next, ensure your keychain is properly set up:**

```bash
# Unlock your keychain
security unlock-keychain ~/Library/Keychains/login.keychain-db
# (Enter your macOS password when prompted)

# Allow codesign to access the signing key without prompting
# IMPORTANT: Replace YOUR_ACTUAL_PASSWORD with your real macOS login password
security set-key-partition-list -S apple-tool:,apple: -s \
  -k YOUR_ACTUAL_PASSWORD \
  ~/Library/Keychains/login.keychain-db
```

**Then, verify your Developer ID certificate is installed:**

```bash
# List all Developer ID certificates
security find-identity -v -p codesigning

# Expected output should include:
# 1) ABCDEF1234567890... "Developer ID Application: Your Name (TEAM_ID)"
```

If you don't see your certificate, import the .p12 file:

```bash
# Import certificate to login keychain
security import certificate.p12 -k ~/Library/Keychains/login.keychain-db

# You'll be prompted for:
# 1. The .p12 password
# 2. Your macOS login password
```

### 6.2 Find Your Certificate Identity

Find the exact identity string for signing:

```bash
# This shows your certificate's full name
security find-identity -v -p codesigning | grep "Developer ID Application"

# Example output:
# 1) ABCD1234... "Developer ID Application: John Doe (XYZ123456)"
```

Copy the full name including quotes (e.g., `Developer ID Application: John Doe (XYZ123456)`).

### 6.3 Set Up Environment Variables

Set up your signing configuration in the `.env` file:

```bash
# Copy the example environment file
cp .env.example .env

# Edit the macOS Code Signing section in .env
nano .env  # or use your preferred editor

# Fill in these values in the macOS Code Signing section:
#   MACOS_IDENTITY="Developer ID Application: Your Name (TEAM_ID)"
#   APPLE_TEAM_ID="XYZ123456"
#   APPLE_ID="your@email.com"
#   APPLE_PASSWORD="xxxx-xxxx-xxxx-xxxx"
```

### 6.4 Test DMG Creation with Signing

Test the full DMG creation with signing (this will also build the app):

```bash
# Build and sign the DMG
./gradlew desktop:packageDmg

# This will:
# 1. Build the app
# 2. Sign the app bundle
# 3. Create and sign the DMG
```

**Watch for errors** like:
- `No identity found` → Check your `MACOS_IDENTITY` value in .env
- `User interaction is not allowed` → You may need to unlock your keychain
- `The specified item could not be found` → Certificate not in keychain

### 6.5 Verify the Signature

After successful build, verify the signature:

```bash
# Find the DMG file
DMG_PATH=$(find desktop/build/compose/binaries/main/dmg -name "*.dmg" -type f | head -n1)
echo "Testing DMG: $DMG_PATH"

# Mount the DMG
hdiutil attach "$DMG_PATH"

# Verify app signature (detailed)
codesign -dv --verbose=4 "/Volumes/Askimo/Askimo.app"

# Check if properly signed and notarized
spctl -a -vv -t install "/Volumes/Askimo/Askimo.app"

# Unmount when done
hdiutil detach "/Volumes/Askimo"
```

### Expected Output for Properly Signed App

```
Executable=/Volumes/Askimo/Askimo.app/Contents/MacOS/Askimo
Identifier=io.askimo.desktop
Format=app bundle with Mach-O universal (x86_64 arm64)
CodeDirectory v=20500 size=...
Authority=Developer ID Application: Your Name (TEAM_ID)
Authority=Developer ID Certification Authority
Authority=Apple Root CA
Timestamp=Jan 15, 2025 at 10:30:45 AM
```

✅ **Good signs**:
- Shows your Developer ID Application certificate
- Shows Apple Root CA in chain
- Has a timestamp

❌ **Bad signs**:
- Shows "adhoc" signature → Not properly signed
- No timestamp → Signing failed
- Missing certificate chain → Certificate not trusted

### 6.6 Test Notarization (Optional but Recommended)

Notarization requires uploading to Apple and waiting for approval:

```bash
# Ensure notarization variables are set in .env
# (APPLE_ID and APPLE_PASSWORD)

# Build with notarization
./gradlew desktop:packageDmg

# The Gradle task will:
# 1. Sign the app
# 2. Create DMG
# 3. Submit to Apple for notarization
# 4. Wait for approval (can take 5-30 minutes)
# 5. Staple the notarization ticket to DMG
```

**Notarization Output**: Look for these messages in the Gradle output:

```
> Notarizing application...
> Uploading to Apple...
> Waiting for notarization...
> Notarization successful
> Stapling ticket to DMG...
```

**Verify notarization**:

```bash
# Check if notarization ticket is stapled
stapler validate desktop/build/compose/binaries/main/dmg/Askimo-*.dmg

# Expected output:
# The validate action worked!
```

If notarization fails, check:
- ✅ `APPLE_ID` is correct
- ✅ `APPLE_PASSWORD` is an **app-specific password** (not your regular password)
- ✅ `APPLE_TEAM_ID` matches your developer account
- ✅ Your Apple Developer membership is active

### 6.7 Test User Experience

Simulate what a user would experience:

```bash
# Find your DMG
DMG_PATH=$(find desktop/build/compose/binaries/main/dmg -name "*.dmg" -type f | head -n1)

# Copy to Desktop to simulate download
cp "$DMG_PATH" ~/Desktop/

# Open from Desktop (like a user would)
open ~/Desktop/$(basename "$DMG_PATH")

# Mount and try to open the app
# macOS should either:
# 1. Open immediately (if notarized)
# 2. Show "verifying..." briefly then open (if notarized)
# 3. Show security warning (if only signed, not notarized)
# 4. Block completely (if unsigned)
```

**What you should see**:
- ✅ **Notarized**: Brief "verifying" message, then app opens
- ⚠️ **Signed only**: Security warning, but can open via System Preferences
- ❌ **Unsigned**: App is blocked, harder for users to open

### 6.8 Clean Up Test Files

After testing:

```bash
# Remove test DMG from Desktop
rm ~/Desktop/Askimo-*.dmg

# Clean build directory
./gradlew clean

# Note: Keep your .env file for future builds
# It's already in .gitignore and won't be committed
```

### 6.9 Troubleshooting Local Testing

#### Keychain Access Denied

```
Error: User interaction is not allowed.
```

**Solution**: Unlock your keychain

```bash
# Unlock login keychain
security unlock-keychain ~/Library/Keychains/login.keychain-db

# Or allow codesign to access the key without prompting
security set-key-partition-list -S apple-tool:,apple: -s \
  -k YOUR_KEYCHAIN_PASSWORD \
  ~/Library/Keychains/login.keychain-db
```

#### Certificate Not Found

```
Error: No identity found for signing
```

**Solution**: Verify certificate identity

```bash
# List all signing identities
security find-identity -v -p codesigning

# Make sure MACOS_IDENTITY matches exactly
echo $MACOS_IDENTITY
```

#### Notarization Timeout

```
Error: Notarization request timed out
```

**Solution**: Apple's servers can be slow. Try:
- Wait and retry (can take up to 30 minutes)
- Check Apple Developer account status
- Verify credentials are correct

### 6.10 Local Testing Checklist

Before setting up GitHub Actions, verify locally:

- [ ] Certificate is installed in keychain
- [ ] `.env` file is configured with your credentials
- [ ] Can create DMG with signing (`./gradlew desktop:packageDmg`)
- [ ] DMG signature verifies (`codesign -dv`)
- [ ] App passes Gatekeeper check (`spctl -a -vv`)
- [ ] (Optional) Notarization succeeds
- [ ] (Optional) Notarization ticket stapled (`stapler validate`)
- [ ] Can open app without security warnings

✅ **Once all checks pass**, you're ready to set up GitHub Actions with confidence!

## Step 7: Trigger Release

Push a version tag to trigger the release workflow:

```bash
# Create and push a tag
git tag v0.3.1
git push origin v0.3.1
```

## Step 8: Monitor the Build

1. Go to **Actions** tab in your GitHub repository
2. Watch the **Desktop** workflow run
3. Check the **desktop-package-macos** job
4. Look for the "Import Code Signing Certificate" step

### What Happens During Build

1. ✅ Certificate is decoded from base64
2. ✅ Temporary keychain is created
3. ✅ Certificate is imported to keychain
4. ✅ App is built and signed during `packageDmg`
5. ✅ (Optional) App is notarized with Apple
6. ✅ DMG is uploaded to GitHub Release

## Verification

After the release is published:

1. Download the `Askimo-Desktop-macos.dmg` from the release
2. Mount the DMG and try to open the app
3. If properly signed:
   - ✅ No "unidentified developer" warning
   - ✅ App opens without security prompts
4. If notarized:
   - ✅ macOS shows a brief "verifying" dialog
   - ✅ App opens smoothly

## Troubleshooting

### "No identity found" error

**Problem**: The certificate identity cannot be found in the keychain.

**Solution**:
- Verify `MACOS_CERTIFICATE_BASE64` is correct (no extra spaces/newlines)
- Verify `MACOS_CERTIFICATE_PASSWORD` is correct
- Check GitHub Actions logs for certificate import errors

### "Code signing failed" error

**Problem**: The signing process fails during build.

**Solution**:
- Ensure the certificate is not expired (check in Apple Developer portal)
- Verify the certificate type is "Developer ID Application" (not "Mac App Distribution")
- Check that `MACOS_IDENTITY` environment variable is set correctly

### Notarization fails

**Problem**: App is signed but notarization times out or fails.

**Solution**:
- Verify `APPLE_ID` is correct
- Verify `APPLE_PASSWORD` is an app-specific password (not your regular password)
- Verify `APPLE_TEAM_ID` matches your Apple Developer account
- Check if your Apple Developer account has active paid membership

### Users still see security warnings

**Problem**: Downloaded app shows "unidentified developer" warning.

**Solutions**:
- Signing is working, but notarization might be missing
- Add notarization secrets (`APPLE_ID`, `APPLE_PASSWORD`)
- Wait 5-10 minutes after release for Apple's servers to update
- Clear macOS's security cache: `sudo spctl --master-disable` then `sudo spctl --master-enable`

## Certificate Renewal

Apple Developer ID certificates are valid for **5 years**. When your certificate expires:

1. Generate a new certificate in Apple Developer portal
2. Export the new certificate as .p12
3. Convert to base64
4. Update `MACOS_CERTIFICATE_BASE64` and `MACOS_CERTIFICATE_PASSWORD` secrets in GitHub
5. No code changes needed - just update the secrets

## Security Best Practices

- ✅ **Never commit** .p12 files to the repository
- ✅ **Never commit** base64-encoded certificates to the repository
- ✅ Use GitHub Secrets for all sensitive data
- ✅ Rotate app-specific passwords periodically
- ✅ Limit repository access to trusted collaborators
- ✅ Use app-specific passwords, never your main Apple ID password
- ✅ Keep your .p12 file backed up securely (you'll need it if you change machines)

## What Gets Signed

The signing process signs:
- ✅ The main application bundle (`Askimo.app`)
- ✅ All embedded frameworks and libraries
- ✅ Native binaries and JVM runtime
- ✅ The DMG installer itself

## Cost

- Apple Developer Program: **$99/year** (required for code signing)
- Notarization: **Free** (included with Developer Program)

## References

- [Apple Developer Documentation](https://developer.apple.com/documentation/xcode/notarizing_macos_software_before_distribution)
- [Compose Multiplatform Signing Docs](https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials/Signing_and_notarization_on_macOS)
- [GitHub Actions Security Best Practices](https://docs.github.com/en/actions/security-guides/encrypted-secrets)

## Support

If you encounter issues:
1. Check the GitHub Actions workflow logs
2. Verify all secrets are set correctly
3. Test signing locally first
4. Check Apple Developer account status

