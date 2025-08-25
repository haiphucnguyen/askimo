<picture>
  <source media="(prefers-color-scheme: dark)" srcset="public/askimo-logo-dark.svg">
  <img alt="Askimo — AI at your command line." src="public/askimo-logo.svg">
</picture>

# Askimo
[![Build](https://github.com/haiphucnguyen/askimo/actions/workflows/release.yml/badge.svg)](https://github.com/haiphucnguyen/askimo/actions/workflows/release.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/haiphucnguyen/askimo)](https://github.com/haiphucnguyen/askimo/releases)
[![DCO](https://img.shields.io/badge/DCO-Signed--off-green.svg)](./CONTRIBUTING.md#-enforcing-dco)


`Askimo` is a powerful, pluggable command-line chat assistant that lets you talk to AI models like OpenAI, X AI, Gemini, Ollama, and more — right from your terminal. It’s built for convenience with session-based configuration that’s saved locally, and it’s easy to extend with a simple plugin-style system.

> Ask anything, switch models, and customize your AI experience — all from your terminal.

## Features

* Chat with many AI models in one place – Use OpenAI, X AI, Gemini, Ollama, and others without juggling different apps. New providers can be added easily as they emerge.

* No setup every time – Your favorite models, API keys, and preferences are saved automatically and ready when you come back.

* Make it your own – Adjust creativity, response length, and other settings so the AI replies just the way you want.

* Feels like home for developers – A friendly REPL (interactive terminal) with simple commands for setup, copying, and managing your chats.

* Supercharge your shell commands – Pipe any command output into Askimo (e.g., git log | askimo) and instantly get summaries, explanations, or rewrites.

* Works everywhere – Run it on macOS, Linux, or Windows with the same smooth experience.

### Coming soon

* Custom prompt shortcuts – Define reusable commands like :release_notes <commits_file> to instantly generate release notes from a list of commits.
* Talk to your files – Open and process local files directly in Askimo, turning documents, logs, or code into useful AI conversations.


## Demo

* Piping commands & switching providers in Askimo

![Demo](public/demo1.gif)

* Interacting with the local file system in Askimo

![Demo](public/demo2.gif)

💬 Simple Web Chat (Local Usage)

Askimo isn’t only for the terminal — you can also start a lightweight local web chat UI if you prefer a browser interface.
This feature is designed for quick testing or personal use, not for production deployment.

* **Start Askimo web server**
```
askimo --web
```


Then open your browser to the URL printed in the console (look for Web server running at http://127.0.0.1:8080). If port 8080 is busy, Askimo will pick the next free port—use the exact address shown in that log line.
The web UI supports real-time streaming responses and Markdown rendering.

![Askimo-web](public/askimo-web.png)

> ⚠️ Important: Before running askimo web, you must finish setting up your AI provider (e.g., Ollama, OpenAI) and select a model using the Askimo CLI.
> The web version is currently a simple chat page — it does not support configuring providers, models, or AI parameters. All configuration must be done beforehand via the CLI.

## Installation

You can install Askimo in four ways today:

### 1. Homebrew (macOS/Linux)

If you’re on macOS or Linux, the easiest way is via [Homebrew](https://brew.sh/)
```bash
brew tap haiphucnguyen/askimo
brew install askimo
```
Once installed, you can run:
```bash
askimo
```
to start the CLI.

To update Askimo later:
```bash
brew upgrade askimo
```

### 2. Scoop (Windows)

If you’re on Windows, use Scoop
.

Install Scoop if you don’t have it:
```bash
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned -Force
iwr -useb get.scoop.sh | iex
```

Add the Askimo bucket and install:

```bash
scoop bucket add askimo https://github.com/haiphucnguyen/scoop-askimo
scoop install askimo
```
Run:
```bash
askimo
```

Update later:
```bash
scoop update
scoop update askimo
```

### 3. Download Release Binaries

Prebuilt binaries for **macOS**, **Linux**, and **Windows** are available on the [Releases page](https://github.com/haiphucnguyen/askimo/releases).

1. Download the archive for your operating system.
2. Extract it.
3. Move the `askimo` (or `askimo.exe` on Windows) binary to a directory on your `$PATH`.

Example (Linux/macOS):

```bash
mv askimo /usr/local/bin/
```

### 4. Docker (macOS/Linux/Windows)
Run Askimo in a container while reading files from your host and persisting settings locally

**Quick start (REPL)**
```bash
IMAGE=ghcr.io/haiphucnguyen/askimo:latest   # or a specific tag like :v0.1.10
docker run --rm -it \
  -v "$HOME/.askimo:/home/nonroot/.askimo" \
  -v "$PWD:/home/nonroot/work" \
  -w /home/nonroot/work \
  $IMAGE
```

* `~/.askimo` on your machine is mounted to the container’s home so Askimo can store and reuse provider settings/keys locally (no -e OPENAI_API_KEY needed).

* Your current folder is mounted at `/home/nonroot/work`, so Askimo can read/write your project files.

**Pipe input**
```bash
cat logs.txt | docker run --rm -i \
  -v "$HOME/.askimo:/home/nonroot/.askimo" \
  -v "$PWD:/home/nonroot/work" -w /home/nonroot/work \
  ghcr.io/haiphucnguyen/askimo:latest "Summarize it"
```

## Available Commands

| Command        | Description                                    | Example Usage               |
|----------------|------------------------------------------------|-----------------------------|
| `:help`        | Show all available commands                    | `:help`                     |
| `:setparam`    | Set a parameter for the current provider       | `:setparam temperature 0.8` |
| `:params`      | View current session parameters                | `:params`                   |
| `:config`      | Edit Askimo configuration file                 | `:config`                   |
| `:providers`   | List all supported AI providers                | `:providers`                |
| `:setprovider` | Switch to a different AI provider              | `:setprovider ollama`       |
| `:models`      | List available models for the current provider | `:models`                   |
| `:copy`        | Copy the last response to the clipboard        | `:copy`                     |
| `:clear`       | Clear the chat history for the current session | `:clear`                    |
| `:exit`        | Exit the Askimo REPL                           | `:exit`                     |


➡ **[View the full command reference »](docs/commands.md)**  
Includes detailed usage, options, and examples for each command.


> 💡 Note: Some providers (such as OpenAI, X AI, etc.) require an API key.  
> Make sure you create and configure the appropriate key from your provider’s account dashboard before using them.

## Extending Askimo

Askimo is designed to be pluggable, so you can tailor it to your needs:

* Add a new chat model – Integrate any AI provider by following the guide in [docs/creating-new-chat-model.md](docs/creating-new-chat-model.md)

* Create a new command – Add custom CLI commands to automate tasks or build integrations. See [docs/creating-new-command.md](docs/creating-new-command.md).

## Contributing

* Fork & clone the repo

* Create a feature branch

* Open a PR

