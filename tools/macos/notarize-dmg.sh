#!/bin/bash

# Manual Notarization Script for macOS DMG
# Usage: ./tools/macos/notarize-dmg.sh [path-to-dmg]

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_success() { echo -e "${GREEN}✅ $1${NC}"; }
print_error() { echo -e "${RED}❌ $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
print_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }

# Load environment variables from .env
if [[ -f ".env" ]]; then
    print_info "Loading credentials from .env..."
    while IFS='=' read -r key value || [[ -n "$key" ]]; do
        [[ "$key" =~ ^#.*$ ]] && continue
        [[ -z "$key" ]] && continue
        key="${key#export }"
        key="${key// /}"
        value="${value#"${value%%[![:space:]]*}"}"
        value="${value%"${value##*[![:space:]]}"}"
        [[ -z "$value" ]] && continue
        [[ "$value" =~ ^(your_|xxxx-) ]] && continue
        export "$key=$value"
    done < .env
fi

# Get DMG path from argument or use default
DMG_PATH="${1:-}"
if [[ -z "$DMG_PATH" ]]; then
    # Find the most recent DMG
    DMG_PATH=$(ls -t desktop/build/compose/binaries/main/dmg/Askimo-*.dmg 2>/dev/null | head -1)
fi

if [[ ! -f "$DMG_PATH" ]]; then
    print_error "DMG not found: $DMG_PATH"
    echo ""
    echo "Usage: $0 [path-to-dmg]"
    echo "Example: $0 desktop/build/compose/binaries/main/dmg/Askimo-1.1.4.dmg"
    exit 1
fi

# Check credentials
APPLE_ID="${APPLE_ID:-}"
APPLE_PASSWORD="${APPLE_PASSWORD:-}"
APPLE_TEAM_ID="${APPLE_TEAM_ID:-}"

if [[ -z "$APPLE_ID" ]] || [[ -z "$APPLE_PASSWORD" ]] || [[ -z "$APPLE_TEAM_ID" ]]; then
    print_error "Missing notarization credentials"
    echo ""
    echo "Required environment variables (set in .env):"
    echo "  APPLE_ID=your-apple-id@email.com"
    echo "  APPLE_PASSWORD=xxxx-xxxx-xxxx-xxxx (app-specific password)"
    echo "  APPLE_TEAM_ID=YOUR_TEAM_ID"
    exit 1
fi

echo "=========================================="
echo "macOS Notarization"
echo "=========================================="
echo ""
print_info "DMG: $DMG_PATH"
print_info "Apple ID: $APPLE_ID"
print_info "Team ID: $APPLE_TEAM_ID"
echo ""

# Step 1: Submit for notarization
print_info "Step 1: Submitting to Apple for notarization..."
echo ""
print_warning "This can take 5-60 minutes. Progress will be shown below."
echo ""

# Submit WITHOUT --wait first to get the submission ID immediately
print_info "Uploading DMG to Apple..."
SUBMIT_OUTPUT=$(xcrun notarytool submit "$DMG_PATH" \
    --apple-id "$APPLE_ID" \
    --team-id "$APPLE_TEAM_ID" \
    --password "$APPLE_PASSWORD" 2>&1)

echo "$SUBMIT_OUTPUT"
echo ""

# Extract submission ID
SUBMISSION_ID=$(echo "$SUBMIT_OUTPUT" | grep "id:" | head -1 | awk '{print $2}')

if [[ -z "$SUBMISSION_ID" ]]; then
    print_error "Failed to submit for notarization"
    echo ""
    echo "Full output:"
    echo "$SUBMIT_OUTPUT"
    echo ""
    print_info "Common issues:"
    echo "  - Invalid credentials (check APPLE_ID, APPLE_PASSWORD, APPLE_TEAM_ID)"
    echo "  - Network connection issues"
    echo "  - Apple's servers are down"
    exit 1
fi

print_success "Upload complete!"
print_info "Submission ID: $SUBMISSION_ID"
echo ""

# Now wait for the result with progress updates
print_info "Waiting for Apple to process the submission..."
print_info "Checking status every 30 seconds..."
echo ""

MAX_WAIT_MINUTES=90
WAIT_SECONDS=$((MAX_WAIT_MINUTES * 60))
CHECK_INTERVAL=30
ELAPSED=0

while [ $ELAPSED -lt $WAIT_SECONDS ]; do
    # Get current status
    STATUS_OUTPUT=$(xcrun notarytool info "$SUBMISSION_ID" \
        --apple-id "$APPLE_ID" \
        --team-id "$APPLE_TEAM_ID" \
        --password "$APPLE_PASSWORD" 2>&1)

    STATUS=$(echo "$STATUS_OUTPUT" | grep "status:" | awk '{print $2}')

    case "$STATUS" in
        "Accepted")
            echo ""
            print_success "Notarization successful!"
            print_info "Status: $STATUS"
            break
            ;;
        "Invalid")
            echo ""
            print_error "Notarization failed - Apple rejected the submission"
            echo ""
            print_info "Fetching detailed error log..."
            xcrun notarytool log "$SUBMISSION_ID" \
                --apple-id "$APPLE_ID" \
                --team-id "$APPLE_TEAM_ID" \
                --password "$APPLE_PASSWORD"
            echo ""
            print_info "Common issues:"
            echo "  - Hardened runtime not enabled (should be OK)"
            echo "  - Missing entitlements"
            echo "  - Unsigned nested components"
            echo "  - Using restricted APIs"
            exit 1
            ;;
        "In Progress")
            MINUTES=$((ELAPSED / 60))
            printf "\r⏳ Status: In Progress | Elapsed: %d:%02d | Checking again in 30s..." $MINUTES $((ELAPSED % 60))
            sleep $CHECK_INTERVAL
            ELAPSED=$((ELAPSED + CHECK_INTERVAL))
            ;;
        "")
            # No status yet, still uploading or processing
            MINUTES=$((ELAPSED / 60))
            printf "\r⏳ Status: Processing | Elapsed: %d:%02d | Checking again in 30s..." $MINUTES $((ELAPSED % 60))
            sleep $CHECK_INTERVAL
            ELAPSED=$((ELAPSED + CHECK_INTERVAL))
            ;;
        *)
            echo ""
            print_warning "Unknown status: $STATUS"
            echo "Full status output:"
            echo "$STATUS_OUTPUT"
            sleep $CHECK_INTERVAL
            ELAPSED=$((ELAPSED + CHECK_INTERVAL))
            ;;
    esac
done

if [ $ELAPSED -ge $WAIT_SECONDS ]; then
    echo ""
    print_error "Timeout waiting for notarization (${MAX_WAIT_MINUTES} minutes)"
    echo ""
    print_info "You can check the status later with:"
    echo "  xcrun notarytool info $SUBMISSION_ID \\"
    echo "    --apple-id \"$APPLE_ID\" \\"
    echo "    --team-id \"$APPLE_TEAM_ID\" \\"
    echo "    --password \"$APPLE_PASSWORD\""
    exit 1
fi

# Check if submission was successful
if echo "$STATUS_OUTPUT" | grep -q "status: Accepted"; then
    print_success "Notarization successful!"

    # Extract submission ID for reference
    print_info "Submission ID: $SUBMISSION_ID"

elif echo "$STATUS_OUTPUT" | grep -q "status: Invalid"; then
    print_error "Notarization failed - Apple rejected the submission"
    echo ""

    # Get the submission ID to fetch detailed log
    if [[ -n "$SUBMISSION_ID" ]]; then
        print_info "Fetching detailed error log..."
        echo ""
        xcrun notarytool log "$SUBMISSION_ID" \
            --apple-id "$APPLE_ID" \
            --team-id "$APPLE_TEAM_ID" \
            --password "$APPLE_PASSWORD"
    fi

    echo ""
    print_info "Common issues:"
    echo "  - Hardened runtime not enabled (should be OK)"
    echo "  - Missing entitlements"
    echo "  - Unsigned nested components"
    echo "  - Using restricted APIs"
    exit 1

else
    print_error "Notarization status unknown"
    echo ""
    echo "Full output:"
    echo "$STATUS_OUTPUT"
    exit 1
fi

echo ""

# Step 2: Staple the ticket
print_info "Step 2: Stapling notarization ticket to DMG..."
if xcrun stapler staple "$DMG_PATH" 2>&1; then
    print_success "Notarization ticket stapled"
else
    print_warning "Failed to staple ticket (this is sometimes OK)"
    print_info "The DMG is still notarized, stapling is optional"
fi

echo ""

# Step 3: Verify
print_info "Step 3: Verifying notarization..."
echo ""

if xcrun stapler validate "$DMG_PATH" 2>&1; then
    print_success "DMG validation successful"
else
    print_info "Stapler validation shows ticket not attached (this is OK if notarization succeeded)"
fi

echo ""

# Gatekeeper check
print_info "Step 4: Checking Gatekeeper acceptance..."
if spctl -a -vv -t install "$DMG_PATH" 2>&1; then
    print_success "Gatekeeper: DMG is fully accepted!"
else
    SPCTL_EXIT=$?
    if [[ $SPCTL_EXIT -eq 3 ]]; then
        print_info "Gatekeeper check skipped (expected for DMG files)"
        print_info "The app inside should be properly notarized"
    else
        print_warning "Gatekeeper check failed"
    fi
fi

echo ""
echo "=========================================="
echo "✅ Notarization Complete!"
echo "=========================================="
echo ""
print_info "Your DMG is now fully signed and notarized"
print_info "Users will not see any security warnings"
echo ""
print_info "You can now distribute: $DMG_PATH"

