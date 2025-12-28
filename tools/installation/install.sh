#!/bin/bash
# Askimo CLI Installation Script
# This script downloads and installs the latest version of Askimo CLI

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# GitHub repository
REPO="haiphucnguyen/askimo"
BINARY_NAME="askimo"

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Detect OS
detect_os() {
    case "$(uname -s)" in
        Darwin*)
            OS="darwin"
            ;;
        Linux*)
            OS="linux"
            ;;
        MINGW*|MSYS*|CYGWIN*)
            OS="windows"
            ;;
        *)
            print_error "Unsupported operating system: $(uname -s)"
            exit 1
            ;;
    esac
}

# Detect architecture
detect_arch() {
    ARCH=$(uname -m)
    case $ARCH in
        x86_64|amd64)
            ARCH="amd64"
            ;;
        aarch64|arm64)
            ARCH="arm64"
            ;;
        *)
            print_error "Unsupported architecture: $ARCH"
            exit 1
            ;;
    esac
}

# Get latest release version from GitHub
get_latest_version() {
    print_info "Fetching latest version..."
    LATEST_VERSION=$(curl -s "https://api.github.com/repos/$REPO/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')

    if [ -z "$LATEST_VERSION" ]; then
        print_error "Failed to fetch latest version"
        exit 1
    fi

    # Remove 'v' prefix if present
    VERSION=${LATEST_VERSION#v}
    print_info "Latest version: $VERSION"
}

# Download and install binary
install_binary() {
    # Construct download URL
    if [ "$OS" = "windows" ]; then
        ARCHIVE_NAME="${BINARY_NAME}-${OS}-${ARCH}.zip"
        BINARY_FILE="${BINARY_NAME}.exe"
    else
        ARCHIVE_NAME="${BINARY_NAME}-${OS}-${ARCH}.tar.gz"
        BINARY_FILE="${BINARY_NAME}"
    fi

    DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${LATEST_VERSION}/${ARCHIVE_NAME}"

    print_info "Downloading from: $DOWNLOAD_URL"

    # Create temporary directory
    TMP_DIR=$(mktemp -d)
    cd "$TMP_DIR"

    # Download archive
    if ! curl -fsSL -o "$ARCHIVE_NAME" "$DOWNLOAD_URL"; then
        print_error "Failed to download $ARCHIVE_NAME"
        rm -rf "$TMP_DIR"
        exit 1
    fi

    print_info "Extracting archive..."

    # Extract archive
    if [ "$OS" = "windows" ]; then
        unzip -q "$ARCHIVE_NAME"
    else
        tar -xzf "$ARCHIVE_NAME"
    fi

    # Determine installation directory
    if [ "$OS" = "windows" ]; then
        INSTALL_DIR="$HOME/bin"
    else
        # Try /usr/local/bin first, fall back to ~/.local/bin if no sudo access
        if [ -w "/usr/local/bin" ]; then
            INSTALL_DIR="/usr/local/bin"
        else
            INSTALL_DIR="$HOME/.local/bin"
        fi
    fi

    # Create install directory if it doesn't exist
    mkdir -p "$INSTALL_DIR"

    print_info "Installing to: $INSTALL_DIR"

    # Move binary to install directory
    if [ -f "$BINARY_FILE" ]; then
        mv "$BINARY_FILE" "$INSTALL_DIR/"
        chmod +x "$INSTALL_DIR/$BINARY_FILE"
    else
        print_error "Binary not found in archive"
        rm -rf "$TMP_DIR"
        exit 1
    fi

    # Clean up
    cd - > /dev/null
    rm -rf "$TMP_DIR"

    print_info "✓ Installation complete!"
}

# Check if binary is in PATH
check_path() {
    if ! command -v $BINARY_NAME &> /dev/null; then
        print_warn "$BINARY_NAME is installed but not in your PATH"

        if [ "$OS" = "windows" ]; then
            print_info "Add $HOME/bin to your PATH environment variable"
        elif [ "$INSTALL_DIR" = "$HOME/.local/bin" ]; then
            print_info "Add the following to your shell profile (~/.bashrc, ~/.zshrc, etc.):"
            echo ""
            echo "    export PATH=\"\$HOME/.local/bin:\$PATH\""
            echo ""
            print_info "Then reload your shell or run: source ~/.bashrc (or ~/.zshrc)"
        fi
    else
        print_info "Verifying installation..."
        $BINARY_NAME --version
    fi
}

# Main installation flow
main() {
    echo ""
    print_info "Askimo CLI Installer"
    echo ""

    # Check if running on Windows without proper bash environment
    if [[ "$OS" == "windows" ]] && [[ ! "$OSTYPE" =~ ^(msys|cygwin) ]]; then
        print_warn "Detected Windows without Git Bash/WSL"
        print_info "For native Windows installation, please use the PowerShell script:"
        echo ""
        echo "    iwr -useb https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/installation/install.ps1 | iex"
        echo ""
        print_info "Or install via Scoop: https://askimo.chat/docs/cli/getting-started/"
        exit 0
    fi

    detect_os
    detect_arch

    print_info "Detected OS: $OS"
    print_info "Detected Architecture: $ARCH"

    get_latest_version
    install_binary
    check_path

    echo ""
    print_info "Next steps:"
    echo "  1. Configure your AI providers and models: https://askimo.chat/docs/cli/getting-started/"
    echo "  2. Run 'askimo -p \"Hello, AI!\"' to start chatting"
    echo ""
    print_info "For more information, visit: https://askimo.chat"
    echo ""
}

main

