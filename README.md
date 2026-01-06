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

**Askimo is a native chat client designed to give you flexibility in choosing AI models.** Use OpenAI, Claude, Gemini, or local models via Ollama, all from one application with your privacy in mind.

We built Askimo to help you switch between different AI providers easily, keeping all your conversations searchable and stored securely on your own machine. Whether you prefer cloud-based models or want to run everything locally, Askimo supports both approaches.


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

---

## Why Askimo?

**The AI chat client that doesn't lock you in.** Most desktop AI tools force you to choose: cloud-only (ChatGPT) or local-only (LM Studio). Askimo is the only chat client that gives you **both** — plus private RAG, full search, and optional CLI automation.

| Feature | **Askimo** | **LM Studio** | **Ollama GUIs** | **ChatGPT Desktop** |
|--------|-------------|----------------|------------------|----------------------|
| **Multi-provider support** | ✅ Cloud + Local | ❌ Local only | ❌ Local only | ❌ OpenAI only |
| **Local model support** | ✅ Via Ollama | ✅ Native | ✅ Native | ❌ |
| **Cloud model support** | ✅ OpenAI, Claude, Gemini, Grok | ❌ | ❌ | ✅ OpenAI |
| **Desktop app** | ✅ | ✅ | ✅ | ✅ |
| **CLI for automation** | ✅ | ✅ API server | ✅ Ollama CLI | ❌ |
| **RAG / Document context** | ✅ Folder-based | ❌ | ❌ | ✅ File upload |
| **Searchable history** | ✅ Full-text | ✅ | Varies by GUI | ✅ |
| **Organize conversations** | ✅ Star + Projects | ❌ | ❌ | ✅ Pin + Projects |
| **Custom prompts/directives** | ✅ | ✅ System prompt | ❌ | ✅ Custom instructions |
| **Export conversations** | ✅ JSON/MD/HTML | ❌ | ❌ | ✅ |
| **Privacy (local-only storage)** | ✅ | ✅ | ✅ | ⚠️ Optional cloud sync |
| **Works offline** | ✅ With local models | ✅ | ✅ | ❌ |
| **Free & open source** | ✅ Apache 2.0 | ✅ Free (proprietary) | ✅ Varies | ❌ Subscription |



[![Askimo Desktop Demo](public/desktop-demo.gif)](https://askimo.chat/desktop)

### 🎯 What You Get

- 🖥️ **Native Desktop Application** - Keep long conversations in one session without worrying about browser crashes or tab closures
- 🔄 **Multi-Provider Support** - Switch between OpenAI, Claude, Gemini, X AI, LMStudio, LocalAI, Ollama, DockerAI and their models to utilize each model's strengths and optimize costs. Use expensive models for complex tasks, cheaper ones for simple queries
- 🔒 **100% Local Storage** - All chat history stored on your machine, never in the cloud
- 🧠 **RAG (Retrieval-Augmented Generation)** - Connect multiple folders and files to give AI context from your documents, code, and notes. Uses hybrid search combining vector embeddings (JVector) and keyword search (Lucene) for highly accurate information retrieval
- ⭐ **Smart Organization** - Star important conversations, search across all chats, create collections
- 📊 **Rich Markdown Support** - Code syntax highlighting, tables, charts, images, and formatted text
- 🎯 **Custom Directives** - Save reusable prompts and system messages for different tasks
- ⚡ **Keyboard-First** - Quick shortcuts to create chats, switch providers, and search
- 💾 **Export Anywhere** - Download conversations in JSON, Markdown, or HTML

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