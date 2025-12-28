# Askimo CLI Installation Script for Windows
# This script downloads and installs the latest version of Askimo CLI on Windows

# Requires PowerShell 5.1 or later
#Requires -Version 5.1

$ErrorActionPreference = "Stop"

# Configuration
$REPO = "haiphucnguyen/askimo"
$BINARY_NAME = "askimo"

# Color output functions
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] " -ForegroundColor Green -NoNewline
    Write-Host $Message
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[WARN] " -ForegroundColor Yellow -NoNewline
    Write-Host $Message
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host "[ERROR] " -ForegroundColor Red -NoNewline
    Write-Host $Message
}

# Detect architecture
function Get-Architecture {
    $arch = $env:PROCESSOR_ARCHITECTURE
    switch ($arch) {
        "AMD64" { return "amd64" }
        "ARM64" { return "arm64" }
        default {
            Write-Error-Custom "Unsupported architecture: $arch"
            exit 1
        }
    }
}

# Get latest release version from GitHub
function Get-LatestVersion {
    Write-Info "Fetching latest version..."

    try {
        $response = Invoke-RestMethod -Uri "https://api.github.com/repos/$REPO/releases/latest" -Method Get
        $version = $response.tag_name

        if ([string]::IsNullOrEmpty($version)) {
            Write-Error-Custom "Failed to fetch latest version"
            exit 1
        }

        Write-Info "Latest version: $version"
        return $version
    }
    catch {
        Write-Error-Custom "Failed to fetch release information: $_"
        exit 1
    }
}

# Download and install binary
function Install-Binary {
    param(
        [string]$Version,
        [string]$Architecture
    )

    $archiveName = "$BINARY_NAME-windows-$Architecture.zip"
    $downloadUrl = "https://github.com/$REPO/releases/download/$Version/$archiveName"

    Write-Info "Downloading from: $downloadUrl"

    # Create temporary directory
    $tempDir = Join-Path $env:TEMP "askimo-install-$(Get-Random)"
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

    $archivePath = Join-Path $tempDir $archiveName

    try {
        # Download archive
        Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath -UseBasicParsing

        Write-Info "Extracting archive..."

        # Extract archive
        Expand-Archive -Path $archivePath -DestinationPath $tempDir -Force

        # Determine installation directory
        $installDir = Join-Path $env:LOCALAPPDATA "Programs\Askimo"

        # Create install directory if it doesn't exist
        if (-not (Test-Path $installDir)) {
            New-Item -ItemType Directory -Path $installDir -Force | Out-Null
        }

        Write-Info "Installing to: $installDir"

        # Move binary to install directory
        $binaryFile = Join-Path $tempDir "$BINARY_NAME.exe"
        $targetFile = Join-Path $installDir "$BINARY_NAME.exe"

        if (Test-Path $binaryFile) {
            # Stop if binary is running
            Get-Process -Name $BINARY_NAME -ErrorAction SilentlyContinue | Stop-Process -Force

            Copy-Item -Path $binaryFile -Destination $targetFile -Force
        }
        else {
            Write-Error-Custom "Binary not found in archive"
            exit 1
        }

        Write-Info "✓ Installation complete!"

        return $installDir
    }
    catch {
        Write-Error-Custom "Installation failed: $_"
        exit 1
    }
    finally {
        # Clean up
        if (Test-Path $tempDir) {
            Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

# Add to PATH if not already present
function Add-ToPath {
    param([string]$Directory)

    $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")

    if ($currentPath -notlike "*$Directory*") {
        Write-Info "Adding to PATH..."

        $newPath = "$currentPath;$Directory"
        [Environment]::SetEnvironmentVariable("Path", $newPath, "User")

        # Update current session PATH
        $env:Path = "$env:Path;$Directory"

        Write-Info "✓ Added to PATH. You may need to restart your terminal."
    }
    else {
        Write-Info "Directory already in PATH"
    }
}

# Verify installation
function Test-Installation {
    # Refresh PATH in current session
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "User") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "Machine")

    Write-Info "Verifying installation..."

    try {
        $version = & $BINARY_NAME --version 2>&1
        Write-Info "✓ Verification successful: $version"
        return $true
    }
    catch {
        Write-Warn "$BINARY_NAME is installed but may not be accessible yet"
        Write-Info "Please restart your terminal and try again"
        return $false
    }
}

# Main installation flow
function Main {
    Write-Host ""
    Write-Info "Askimo CLI Installer for Windows"
    Write-Host ""

    # Check PowerShell version
    if ($PSVersionTable.PSVersion.Major -lt 5) {
        Write-Error-Custom "PowerShell 5.1 or later is required"
        Write-Info "Please upgrade PowerShell: https://aka.ms/powershell"
        exit 1
    }

    $architecture = Get-Architecture
    Write-Info "Detected Architecture: $architecture"

    $version = Get-LatestVersion
    $installDir = Install-Binary -Version $version -Architecture $architecture

    Add-ToPath -Directory $installDir
    Test-Installation

    Write-Host ""
    Write-Info "Next steps:"
    Write-Host "  1. Configure your AI providers and models: https://askimo.chat/docs/cli/getting-started/"
    Write-Host "  2. Run 'askimo -p `"Hello, AI!`"' to start chatting"
    Write-Host ""
    Write-Info "Note: If the command is not found, please restart your terminal"
    Write-Host ""
    Write-Info "For more information, visit: https://askimo.chat"
    Write-Host ""
}

# Run main function
Main

