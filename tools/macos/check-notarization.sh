#!/bin/bash

# Check Apple Notarization Status
# Shows recent notarization submissions and their status

set -euo pipefail

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_success() { echo -e "${GREEN}✅ $1${NC}"; }
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

# Check credentials
APPLE_ID="${APPLE_ID:-}"
APPLE_PASSWORD="${APPLE_PASSWORD:-}"
APPLE_TEAM_ID="${APPLE_TEAM_ID:-}"

if [[ -z "$APPLE_ID" ]] || [[ -z "$APPLE_PASSWORD" ]] || [[ -z "$APPLE_TEAM_ID" ]]; then
    echo "❌ Missing credentials in .env file"
    echo ""
    echo "Required: APPLE_ID, APPLE_PASSWORD, APPLE_TEAM_ID"
    exit 1
fi

echo "=========================================="
echo "Apple Notarization Status"
echo "=========================================="
echo ""
print_info "Apple ID: $APPLE_ID"
print_info "Team ID: $APPLE_TEAM_ID"
echo ""

# Get submission ID from argument if provided
SUBMISSION_ID="${1:-}"

if [[ -n "$SUBMISSION_ID" ]]; then
    # Check specific submission
    echo "Checking submission: $SUBMISSION_ID"
    echo ""

    xcrun notarytool info "$SUBMISSION_ID" \
        --apple-id "$APPLE_ID" \
        --team-id "$APPLE_TEAM_ID" \
        --password "$APPLE_PASSWORD"

    echo ""

    # Offer to show log if failed
    STATUS=$(xcrun notarytool info "$SUBMISSION_ID" \
        --apple-id "$APPLE_ID" \
        --team-id "$APPLE_TEAM_ID" \
        --password "$APPLE_PASSWORD" | grep "status:" | awk '{print $2}')

    if [[ "$STATUS" == "Invalid" ]]; then
        echo ""
        print_warning "Submission was rejected. Fetching detailed log..."
        echo ""
        xcrun notarytool log "$SUBMISSION_ID" \
            --apple-id "$APPLE_ID" \
            --team-id "$APPLE_TEAM_ID" \
            --password "$APPLE_PASSWORD"
    fi
else
    # Show recent submissions
    echo "Recent notarization submissions:"
    echo ""

    xcrun notarytool history \
        --apple-id "$APPLE_ID" \
        --team-id "$APPLE_TEAM_ID" \
        --password "$APPLE_PASSWORD"
fi

echo ""
echo "=========================================="
print_info "To check a specific submission:"
echo "  $0 <submission-id>"

