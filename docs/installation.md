# Installation Guide

Askimo can be installed in several ways depending on your operating system and preference.
Choose the method that works best for your workflow.

## Installation Options by OS

| Method               | macOS | Linux | Windows | Notes                          |
|----------------------|:-----:|:-----:|:-------:|--------------------------------|
| **Homebrew**         | ✅    | ✅    | ❌      | Easiest on macOS/Linux         |
| **Scoop**            | ❌    | ❌    | ✅      | Easiest on Windows             |
| **Release Binaries** | ✅    | ✅    | ✅      | Manual install, works anywhere |
| **Docker**           | ✅    | ✅    | ✅      | No local install needed        |


---

## 1. Homebrew (macOS / Linux)

The easiest way if you’re on macOS or Linux.
```bash
brew tap haiphucnguyen/askimo
brew install askimo
askimo
```

Update later:
```bash
brew upgrade askimo
```

## 2. Scoop (Windows)

The easiest way if you’re on Windows.

Install Scoop if you don’t have it:
```bash
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned -Force
iwr -useb get.scoop.sh | iex
```

Add the Askimo bucket and install:
```bash
scoop bucket add askimo https://github.com/haiphucnguyen/scoop-askimo
scoop install askimo
askimo
```

Update later:
```bash
scoop update
scoop update askimo
```

## 3. Download Release Binaries (macOS / Linux / Windows)

Prebuilt binaries are available on the [Releases](https://github.com/haiphucnguyen/askimo/releases) page

* Download the archive for your OS.

* Extract it.

* Move the binary into a directory on your $PATH.

Example (macOS/Linux):
```bash
mv askimo /usr/local/bin/
chmod +x /usr/local/bin/askimo
askimo
```

On Windows, move askimo.exe into a folder included in your PATH.

## 4. Docker (macOS / Linux / Windows)

Run Askimo inside a container without installing anything locally.
```bash
IMAGE=ghcr.io/haiphucnguyen/askimo:latest   # or specific tag like :v0.1.10
docker run --rm -it \
  -v "$HOME/.askimo:/home/nonroot/.askimo" \
  -v "$PWD:/home/nonroot/work" \
  -w /home/nonroot/work \
  $IMAGE
```

* ~/.askimo on your machine is mounted for local provider settings/keys.

* Your current folder is mounted so Askimo can read/write your project files.

Pipe input:
```bash
cat logs.txt | docker run --rm -i \
  -v "$HOME/.askimo:/home/nonroot/.askimo" \
  -v "$PWD:/home/nonroot/work" \
  -w /home/nonroot/work \
  ghcr.io/haiphucnguyen/askimo:latest "Summarize it"
```

---
## Troubleshooting

* Command not found → Make sure askimo is in your $PATH.
* macOS Gatekeeper blocks the binary → Run:
```bash
xattr -d com.apple.quarantine askimo
```
* Windows Execution Policy issues → Run PowerShell as Administrator and use:
```bash
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
```