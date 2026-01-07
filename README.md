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

<p align="center">
  <a href="https://github.com/haiphucnguyen/askimo/releases/latest"><strong>📥 Download</strong></a> •
  <a href="https://askimo.chat/docs"><strong>📖 Documentation</strong></a> •
  <a href="https://github.com/haiphucnguyen/askimo/discussions"><strong>💬 Discussions</strong></a> •
  <a href="https://github.com/haiphucnguyen/askimo/stargazers"><strong>⭐ Star on GitHub</strong></a>
</p>

---

## 🚀 What is Askimo?

**Askimo is a privacy-focused desktop chat client that works with any AI model.** Switch between OpenAI, Claude, Gemini, and local models like Ollama—all in one app with complete data privacy.

Use GPT-4 for complex reasoning, Claude for writing, and local models for privacy—without changing apps or losing conversation context. All your chat history stays on your machine, fully searchable and organized.

---

## Why Askimo?

**The only desktop AI client that supports both cloud and local models.** Most tools force you to choose one or the other.

| Feature | **Askimo** | **LM Studio** | **Ollama GUIs** | **ChatGPT Desktop** |
|---------|------------|---------------|-----------------|---------------------|
| **Multi-provider** | ✅ Cloud + Local | ❌ Local only | ❌ Local only | ❌ OpenAI only |
| **RAG / Codebase context** | ✅ Folder-based | ❌ | ❌ | ⚠️ File upload only |
| **CLI automation** | ✅ Built-in | ✅ API server | ✅ Ollama CLI | ❌ |
| **Privacy** | ✅ Local storage | ✅ | ✅ | ⚠️ Optional cloud sync |
| **Open source** | ✅ Apache 2.0 | ⚠️ Free (proprietary) | ✅ Varies | ❌ Subscription |

---

## Supported Providers

**Cloud:** OpenAI · Claude · Gemini · Grok  
**Local:** Ollama · LM Studio · LocalAI · Docker AI

*Works with any OpenAI-compatible endpoint.*

## 🚀 Quick Start

Download the installer for your operating system:

- **macOS**: [Download .dmg](https://github.com/haiphucnguyen/askimo/releases/latest/download/Askimo-Desktop-macos.dmg)
- **Windows**: [Download .msi](https://github.com/haiphucnguyen/askimo/releases/latest/download/Askimo-Desktop-windows.msi)
- **Linux**: [Download .deb](https://github.com/haiphucnguyen/askimo/releases/latest/download/Askimo-Desktop-linux.deb)

Or visit the [releases page](https://github.com/haiphucnguyen/askimo/releases) for all available versions.

**After installation:** Open Askimo, add your API keys (or connect to Ollama for local models), and start chatting. [**Setup guide →**](https://askimo.chat/docs/desktop/ai-providers/)

### System Requirements

- **Memory**: 50-300 MB for Askimo itself (AI models require additional memory)
- **Operating System**: 
  - macOS 11.0 (Big Sur) or later
  - Windows 10 or later
  - Linux (Ubuntu 20.04+, Debian 11+, Fedora 35+, or compatible)
- **Disk Space**: 250 MB for application
- **Internet**: Required for cloud providers, optional for local models

---

[![Askimo Desktop Demo](public/desktop-demo.gif)](https://askimo.chat/desktop)

### 🎯 What You Get

- 🖥️ **Native Desktop Application** - Maintain conversations with 200+ messages without browser crashes or tab closures. Your work stays intact across restarts
- 🔄 **Multi-Provider Support** - Switch between OpenAI, Claude, Gemini, X AI, LMStudio, LocalAI, Ollama, DockerAI and their models to utilize each model's strengths and optimize costs. Use expensive models for complex tasks, cheaper ones for simple queries
- 🔒 **100% Local Storage** - All chat history stored on your machine, never in the cloud. Full privacy and control over your data
- 🧠 **RAG (Retrieval-Augmented Generation)** - Connect your project folders and ask questions like "How does our authentication work?" to get answers from your actual codebase. Uses hybrid search combining vector embeddings (JVector) and keyword search (Lucene) for highly accurate information retrieval
- ⭐ **Smart Organization** - Star important conversations, full-text search across all chats, organize by projects
- 📊 **Rich Markdown Support** - Code syntax highlighting, tables, charts, images, and formatted text. Export-ready output
- 🎯 **Custom Directives** - Save reusable prompts and system messages. One-click access to your favorite AI personalities and workflows
- ⚡ **Keyboard-First** - Quick shortcuts to create chats, switch providers, and search. Built for productivity
- 📈 **Usage Telemetry** - Track your AI usage with detailed metrics on token consumption, response times, and costs. Monitor RAG operations including classification decisions, retrieval performance, and chunks retrieved. All data stays local on your machine
- 💾 **Export Anywhere** - Download conversations in JSON, Markdown, or HTML. Take your data wherever you need it


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

---

## 🤝 Community & Support

### Get Help

- 📖 **[Documentation](https://askimo.chat/docs)** - Comprehensive guides and tutorials
- 💬 **[GitHub Discussions](https://github.com/haiphucnguyen/askimo/discussions)** - Ask questions, share tips, and connect with other users
- 🐛 **[Issue Tracker](https://github.com/haiphucnguyen/askimo/issues)** - Report bugs or request features
- 📧 **Email Support** - For private inquiries: support@askimo.chat

### Contributing

We welcome contributions! Here's how you can help:

- 🐛 **Report bugs** - Found an issue? [Open a bug report](https://github.com/haiphucnguyen/askimo/issues/new?template=bug_report.md)
- 💡 **Suggest features** - Have ideas? [Share them here](https://github.com/haiphucnguyen/askimo/issues/new?template=feature_request.md)
- 🌍 **Translate** - Help localize Askimo to your language
- 💻 **Submit PRs** - Check our [Contributing Guide](./CONTRIBUTING.md) to get started
- 📝 **Improve docs** - Documentation improvements are always appreciated

---

## Support

If you enjoy this project, here are a few simple ways to show support:

* Star the repo - A quick ⭐️ at the top helps a lot and keeps the project growing.

* Contribute - Spot a bug or want to improve something? Pull requests are always welcome.

* Share feedback - Got ideas or suggestions? Feel free to open an issue or start a discussion.

Thanks for being part of the journey! 🙌
## License

Apache 2.0. See [LICENSE](./LICENSE).