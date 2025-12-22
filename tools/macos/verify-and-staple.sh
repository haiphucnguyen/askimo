#!/bin/bash

# Script to verify notarization and retry stapling after CDN propagation

set -e

echo "=================================="
echo "Notarization Verification Script"
echo "=================================="
echo ""

# Check if .env exists and load it
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
    echo "✅ Loaded credentials from .env"
else
    echo "❌ .env file not found"
    exit 1
fi

# Check required variables
if [ -z "$APPLE_ID" ] || [ -z "$APPLE_PASSWORD" ] || [ -z "$APPLE_TEAM_ID" ]; then
    echo "❌ Missing required environment variables"
    echo "   Please set APPLE_ID, APPLE_PASSWORD, and APPLE_TEAM_ID in .env"
    exit 1
fi

APP_PATH="desktop/build/compose/binaries/main/app/Askimo.app"
DMG_PATH="desktop/build/compose/binaries/main/dmg/Askimo-1.2.0.dmg"

# Check if files exist
if [ ! -d "$APP_PATH" ]; then
    echo "❌ App not found: $APP_PATH"
    exit 1
fi

if [ ! -f "$DMG_PATH" ]; then
    echo "❌ DMG not found: $DMG_PATH"
    exit 1
fi

echo ""
echo "Found:"
echo "  App: $APP_PATH"
echo "  DMG: $DMG_PATH"
echo ""

# Function to check notarization status
check_notarization() {
    local submission_id=$1
    echo "Checking status for submission: $submission_id"
    xcrun notarytool info "$submission_id" \
        --apple-id "$APPLE_ID" \
        --team-id "$APPLE_TEAM_ID" \
        --password "$APPLE_PASSWORD" 2>&1 | grep -E "status:|createdDate:"
    echo ""
}

# Check app submission (you can replace with your actual submission ID)
echo "=================================="
echo "Step 1: Check Notarization Status"
echo "=================================="
echo ""
read -p "Enter app submission ID (from build logs): " APP_SUBMISSION_ID
if [ -n "$APP_SUBMISSION_ID" ]; then
    check_notarization "$APP_SUBMISSION_ID"
fi

read -p "Enter DMG submission ID (from build logs): " DMG_SUBMISSION_ID
if [ -n "$DMG_SUBMISSION_ID" ]; then
    check_notarization "$DMG_SUBMISSION_ID"
fi

# Try to staple
echo "=================================="
echo "Step 2: Attempt Stapling"
echo "=================================="
echo ""

echo "Stapling app..."
if xcrun stapler staple "$APP_PATH" 2>&1; then
    echo "✅ App stapled successfully!"
else
    echo "⚠️  App stapling failed (CDN may still be propagating)"
fi

echo ""
echo "Stapling DMG..."
if xcrun stapler staple "$DMG_PATH" 2>&1; then
    echo "✅ DMG stapled successfully!"
else
    echo "⚠️  DMG stapling failed (CDN may still be propagating)"
fi

# Verify with spctl
echo ""
echo "=================================="
echo "Step 3: Verify with Gatekeeper"
echo "=================================="
echo ""

echo "Mounting DMG..."
hdiutil attach "$DMG_PATH" -quiet

echo "Checking app signature..."
if spctl -a -vv /Volumes/Askimo/Askimo.app 2>&1; then
    echo ""
    echo "✅ SUCCESS! App is properly notarized!"
else
    echo ""
    echo "⚠️  Gatekeeper verification failed"
    echo ""
    echo "This means either:"
    echo "  1. CDN is still propagating (wait 30-60 minutes)"
    echo "  2. You need internet connection for verification"
fi

echo ""
echo "Unmounting DMG..."
hdiutil detach /Volumes/Askimo -quiet

echo ""
echo "=================================="
echo "Summary"
echo "=================================="
echo ""
echo "If stapling succeeded, your DMG is ready for distribution!"
echo "If stapling failed, wait 30-60 minutes and run this script again."
echo ""
echo "Even without stapling, your app is notarized and will work"
echo "for users with internet connection."
echo ""

