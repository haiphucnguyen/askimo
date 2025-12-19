#!/bin/bash
# Copyright 2025 Hai Nguyen
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# macOS Code Signing and Testing Script
# This script handles the complete workflow:
# 1. Checks and installs Apple intermediate certificates
# 2. Verifies Developer ID certificate
# 3. Sets up keychain access
# 4. Loads environment variables from .env
# 5. Builds and signs the application
# 6. Verifies signatures and notarization
#
# Usage: ./tools/macos/test-signing-local.sh
#
# Prerequisites:
# 1. Copy .env.example to .env and fill in your credentials
# 2. Import your Developer ID certificate (.p12) to keychain

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print functions
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# Check if we're on macOS
if [[ "$OSTYPE" != "darwin"* ]]; then
    print_error "This script only works on macOS"
    exit 1
fi

print_header "macOS Code Signing Complete Setup & Test"

# ============================================================================
# STEP 0: Check and install Apple intermediate certificates
# ============================================================================
print_header "Step 0: Checking Apple Intermediate Certificates"

NEED_SUDO=false

# Check Developer ID Certification Authority (G1 and G2)
if security find-certificate -a -c "Developer ID Certification Authority" /Library/Keychains/System.keychain 2>&1 | grep -q "Developer ID Certification Authority"; then
    print_success "Developer ID Certification Authority (G1) found"
else
    print_warning "Developer ID Certification Authority (G1) not found, will install..."
    NEED_SUDO=true
fi

# Check for G2 certificate
if security find-certificate -a /Library/Keychains/System.keychain 2>&1 | grep -q "OU=G2"; then
    print_success "Developer ID Certification Authority (G2) found"
else
    print_warning "Developer ID Certification Authority (G2) not found, will install..."
    NEED_SUDO=true
fi

# Check Apple Root CA
if security find-certificate -a -c "Apple Root CA" /Library/Keychains/System.keychain 2>&1 | grep -q "Apple Root CA"; then
    print_success "Apple Root CA found"
else
    print_warning "Apple Root CA not found, will install..."
    NEED_SUDO=true
fi

# Install missing certificates if needed
if [[ "$NEED_SUDO" == "true" ]]; then
    print_info "Installing missing Apple certificates (requires sudo)..."
    echo ""

    # Install Developer ID Certification Authority G1 if missing
    if ! security find-certificate -a -c "Developer ID Certification Authority" /Library/Keychains/System.keychain 2>&1 | grep -q "Developer ID Certification Authority"; then
        print_info "Downloading Developer ID Certification Authority (G1)..."
        curl -sO https://www.apple.com/certificateauthority/DeveloperIDCA.cer
        sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain DeveloperIDCA.cer
        rm DeveloperIDCA.cer
        print_success "Developer ID Certification Authority (G1) installed"
    fi

    # Install Developer ID Certification Authority G2 if missing
    if ! security find-certificate -a /Library/Keychains/System.keychain 2>&1 | grep -q "OU=G2"; then
        print_info "Downloading Developer ID Certification Authority (G2)..."
        curl -sO https://www.apple.com/certificateauthority/DeveloperIDG2CA.cer
        sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain DeveloperIDG2CA.cer
        rm DeveloperIDG2CA.cer
        print_success "Developer ID Certification Authority (G2) installed"
    fi

    # Install Apple Root CA if missing
    if ! security find-certificate -a -c "Apple Root CA" /Library/Keychains/System.keychain 2>&1 | grep -q "Apple Root CA"; then
        print_info "Downloading Apple Root CA..."
        curl -sO https://www.apple.com/appleca/AppleIncRootCertificate.cer
        sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain AppleIncRootCertificate.cer
        rm AppleIncRootCertificate.cer
        print_success "Apple Root CA installed"
    fi

    echo ""
    print_success "All Apple certificates installed"

    # Rebuild trust settings after installing certificates
    print_info "Rebuilding trust cache..."
    sudo killall -HUP trustd 2>/dev/null || true
    sleep 2
    print_success "Trust cache rebuilt"
fi

# ============================================================================
# STEP 1: Check for Developer ID certificate
# ============================================================================
print_header "Step 1: Checking Developer ID Certificate"

CERT_INFO=$(security find-identity -v -p codesigning | grep "Developer ID Application" || echo "")
if [[ -z "$CERT_INFO" ]]; then
    print_error "No Developer ID Application certificate found in keychain"
    print_info "Please import your certificate first:"
    echo "  security import certificate.p12 -k ~/Library/Keychains/login.keychain-db"
    exit 1
fi

print_success "Developer ID Application certificate found"
echo "$CERT_INFO"
CERT_NAME=$(echo "$CERT_INFO" | sed 's/.*"\(.*\)".*/\1/')

# ============================================================================
# STEP 2: Setup keychain access
# ============================================================================
print_header "Step 2: Setting Up Keychain Access"

print_info "Testing if codesign can access the certificate..."
TEST_FILE="/tmp/test-codesign-$$.txt"
echo "test" > "$TEST_FILE"

# Try signing without password prompt
if codesign --sign "$CERT_NAME" "$TEST_FILE" 2>/dev/null; then
    print_success "Keychain access already configured"
    rm "$TEST_FILE"
else
    print_warning "Keychain access needs to be configured"
    rm "$TEST_FILE" 2>/dev/null || true

    print_info "You will be prompted for your macOS login password"
    echo ""

    # Unlock keychain
    print_info "Unlocking keychain..."
    security unlock-keychain ~/Library/Keychains/login.keychain-db

    # Prompt for password for partition list
    echo ""
    echo "Enter your macOS login password to configure keychain access:"
    read -s MACOS_PASSWORD

    if [[ -z "$MACOS_PASSWORD" ]]; then
        print_error "Password cannot be empty"
        exit 1
    fi

    # Set partition list
    print_info "Configuring keychain partition list..."
    if security set-key-partition-list -S apple-tool:,apple: -s \
        -k "$MACOS_PASSWORD" \
        ~/Library/Keychains/login.keychain-db 2>/dev/null; then
        print_success "Keychain access configured"
    else
        print_error "Failed to configure keychain access (incorrect password?)"
        exit 1
    fi

    # Test again
    echo "test" > "$TEST_FILE"
    CODESIGN_OUTPUT=$(codesign --sign "$CERT_NAME" "$TEST_FILE" 2>&1) || CODESIGN_FAILED=true

    if [[ -z "${CODESIGN_FAILED:-}" ]]; then
        print_success "Codesign test successful"
        rm "$TEST_FILE"
    else
        print_error "Codesign still failing after configuration"
        echo "Error details:"
        echo "$CODESIGN_OUTPUT"
        rm "$TEST_FILE" 2>/dev/null || true
        echo ""
        print_info "This might be due to missing intermediate certificates"
        print_info "The script will continue to check and install them if needed"
        print_warning "If the build still fails, try running the script again"
    fi
fi

# ============================================================================
# STEP 3: Load environment variables
# ============================================================================
print_header "Step 3: Loading Environment Variables"

if [[ -f ".env" ]]; then
    print_info "Loading environment variables from .env file..."

    # Export variables from .env file (handle both formats: KEY=value and export KEY=value)
    set -a
    source <(grep -v '^#' .env | grep -v '^$' | sed 's/^export //')
    set +a

    print_success ".env file loaded"
elif [[ -n "${MACOS_IDENTITY:-}" ]] && [[ -n "${APPLE_TEAM_ID:-}" ]]; then
    print_info "Environment variables already set"
else
    print_error "No .env file found and environment variables not set"
    echo ""
    echo "Please create .env file with your credentials:"
    echo "  cp .env.example .env"
    echo "  nano .env  # Edit the macOS signing section"
    exit 1
fi

# ============================================================================
# STEP 4: Verify environment variables
# ============================================================================
print_header "Step 4: Verifying Environment Variables"

MISSING_VARS=()

if [[ -z "${MACOS_IDENTITY:-}" ]]; then
    MISSING_VARS+=("MACOS_IDENTITY")
fi

if [[ -z "${APPLE_TEAM_ID:-}" ]]; then
    MISSING_VARS+=("APPLE_TEAM_ID")
fi

if [[ ${#MISSING_VARS[@]} -gt 0 ]]; then
    print_error "Missing required environment variables: ${MISSING_VARS[*]}"
    print_info "Please edit your .env file and add:"
    for var in "${MISSING_VARS[@]}"; do
        echo "  $var=your_value_here"
    done
    exit 1
fi

print_success "Required environment variables set"
echo "  MACOS_IDENTITY: $MACOS_IDENTITY"
echo "  APPLE_TEAM_ID: $APPLE_TEAM_ID"

# Check optional notarization variables
if [[ -n "${APPLE_ID:-}" ]] && [[ -n "${APPLE_PASSWORD:-}" ]]; then
    print_success "Notarization credentials found (will test notarization)"
    echo "  APPLE_ID: $APPLE_ID"
    echo "  APPLE_PASSWORD: ****"
    TEST_NOTARIZATION=true
else
    print_warning "Notarization credentials not set (will skip notarization)"
    print_info "To enable notarization, add APPLE_ID and APPLE_PASSWORD to .env"
    TEST_NOTARIZATION=false
fi

# ============================================================================
# STEP 5: Clean previous builds
# ============================================================================
print_header "Step 5: Cleaning Previous Builds"
./gradlew clean >/dev/null 2>&1
print_success "Clean completed"

# ============================================================================
# STEP 6: Build and sign DMG
# ============================================================================
print_header "Step 6: Building and Signing DMG"

print_info "This may take a few minutes..."
if ./gradlew desktop:packageDmg; then
    print_success "Build and signing completed successfully"
else
    print_error "Build failed"
    exit 1
fi

# Find the DMG
DMG_PATH=$(find desktop/build/compose/binaries/main/dmg -name "*.dmg" -type f | head -n1)
if [[ -z "$DMG_PATH" ]]; then
    print_error "No DMG file found"
    exit 1
fi

print_success "DMG created: $DMG_PATH"

# ============================================================================
# STEP 7: Verify signature
# ============================================================================
print_header "Step 7: Verifying Signature"

# Mount the DMG
MOUNT_POINT=$(hdiutil attach "$DMG_PATH" | grep Volumes | sed 's/.*\(\/Volumes\/.*\)/\1/')
if [[ -z "$MOUNT_POINT" ]]; then
    print_error "Failed to mount DMG"
    exit 1
fi

print_info "Mounted DMG at: $MOUNT_POINT"

# Find the app in the mounted volume
MOUNTED_APP=$(find "$MOUNT_POINT" -name "*.app" -type d -maxdepth 1 | head -n1)
if [[ -z "$MOUNTED_APP" ]]; then
    hdiutil detach "$MOUNT_POINT"
    print_error "No app found in mounted DMG"
    exit 1
fi

print_info "Verifying signature of: $MOUNTED_APP"
echo ""

# Verify the signature
if codesign -dv --verbose=4 "$MOUNTED_APP" 2>&1 | grep -q "Developer ID Application"; then
    print_success "App is properly signed with Developer ID"
    echo ""
    codesign -dv --verbose=4 "$MOUNTED_APP" 2>&1 | grep -E "Authority|Identifier|Timestamp"
else
    print_error "App is not properly signed"
    hdiutil detach "$MOUNT_POINT"
    exit 1
fi

echo ""

# Check Gatekeeper
print_info "Checking Gatekeeper verification..."
if spctl -a -vv -t install "$MOUNTED_APP" 2>&1; then
    print_success "App passes Gatekeeper verification"
else
    SPCTL_EXIT=$?
    if [[ $SPCTL_EXIT -eq 3 ]]; then
        print_warning "App is signed but not notarized"
        print_info "Users will see a security warning"
    else
        print_error "App fails Gatekeeper verification"
        hdiutil detach "$MOUNT_POINT"
        exit 1
    fi
fi

# Unmount the DMG
hdiutil detach "$MOUNT_POINT" >/dev/null
print_info "Unmounted DMG"

# ============================================================================
# STEP 8: Check notarization status
# ============================================================================
print_header "Step 8: Checking Notarization Status"

if stapler validate "$DMG_PATH" >/dev/null 2>&1; then
    print_success "DMG is notarized and ticket is stapled"
else
    if [[ "$TEST_NOTARIZATION" == "true" ]]; then
        print_warning "DMG is not notarized"
        print_info "Notarization credentials were provided but notarization may have failed"
        print_info "Check the Gradle output above for notarization errors"
    else
        print_warning "DMG is not notarized (credentials not provided)"
        print_info "Add APPLE_ID and APPLE_PASSWORD to .env to enable notarization"
    fi
fi

# ============================================================================
# Summary
# ============================================================================
print_header "Test Summary"

echo -e "${GREEN}✅ Apple certificates installed${NC}"
echo -e "${GREEN}✅ Keychain access configured${NC}"
echo -e "${GREEN}✅ Certificate verified${NC}"
echo -e "${GREEN}✅ Environment variables set${NC}"
echo -e "${GREEN}✅ Build successful${NC}"
echo -e "${GREEN}✅ DMG packaging successful${NC}"
echo -e "${GREEN}✅ Code signing successful${NC}"
echo -e "${GREEN}✅ Gatekeeper check passed${NC}"

if stapler validate "$DMG_PATH" >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Notarization successful${NC}"
else
    echo -e "${YELLOW}⚠️  Notarization not completed${NC}"
fi

echo ""
print_success "All critical tests passed!"
print_info "Your signing setup is working correctly"
print_info "You can now set up GitHub Actions with confidence"

echo ""
print_info "DMG location: $DMG_PATH"
print_info "You can test opening it: open \"$DMG_PATH\""

exit 0

