<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="public/github-logo-dark.svg">
    <img alt="Askimo - AI toolkit for your workflows." src="public/github-logo-light.svg">
  </picture>
</p>

<p align="center">
  <b><a href="https://askimo.chat">askimo.chat</a></b> · The AI chat client that works with ANY model.
</p>

<p align="center">
  <a href="https://github.com/haiphucnguyen/askimo/actions/workflows/cli-release.yml">
    <img src="https://github.com/haiphucnguyen/askimo/actions/workflows/cli-release.yml/badge.svg" alt="CLI Build">
  </a>
  <a href="https://github.com/haiphucnguyen/askimo/actions/workflows/desktop-release.yml">
    <img src="https://github.com/haiphucnguyen/askimo/actions/workflows/desktop-release.yml/badge.svg" alt="Desktop Build">
  </a>
  <a href="./LICENSE">
    <img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License">
  </a>
  <a href="https://github.com/haiphucnguyen/askimo/releases">
    <img src="https://img.shields.io/github/v/release/haiphucnguyen/askimo" alt="Release">
  </a>
  <a href="./CONTRIBUTING.md#-enforcing-dco">
    <img src="https://img.shields.io/badge/DCO-Signed--off-green.svg" alt="DCO">
  </a>
</p>

<p align="center">
  <a href="https://github.com/haiphucnguyen/askimo/stargazers">
    <img src="https://img.shields.io/github/stars/haiphucnguyen/askimo?style=social" alt="GitHub Stars">
  </a>
  <img src="https://img.shields.io/github/commit-activity/m/haiphucnguyen/askimo" alt="Commit Activity">
  <img src="https://img.shields.io/github/last-commit/haiphucnguyen/askimo" alt="Last Commit">
  <img src="https://img.shields.io/badge/macOS-000000?logo=apple&logoColor=white" alt="macOS">
  <img src="https://img.shields.io/badge/Windows-0078D6?logo=windows&logoColor=white" alt="Windows">
  <img src="https://img.shields.io/badge/Linux-FCC624?logo=linux&logoColor=black" alt="Linux">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/OpenAI-Supported-412991" alt="OpenAI">
  <img src="https://img.shields.io/badge/Claude-Supported-542683" alt="Claude">
  <img src="https://img.shields.io/badge/Gemini-Supported-4285F4" alt="Gemini">
  <img src="https://img.shields.io/badge/Ollama-Supported-000000" alt="Ollama">
  <img src="https://img.shields.io/badge/LocalAI-Supported-00ADD8" alt="LocalAI">
  <img src="https://img.shields.io/badge/LMStudio-Supported-6B46C1" alt="LMStudio">
  <img src="https://img.shields.io/badge/DockerAI-Supported-2496ED" alt="DockerAI">
</p>

---

**[Why Askimo?](#why-askimo)** · **[Supported Providers](#supported-providers)** · **[Quick Start](#-quick-start)** · **[Comparisons](#askimo-vs-other-ai-clients)**

---

## 🚀 What is Askimo?

**Askimo is your freedom from AI vendor lock-in.** A beautiful native chat client that lets you use **any AI model** — OpenAI, Claude, Gemini, local models via Ollama — all in one place, with complete privacy.

Unlike ChatGPT Desktop (OpenAI-only) or LM Studio (local-only), **Askimo gives you the flexibility to switch providers instantly** while keeping all your conversations searchable and secure on your machine.

### ✨ Core Features

- 🔁 **Multi-provider freedom** — Switch between cloud and local AI models without leaving the app
- 🧠 **Built-in RAG** — Connect local folders for private, context-aware AI conversations
- 🔒 **100% local storage** — Your data stays on your machine, no cloud sync required
- 🔍 **Never lose context** — Search all conversations, star favorites, organize by project
- ⚡ **Plus: Optional CLI** — For automation and scripting workflows



## Supported Providers
### Cloud

* OpenAI

* Anthropic Claude

* Google Gemini

* X AI (Grok)

### Local

* Ollama

* LM Studio

* LocalAI

* Docker AI

Askimo works with any OpenAI-compatible API endpoint.

## 🚀 Quick Start

Download the installer for your operating system:

- **macOS**: [Download .dmg](https://github.com/haiphucnguyen/askimo/releases/latest/download/Askimo-Desktop-macos.dmg)
- **Windows**: [Download .msi](https://github.com/haiphucnguyen/askimo/releases/latest/download/Askimo-Desktop-windows.msi)
- **Linux**: [Download .deb](https://github.com/haiphucnguyen/askimo/releases/latest/download/Askimo-Desktop-linux.deb)

Or visit the [releases page](https://github.com/haiphucnguyen/askimo/releases) for all available versions.

**After installation:** Open Askimo, add your API keys (or connect to Ollama for local models), and start chatting.

---

## Why Askimo?

**The AI chat client that doesn't lock you in.** Most desktop AI tools force you to choose: cloud-only (ChatGPT) or local-only (LM Studio). Askimo is the only chat client that gives you **both** — plus private RAG, full search, and optional CLI automation.

| Feature | **Askimo** | **LM Studio** | **Ollama Desktop GUIs** | **ChatGPT Desktop** |
|--------|-------------|----------------|--------------------------|----------------------|
| Works with multiple providers | ✅ | ❌ (Local only) | ❌ (Local only) | ❌ (OpenAI only) |
| Works with local models | ✅ | ✅ | ✅ | ❌ |
| Works with cloud models (OpenAI, Claude, Gemini, etc.) | ✅ | ❌ | ❌ | ✅ |
| Desktop application | ✅ | ✅ | ✅ | ✅ |
| Command-line interface (CLI) | ✅ | ✅ (API server) | ✅ (CLI only) | ❌ |
| Automation / scripting support | ✅ | ✅ (via API) | ✅ (via CLI) | ❌ |
| RAG (Retrieval-Augmented Generation) | ✅ Desktop (Single folder) | ❌ | ❌ | ✅ (File upload) |
| Searchable chat history | ✅ | ✅ | Varies | ✅ |
| Star/favorite conversations | ✅ | ❌ | ❌ | ✅ (Pinned) |
| Custom directives / prompt profiles | ✅ | ✅ (System prompt) | ❌ | ✅ (Custom instructions) |
| Export conversations | ✅ | ❌ | ❌ | ✅ |
| Privacy (local storage) | ✅ | ✅ | ✅ | ❌ (Cloud sync) |
| Multi-workspace organization | ✅ (Projects) | ❌ | ❌ | ✅ (Projects + Memory) |
| Works offline (local models) | ✅ | ✅ | ✅ | ❌ |



[![Askimo Desktop Demo](public/desktop-demo.gif)](https://askimo.chat/desktop)

### 🎯 What You Get

- **Multi-Provider Support** - Switch between OpenAI, Claude, Gemini, X AI, and Ollama without leaving the app
- **100% Local Storage** - All chat history stored on your machine, never in the cloud
- **RAG (Retrieval-Augmented Generation)** - Connect a knowledge folder to give AI context from your documents, code, and notes
- **Smart Organization** - Star important conversations, search across all chats, create collections
- **Rich Markdown Support** - Code syntax highlighting, tables, images, and formatted text
- **Custom Directives** - Save reusable prompts and system messages for different tasks
- **Keyboard-First** - Quick shortcuts to create chats, switch providers, and search
- **Export Anywhere** - Download conversations in JSON, Markdown, or plain text

### 💡 Perfect For

- 💬 Daily AI conversations without context loss
- 📝 Writing and content creation with consistent AI assistance
- 🔍 Research that requires comparing responses from different models
- 🎨 Creative work with saved prompt templates
- 📚 Working with AI on your private knowledge base and documentation


**Screenshots:**

<p align="center">
  <img src="public/desktop_ai_provider_switcher.png" alt="Provider Switching" width="45%">
  <img src="public/desktop_chat_search.png" alt="Search & Favorites" width="45%">
  <img src="public/desktop_rag.png" alt="RAG" width="45%">
</p>

---

## ⚡ CLI for Automation

Need to automate AI tasks in scripts or CI/CD? Askimo includes a command-line interface.

```bash
# Pipe directly from commands
cat app.log | askimo -p "Find critical errors and suggest fixes"
git diff | askimo -p "Review this code for bugs and improvements"

# Use recipes for repetitive tasks
askimo -r gitcommit
```

**Installation:**
```bash
# macOS/Linux
curl -sSL https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/installation/install.sh | bash

# Windows (PowerShell)
iwr -useb https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/installation/install.ps1 | iex
```

[**Learn more about CLI →**](https://askimo.chat/cli)


## 🌐 Language Support

Askimo Desktop is fully localized into:

* English (en)

* Simplified Chinese (zh_CN)

* Traditional Chinese (zh_TW)

* Japanese (ja_JP)

* Korean (ko_KR)

* French (fr)

* Spanish (es)

* German (de)

* Portuguese - Brazil (pt_BR)

* Vietnamese (vi)

More languages coming soon.

## Support
If you enjoy this project, here are a few simple ways to show support:

* Star the repo - A quick ⭐️ at the top helps a lot and keeps the project growing.

* Contribute - Spot a bug or want to improve something? Pull requests are always welcome.

* Share feedback - Got ideas or suggestions? Feel free to open an issue or start a discussion.

Thanks for being part of the journey! 🙌
## License

Apache 2.0. See [LICENSE](./LICENSE).