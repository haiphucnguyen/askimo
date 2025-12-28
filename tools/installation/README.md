# Installation Scripts

Askimo CLI provides installation scripts for easy setup on different operating systems.

## Bash Script (macOS/Linux/Git Bash/WSL)

The `install.sh` script provides a quick and easy way to install Askimo CLI on macOS, Linux, and Windows (via Git Bash or WSL).

### Usage

**One-line installation (macOS/Linux):**
```bash
curl -sSL https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/install.sh | bash
```

**Windows with Git Bash or WSL:**
```bash
curl -sSL https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/install.sh | bash
```

**Or download and run:**
```bash
wget https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/install.sh
chmod +x install.sh
./install.sh
```

### What It Does

The bash installation script will:

1. **Detect your system** - Automatically identifies your OS (macOS/Linux/Windows) and architecture (amd64/arm64)
2. **Check bash environment** - On Windows, verifies you're running Git Bash, WSL, or similar
3. **Fetch latest version** - Gets the latest release from GitHub
4. **Download binary** - Downloads the appropriate binary for your system
5. **Extract archive** - Unpacks the downloaded archive (.tar.gz)
6. **Install binary** - Moves the binary to an appropriate location:
   - `/usr/local/bin` if you have write access (macOS/Linux)
   - `~/.local/bin` if you don't have sudo access (macOS/Linux)
   - `~/bin` for Git Bash/WSL on Windows
7. **Set permissions** - Makes the binary executable
8. **Verify installation** - Checks that the binary is accessible

---

## PowerShell Script (Windows)

The `install.ps1` script is designed specifically for native Windows PowerShell environments.

### Usage

**One-line installation (PowerShell):**
```powershell
iwr -useb https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/install.ps1 | iex
```

**Or download and run:**
```powershell
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/install.ps1" -OutFile "install.ps1"
.\install.ps1
```

**Note:** You may need to allow script execution:
```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### What It Does

The PowerShell installation script will:

1. **Check PowerShell version** - Ensures PowerShell 5.1 or later is installed
2. **Detect architecture** - Automatically identifies your architecture (amd64/arm64)
3. **Fetch latest version** - Gets the latest release from GitHub API
4. **Download binary** - Downloads the Windows binary (.zip)
5. **Extract archive** - Unpacks the downloaded archive
6. **Install binary** - Moves the binary to `%LOCALAPPDATA%\Programs\Askimo`
7. **Add to PATH** - Automatically adds the installation directory to your user PATH
8. **Verify installation** - Checks that the binary is accessible

---

## Supported Systems

### Bash Script (install.sh)

- **Operating Systems:**
  - macOS (Darwin)
  - Linux (all distributions)
  - Windows (via Git Bash, WSL, MSYS2, Cygwin)

- **Architectures:**
  - x86_64/amd64
  - arm64/aarch64

### PowerShell Script (install.ps1)

- **Operating Systems:**
  - Windows 10 or later

- **Architectures:**
  - AMD64 (x86_64)
  - ARM64

- **Requirements:**
  - PowerShell 5.1 or later (included in Windows 10+)

---

## Installation Locations

### Bash Script
The script installs to the first available location:

1. `/usr/local/bin` (preferred, requires write access)
2. `~/.local/bin` (fallback for user-only installation)
3. `~/bin` (Windows Git Bash/WSL)

### PowerShell Script
- Windows: `%LOCALAPPDATA%\Programs\Askimo`
- Automatically added to user PATH

---

## PATH Configuration

### macOS/Linux (Bash Script)

If the binary is not in your PATH after installation, add the installation directory to your PATH:

**For bash (~/.bashrc):**
```bash
export PATH="$HOME/.local/bin:$PATH"
```

**For zsh (~/.zshrc):**
```bash
export PATH="$HOME/.local/bin:$PATH"
```

Then reload your shell:
```bash
source ~/.bashrc  # or source ~/.zshrc
```

### Windows (PowerShell Script)

The PowerShell script automatically adds the installation directory to your user PATH. If you need to do it manually:

1. Press `Win + X` and select "System"
2. Click "Advanced system settings"
3. Click "Environment Variables"
4. Under "User variables", select "Path" and click "Edit"
5. Click "New" and add `%LOCALAPPDATA%\Programs\Askimo`
6. Click "OK" to save

Or use PowerShell:
```powershell
$path = [Environment]::GetEnvironmentVariable("Path", "User")
[Environment]::SetEnvironmentVariable("Path", "$path;$env:LOCALAPPDATA\Programs\Askimo", "User")
```

---

## Troubleshooting

### Bash Script

**Binary not found after installation:**
- Check if the installation directory is in your PATH
- Run `echo $PATH` to see your current PATH
- Add the installation directory to your shell profile

**Permission denied:**
- Make sure the script is executable: `chmod +x install.sh`
- Or pipe directly to bash: `curl -sSL <url> | bash`

**Download fails:**
- Check your internet connection
- Verify the GitHub repository is accessible
- Try downloading manually from the [releases page](https://github.com/haiphucnguyen/askimo/releases)

**Unsupported OS/Architecture:**
- Check supported systems above
- Download manually from releases and install to a directory in your PATH

**Windows: Script fails:**
- Use PowerShell script instead for native Windows
- Or ensure you're running in Git Bash, WSL, MSYS2, or Cygwin

### PowerShell Script

**Execution policy error:**
```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

**Binary not found after installation:**
- Restart your PowerShell terminal
- Check PATH: `$env:Path`
- Manually verify the binary exists in `%LOCALAPPDATA%\Programs\Askimo`

**Download fails:**
- Check your internet connection
- Verify firewall settings allow PowerShell to download files
- Try manual installation from releases

**PowerShell version too old:**
- Update PowerShell: https://aka.ms/powershell
- Or use Scoop: `scoop install askimo`

---

## Manual Installation

If the scripts don't work for your system, you can install manually:

1. Go to the [releases page](https://github.com/haiphucnguyen/askimo/releases)
2. Download the appropriate archive for your OS and architecture
3. Extract the archive
4. Move the binary to a directory in your PATH
5. (macOS/Linux) Make it executable: `chmod +x /usr/local/bin/askimo`

---

## Next Steps

After installation:

1. **Configure AI providers:**
   Visit https://askimo.chat/docs/cli/getting-started/

2. **Verify installation:**
   ```bash
   askimo --version
   ```

3. **Start using Askimo:**
   ```bash
   askimo -p "Hello, AI!"
   ```

For more information, visit the [documentation](https://askimo.chat) or check the main [README](../README.md).

