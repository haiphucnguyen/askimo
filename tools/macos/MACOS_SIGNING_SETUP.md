# macOS Code Signing and Notarization - Complete Guide

**Complete guide for code signing and notarizing Askimo Desktop for macOS distribution.**

---

## 📋 Table of Contents

1. [Quick Start](#quick-start)
2. [Prerequisites](#prerequisites)
3. [Initial Setup](#initial-setup)
4. [Local Code Signing](#local-code-signing)
5. [Building Signed Apps](#building-signed-apps)
6. [Notarization](#notarization)
7. [Gradle Integration](#gradle-integration)
8. [GitHub Actions](#github-actions)
9. [Troubleshooting](#troubleshooting)
10. [Reference](#reference)

---

## 🚀 Quick Start

**For impatient developers:**

```bash
# 1. Setup (one-time)
cp .env.example .env
# Edit .env with your Apple credentials
./tools/macos/test-signing-local.sh

# 2. Daily development (fast)
./gradlew :desktop:createDistributable

# 3. Release build (complete)
./gradlew :desktop:packageNotarizedDmg
```

**Done!** Read on for details.

---

## ✅ Prerequisites

### Required Accounts

- **Apple Developer Account** ($99/year) - https://developer.apple.com/programs/
- **Developer ID Application Certificate** - Created in Apple Developer portal
- **App-Specific Password** - Generated at https://appleid.apple.com

### Required Tools

- macOS (for code signing)
- Xcode Command Line Tools: `xcode-select --install`
- Java 21+

---

## 🔧 Initial Setup

### Step 1: Configure Environment

Create `.env` file from template:

```bash
cp .env.example .env
```

Add your credentials:

```bash
# macOS Code Signing
MACOS_IDENTITY=Developer ID Application: Your Name (TEAM_ID)
APPLE_TEAM_ID=YOUR_TEAM_ID
APPLE_ID=your@email.com
APPLE_PASSWORD=xxxx-xxxx-xxxx-xxxx  # App-specific password
```

**Finding your values:**

| Variable | How to Find |
|----------|-------------|
| `MACOS_IDENTITY` | `security find-identity -v -p codesigning` |
| `APPLE_TEAM_ID` | https://developer.apple.com/account → Membership |
| `APPLE_ID` | Your Apple ID email |
| `APPLE_PASSWORD` | Generate at https://appleid.apple.com → Security → App-Specific Passwords |

### Step 2: Generate App-Specific Password

**Important:** Use app-specific password, NOT your regular Apple ID password!

1. Go to https://appleid.apple.com
2. Sign in → Security → App-Specific Passwords
3. Click "Generate Password"
4. Label: "Askimo Notarization"
5. Copy password (format: xxxx-xxxx-xxxx-xxxx)
6. Add to `.env` as `APPLE_PASSWORD`

### Step 3: Install Developer ID Certificate

**Option A - Download existing:**
1. https://developer.apple.com/account/resources/certificates
2. Download "Developer ID Application"
3. Double-click to install

**Option B - Create new:**
1. Keychain Access → Certificate Assistant → Request Certificate
2. Upload CSR at developer.apple.com
3. Select "Developer ID Application"
4. Download and install

### Step 4: Run Automated Setup

```bash
./tools/macos/test-signing-local.sh
```

This comprehensive script:
- ✅ Installs Apple intermediate certificates
- ✅ Verifies your Developer ID certificate
- ✅ Configures keychain access
- ✅ Tests code signing
- ✅ Builds and signs the app
- ✅ Verifies everything works

**Expected time:** 5-10 minutes (first run)

---

## 🔐 Local Code Signing

### Understanding Code Signing

**What it does:**
- Proves the app came from you
- Ensures it hasn't been tampered with
- Allows macOS to trust it

**Certificate chain:**
```
Your App
  └─ Developer ID Application: Your Name (TEAM_ID)
      └─ Developer ID Certification Authority (G2)
          └─ Apple Root CA
```

All three must be installed and trusted.

### Manual Setup (if automated fails)

#### Install Apple Certificates

```bash
# Automated
./tools/macos/install-apple-certificates.sh

# Or manual
curl -O https://www.apple.com/certificateauthority/DeveloperIDG2CA.cer
security import DeveloperIDG2CA.cer -k ~/Library/Keychains/login.keychain-db -T /usr/bin/codesign -A
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain DeveloperIDG2CA.cer
```

#### Configure Keychain Access

```bash
security set-key-partition-list -S apple-tool:,apple:,codesign: ~/Library/Keychains/login.keychain-db
```

Enter your macOS login password when prompted.

#### Verify Setup

```bash
echo "test" > /tmp/test.txt
codesign --sign "Developer ID Application: Your Name (TEAM_ID)" /tmp/test.txt
# Should succeed with exit code 0
```

### Common Setup Issues

**Error: "unable to build chain to self-signed root"**
```bash
# Fix: Install Apple intermediate certificates
./tools/macos/install-apple-certificates.sh
```

**Error: "errSecInternalComponent"**
```bash
# Fix: Configure keychain partition list
security unlock-keychain ~/Library/Keychains/login.keychain-db
security set-key-partition-list -S apple-tool:,apple:,codesign: ~/Library/Keychains/login.keychain-db
```

---

## 🏗️ Building Signed Apps

### Build Commands Comparison

| Command | Time | Signed | Notarized | Use Case |
|---------|------|--------|-----------|----------|
| `createDistributable` | 2-5 min | ✅ | ❌ | Daily development |
| `packageDmg` | 2-5 min | ✅ | ❌ | Signed DMG |
| `notarizeAndStapleDmg` | 5-60 min | ✅ | ✅ | Notarize existing |
| `packageNotarizedDmg` | 10-70 min | ✅ | ✅ | **Release builds** ⭐ |

### Development Build (Fast)

```bash
./gradlew :desktop:createDistributable
```

**Output:** `desktop/build/compose/binaries/main/app/Askimo.app`
- ✅ Code signed
- ❌ No DMG
- ❌ Not notarized
- ⏱️ 2-5 minutes

### Signed DMG

```bash
./gradlew :desktop:packageDmg
```

**Output:** `desktop/build/compose/binaries/main/dmg/Askimo-<version>.dmg`
- ✅ Code signed
- ✅ DMG created
- ❌ Not notarized
- ⚠️ Users see warning on first launch

### Complete Release Build (Recommended)

```bash
./gradlew :desktop:packageNotarizedDmg
```

**Output:** Fully signed and notarized DMG
- ✅ Code signed
- ✅ DMG created
- ✅ Notarized
- ✅ Ticket stapled
- ✅ No warnings for users!
- ⏱️ 10-70 minutes

---

## 📱 Notarization

### What is Notarization?

**Apple's automated malware scan** that:
- Checks for malicious code
- Validates signatures
- Allows Gatekeeper to trust the app

### Code Signing vs Notarization

| Feature | Code Signing | Notarization |
|---------|-------------|--------------|
| Purpose | Prove app from you | Apple malware scan |
| Required | Yes (for distribution) | Recommended |
| User impact | App is trusted | No warnings |
| Time | During build | 5-60 min post-build |

### User Experience

**Without notarization:**
1. User opens app
2. "Cannot be opened..." warning
3. Right-click → Open → Confirm
4. Works fine after

**With notarization:**
1. User opens app
2. Opens immediately
3. No warnings! ✅

### Automatic Notarization (Integrated)

```bash
./gradlew :desktop:packageNotarizedDmg
```

**What happens:**
1. Builds and signs DMG
2. Uploads to Apple
3. Waits for approval (shows progress)
4. Staples ticket
5. Verifies

**Progress display:**
```
📤 Uploading to Apple...
⏳ Status: In Progress | Elapsed: 5:30
⏳ Status: In Progress | Elapsed: 15:45
✅ Notarization accepted!
📎 Stapling ticket...
✅ Complete!
```


### Do You Need Notarization?

| Scenario | Recommended? |
|----------|-------------|
| Local testing | ❌ No |
| Team testing (< 10) | ⚠️ Optional |
| Public distribution | ✅ **Yes** |
| Mac App Store | ✅ **Required** |

### About App Store Connect

**Important:** You do NOT need App Store Connect for notarization!

- ✅ **Notarization** - For direct distribution (what you're doing)
- ❌ **App Store Connect** - Only for Mac App Store sales

Notarization works for:
- Website downloads
- GitHub releases
- Email distribution
- Any direct download

---

## Create profile for local testing

```bash
xcrun notarytool store-credentials askimo-notary \                                              
  --apple-id "user_id" \
  --team-id "team_id"
```

## 🎯 Gradle Integration

### Available Tasks

#### Daily Development
```bash
./gradlew :desktop:createDistributable
```
- Fast: 2-5 minutes
- Signed app (no DMG)
- Not notarized

#### Build DMG
```bash
./gradlew :desktop:packageDmg
```
- Fast: 2-5 minutes
- Signed DMG
- Not notarized

#### Notarize Existing DMG
```bash
./gradlew :desktop:notarizeAndStapleDmg
```
- Must run after `packageDmg`
- Waits 5-60 minutes
- Staples ticket

#### Complete Release ⭐
```bash
./gradlew :desktop:packageNotarizedDmg
```
- Everything in one command
- Builds, signs, notarizes, staples
- 10-70 minutes total
- **Use for production releases**

### Configuration

Tasks automatically read from `.env`:
- `MACOS_IDENTITY`
- `APPLE_TEAM_ID`
- `APPLE_ID`
- `APPLE_PASSWORD`

If credentials missing, notarization is skipped.

### Typical Workflow

```bash
# Monday-Friday: Fast builds
./gradlew :desktop:createDistributable

# Release day
git tag v1.2.0
./gradlew :desktop:packageNotarizedDmg
git push origin v1.2.0
```

---

## 🤖 GitHub Actions

### Required Secrets

Add 6 secrets to GitHub (Settings → Secrets → Actions):

| Secret | Description |
|--------|-------------|
| `MACOS_CERTIFICATE_BASE64` | .p12 certificate in base64 |
| `MACOS_CERTIFICATE_PASSWORD` | Password for .p12 |
| `MACOS_IDENTITY` | Certificate identity string |
| `APPLE_TEAM_ID` | Your Apple Team ID |
| `APPLE_ID` | Your Apple ID email |
| `APPLE_PASSWORD` | App-specific password |

### Generate MACOS_CERTIFICATE_BASE64

```bash
# 1. Export from Keychain
# Keychain Access → Find certificate → Right-click → Export
# Save as Certificates.p12 with password

# 2. Convert to base64
base64 -i Certificates.p12 | pbcopy

# 3. Paste into GitHub secret
```

### Workflow Behavior

**On tag push (v*):**
1. Installs certificates
2. Configures signing
3. Builds app
4. Creates signed DMG
5. Submits for notarization
6. Waits (5-60 min)
7. Staples ticket
8. Verifies
9. Uploads to release

**Total time:** 10-70 minutes

### Trigger Release

```bash
git tag v1.2.0
git push origin v1.2.0

# Watch at: https://github.com/your-repo/actions
```

---

## 🔧 Troubleshooting

### Code Signing Errors

#### "unable to build chain to self-signed root"
```bash
./tools/macos/install-apple-certificates.sh
```

#### "errSecInternalComponent"
```bash
security unlock-keychain ~/Library/Keychains/login.keychain-db
security set-key-partition-list -S apple-tool:,apple:,codesign: ~/Library/Keychains/login.keychain-db
```

#### "No identity found"
1. Download certificate from developer.apple.com
2. Install in Keychain Access
3. Verify: `security find-identity -v -p codesigning`

### Notarization Errors

#### "Authentication failed"
- Verify `APPLE_ID` is correct
- Generate new app-specific password
- Update `APPLE_PASSWORD` in `.env`
- Don't use regular Apple ID password!

#### "Notarization failed - Invalid"
```bash
# Check detailed log
./tools/macos/check-notarization.sh <submission-id>
```

Common issues:
- Hardened runtime not enabled
- Unsigned nested components
- Restricted APIs used

#### Notarization Hangs
- Wait up to 90 minutes
- Check separately: `./tools/macos/check-notarization.sh`
- Updated script shows progress every 30s

### Build Errors

#### "DMG not found"
```bash
./gradlew clean :desktop:packageDmg --info
# Fix compilation errors, then retry
```

#### GitHub Actions fails
1. Verify all 6 secrets are set
2. Re-export certificate
3. Test locally first
4. Check workflow logs for details

---

## 📚 Reference

### Helper Scripts

All in `tools/macos/`:

| Script | Purpose |
|--------|---------|
| `test-signing-local.sh` | Complete end-to-end test |
| `install-apple-certificates.sh` | Install certificates |
| `notarize-dmg.sh` | Notarize a DMG |
| `check-notarization.sh` | Check status |
| `verify-dmg.sh` | Verify signatures |
| `diagnose-codesigning.sh` | Diagnostic report |

### Verification Commands

```bash
# Check certificates
security find-certificate -c "Developer ID Certification Authority" ~/Library/Keychains/login.keychain-db

# List identities
security find-identity -v -p codesigning

# Verify signature
codesign -vvv --deep --strict /path/to/Askimo.app

# Check notarization
xcrun stapler validate /path/to/Askimo.dmg
spctl -a -vv -t install /path/to/Askimo.dmg

# View submissions
xcrun notarytool history --apple-id "$APPLE_ID" --team-id "$APPLE_TEAM_ID" --password "$APPLE_PASSWORD"
```

### Quick Command Reference

```bash
# Setup
./tools/macos/test-signing-local.sh
./tools/macos/diagnose-codesigning.sh

# Build
./gradlew :desktop:createDistributable
./gradlew :desktop:packageDmg
./gradlew :desktop:packageNotarizedDmg

# Notarize
./tools/macos/notarize-dmg.sh
./tools/macos/check-notarization.sh

# Verify
./tools/macos/verify-dmg.sh
codesign -vvv --deep /path/to/app
```

### Useful Links

- **Apple Developer:** https://developer.apple.com/account
- **Certificates:** https://developer.apple.com/account/resources/certificates
- **App Passwords:** https://appleid.apple.com
- **Notary:** https://appstoreconnect.apple.com
- **Docs:** https://developer.apple.com/documentation/security/notarizing_macos_software_before_distribution

### Security Best Practices

1. ✅ Never commit `.env` file
2. ✅ Never share .p12 or passwords
3. ✅ Use app-specific passwords
4. ✅ Rotate passwords periodically
5. ✅ Backup certificates securely
6. ✅ Use GitHub Secrets for CI/CD
7. ✅ Enable 2FA on Apple ID

---

## ✅ Success Checklist

### Initial Setup
- [ ] Apple Developer account active
- [ ] Developer ID certificate installed
- [ ] App-specific password generated
- [ ] `.env` file configured
- [ ] Apple certificates installed

### Local Signing
- [ ] `security find-identity -v` shows certificate
- [ ] Keychain partition list configured
- [ ] Test signing succeeds
- [ ] `createDistributable` works

### Notarization
- [ ] All credentials in `.env`
- [ ] Test notarization succeeds
- [ ] Status shows "Accepted"
- [ ] Stapling works

### GitHub Actions (Optional)
- [ ] All 6 secrets configured
- [ ] Workflow runs successfully
- [ ] DMG uploaded to release
- [ ] Opens without warnings

### Ready to Ship
- [ ] DMG signed (verified)
- [ ] DMG notarized (verified)
- [ ] Opens on other Mac without warnings
- [ ] Version tagged and released

---

## 🎉 You're Done!

**Congratulations!** You now have:

✅ Working code signing  
✅ Automatic notarization  
✅ GitHub Actions configured  
✅ Professional macOS distribution  

### Next Steps

1. Build release: `./gradlew :desktop:packageNotarizedDmg`
2. Test on another Mac
3. Create GitHub release
4. Distribute to users!

**Happy shipping!** 🚀

---

*Last updated: December 20, 2025*  
*For detailed documentation, see:*
- `docs/GRADLE_NOTARIZATION_TASKS.md`
- `docs/GITHUB_ACTIONS_SETUP.md`
- `docs/NOTARIZATION_SUCCESS.md`

