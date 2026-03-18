<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="public/github-logo-dark.svg">
    <img alt="Askimo - AI toolkit for your workflows." src="public/github-logo-light.svg">
  </picture>
</p>

<p align="center">
  <b><a href="https://askimo.chat">askimo.chat</a></b> · Local-first AI agent platform — chat, search, run, automate.
</p>

<p align="center">
  <a href="https://github.com/haiphucnguyen/askimo/actions/workflows/cli-release.yml">
    <img src="https://github.com/haiphucnguyen/askimo/actions/workflows/cli-release.yml/badge.svg" alt="CLI Build">
  </a>
  <a href="https://github.com/haiphucnguyen/askimo/actions/workflows/desktop-release.yml">
    <img src="https://github.com/haiphucnguyen/askimo/actions/workflows/desktop-release.yml/badge.svg" alt="Desktop Build">
  </a>
  <a href="./LICENSE">
    <img src="https://img.shields.io/badge/License-AGPLv3-blue.svg" alt="License">
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
  <img src="https://img.shields.io/badge/Grok-Supported-1DA1F2" alt="Grok">
  <img src="https://img.shields.io/badge/Ollama-Supported-000000" alt="Ollama">
  <img src="https://img.shields.io/badge/LocalAI-Supported-00ADD8" alt="LocalAI">
  <img src="https://img.shields.io/badge/LMStudio-Supported-6B46C1" alt="LMStudio">
  <img src="https://img.shields.io/badge/DockerAI-Supported-2496ED" alt="DockerAI">
</p>

<p align="center">
  <a href="https://github.com/haiphucnguyen/askimo/releases/latest"><strong>📥 Download</strong></a> •
  <a href="https://askimo.chat/docs/"><strong>📖 Documentation</strong></a> •
  <a href="https://github.com/haiphucnguyen/askimo/discussions"><strong>💬 Discussions</strong></a> •
  <a href="https://github.com/haiphucnguyen/askimo/stargazers"><strong>⭐ Star on GitHub</strong></a>
</p>

---

## 🚀 What is Askimo?

**Askimo is a local-first AI agent platform** — a desktop app and CLI that connects any AI model to your files, tools, and workflows.

Go beyond chatting: Askimo can search your documents and code with smart RAG, execute Python, Bash, and JavaScript scripts it writes itself, call external tools and services via MCP, analyze images, and remember every conversation — all without your data leaving your machine.

Think of it as a personal AI workbench: use Claude for code reviews, GPT-4o for document analysis, a free local Ollama model for quick tasks, and let all of them share the same knowledge base. Switch between models in a single click, even mid-conversation.

---

## Why Choose Askimo?

✅ **Multi-model in one place** — Switch between ChatGPT, Claude, Gemini, Grok, Ollama & more, even mid-session  
✅ **Smart document search (RAG)** — Index local folders, files, and web URLs; an AI classifier decides when retrieval is actually needed, cutting unnecessary searches by ~67%  
✅ **Run code the AI writes** — Execute Python, Bash, and JavaScript directly from chat with one click; Python auto-installs its own dependencies  
✅ **Vision & image support** — Attach screenshots, diagrams, or photos; multimodal models analyze them right in the conversation  
✅ **Extend with MCP tools** — Connect any MCP-compatible server (filesystems, databases, Git, custom APIs) via stdio or HTTP at the global or per-project level  
✅ **Automate with Recipes** — YAML-based prompt templates with variables, file I/O, and post-actions for CLI automation  
✅ **Local-first & private** — Conversations, indexes, and telemetry live entirely on your machine  
✅ **Usage & cost tracking** — Built-in local telemetry: tokens used, estimated cost, and RAG performance per provider  
✅ **Free & open source** — AGPLv3, forever

---

## Supported Providers

**Cloud:** OpenAI · Claude · Gemini · Grok (xAI)  
**Local:** Ollama · LM Studio · LocalAI · Docker AI

*Works with any OpenAI-compatible endpoint.*

## 🚀 Quick Start

**[Download Askimo for your operating system →](https://askimo.chat/download/)**

Installers available for macOS (Apple Silicon & Intel), Windows, and Linux (ARM64 & x64).

**After installation:** Open Askimo, add your API keys (or connect to Ollama for local models), and start chatting. [**Setup guide →**](https://askimo.chat/docs/desktop/ai-providers/)

### System Requirements

- **Memory**: 50-300 MB for Askimo itself (AI models require additional memory)
- **Operating System**: 
  - macOS 11.0 (Big Sur) or later
  - Windows 10 or later
  - Linux (Ubuntu 20.04+, Debian 11+, Fedora 35+, or compatible)
- **Disk Space**: 250 MB for application

---

### 🎬 See It in Action

**💬 AI Chat with RAG:** Ask questions about your files, documents, and code using any AI model

[![Askimo RAG Demo](public/askimo_rag_1280.gif)](public/askimo_rag_1920.gif)

**🐍 One-Click Code Execution:** Ask AI to write a Python, Bash, or JavaScript script — a ▶ Run button appears inline. Python auto-installs missing packages. No copy-paste, no manual setup.

[![Askimo Run Script Demo](public/askimo_run_script_1280.gif)](public/askimo_run_script_1920.gif)

**🔌 MCP Tools in Action:** Connect any MCP-compatible server and let the AI call real tools — query databases, read files, call APIs, and automate workflows directly from chat.

[![Askimo MCP Demo](public/askimo_mcp_1280.gif)](public/askimo_mcp_1920.gif)

### 🎯 What You Get

- 🖥️ **Never Lose Your Conversations** — Your chats stay open even after closing the app or restarting your computer. No more "lost tab" frustration
- 🔄 **Use the Best AI for Each Task** — Need help with code? Use Claude. General questions? Try ChatGPT. Writing a quick email? Use a free local model. Switch instantly between different AIs
- 🔒 **Complete Privacy** — Everything stays on your computer. Your conversations, your data, your control. Nothing is sent to the cloud unless you choose to ask an AI
- 🧠 **Smart Document Search (RAG)** — Point Askimo to local folders, individual files, or web URLs and ask questions about your actual content. A hybrid BM25 + vector search engine retrieves the most relevant context, and an AI classifier skips retrieval entirely when it isn't needed — keeping responses fast and accurate
- ⚙️ **Run Code the AI Writes** — Ask AI to write a Python, Bash, or JavaScript script, then run it with one click. Python scripts automatically create a managed virtualenv and install any missing packages. No setup required
- 🔌 **MCP Tool Integrations** — Connect any MCP-compatible server (filesystem, databases, Git, custom APIs) via stdio or HTTP — at the global level or scoped to a specific project
- 📋 **Recipe Automation (CLI)** — Save prompt templates as YAML recipes with input variables, file I/O, and post-actions. Run them in one command: `askimo -r myrecipe arg1 arg2`
- ⭐ **Stay Organized** — Star your favorite conversations, search through everything you've ever asked, and keep work separate from personal chats
- 📊 **Beautiful Formatting** — See code with syntax highlighting, tables, diagrams, and properly formatted text. Copy and paste ready to use
- 🎯 **Save Your Favorite Prompts** — Create reusable templates for things you ask often. One click to use your "proofreader" or "code reviewer" assistant
- ⚡ **Work Fast** — Keyboard shortcuts for everything. Create new chats, switch AIs, and search without touching your mouse
- 📈 **Local Usage & Cost Telemetry** — Track tokens used, estimated cost, RAG hit rate, and response times per provider. All data stays on your machine — nothing is uploaded
- 💾 **Export Everything** — Download your conversations as documents. Share insights or keep backups however you want


**Screenshots:**

<p align="center">
  <img src="public/desktop_ai_provider_switcher.png" alt="Provider Switching" width="45%">
  <img src="public/mcp_tools_configure.png" alt="MCP Tools Configuration" width="45%">
  <img src="public/desktop_rag.png" alt="RAG" width="45%">
</p>

---

## ⚡ Command Line Tool (Optional)

**For advanced users:** Askimo also works from the command line, perfect for automating repetitive tasks.

**Installation:**
```bash
# macOS/Linux
curl -sSL https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/installation/install.sh | bash

# Windows (PowerShell)
iwr -useb https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/installation/install.ps1 | iex
```

[**Learn more about the command line tool →**](https://askimo.chat/cli/)

---

## 🛠️ Developer Setup

Want to build and run Askimo from source? Here's how to get started:

### Prerequisites

- **JDK 21** or later
- **Git** for cloning the repository

### Clone & Build

```bash
# Clone the repository
git clone https://github.com/haiphucnguyen/askimo.git
cd askimo

# Run the desktop application
./gradlew :desktop:run

# Build native installers
./gradlew :desktop:package
```

### Project Structure

- **`desktop/`** - Desktop application (Kotlin + Compose Multiplatform)
- **`cli/`** - Command-line interface (JLine3 REPL + GraalVM native image)
- **`shared/`** - Core business logic: LLM providers, RAG, MCP, memory, tools

### Contributing

Ready to contribute? Check out our [**Contributing Guide**](./CONTRIBUTING.md) for detailed development guidelines, code standards, and how to submit pull requests.

> **📚 Note:** For detailed information on customizing Askimo, building from source, and development workflows, follow the [**Development Getting Started Guide**](https://askimo.chat/docs/development/getting-started/).

---

## 🌐 Available in Your Language

Askimo speaks your language! The entire app interface is available in:

🇺🇸 English • 🇨🇳 Chinese (Simplified & Traditional) • 🇯🇵 Japanese • 🇰🇷 Korean • 🇫🇷 French • 🇪🇸 Spanish • 🇩🇪 German • 🇧🇷 Portuguese • 🇻🇳 Vietnamese

More languages coming soon. Want to help translate? [Let us know!](https://github.com/haiphucnguyen/askimo/discussions)

---

## 🤝 Need Help or Want to Contribute?

### Get Help

- 📖 **[User Guide](https://askimo.chat/docs/)** - Step-by-step instructions and tips
- 💬 **[Community Forum](https://github.com/haiphucnguyen/askimo/discussions)** - Ask questions, share your experience, get help from other users
- 🐛 **[Report a Problem](https://github.com/haiphucnguyen/askimo/issues)** - Something not working? Let us know
- 📧 **Email Us** - Need private help? Contact support@askimo.chat

### Ways to Contribute

We'd love your help making Askimo better! Here are some easy ways to get involved:

- ⭐ **Star the repo** - A quick click at the top helps others discover Askimo
- 🐛 **Report bugs** - Found something broken? [Tell us about it](https://github.com/haiphucnguyen/askimo/issues/new?template=bug_report.md)
- 💡 **Share ideas** - Have a suggestion? [We want to hear it](https://github.com/haiphucnguyen/askimo/issues/new?template=feature_request.md)
- 🌍 **Help translate** - Know another language? Help make Askimo available to more people
- 💻 **Contribute code** - Comfortable with coding? Check our [Contributing Guide](./CONTRIBUTING.md)
- 📝 **Improve documentation** - Spot a typo or confusing explanation? Fix it!

No contribution is too small - we appreciate all help! 🙌

---

## ❤️ Enjoying Askimo?

If you find Askimo helpful, here are a few simple ways to show support:

* **Star the repo** - A quick ⭐️ at the top helps a lot and keeps the project growing.

* **Contribute** - Spot a bug or want to improve something? Pull requests are always welcome.

* **Share feedback** - Got ideas or suggestions? Feel free to open an issue or start a discussion.

Thanks for being part of the journey! 🙌

---

## 📄 License

AGPLv3. See [LICENSE](./LICENSE).