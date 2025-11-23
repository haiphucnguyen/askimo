<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="public/askimo-logo.svg">
    <img alt="Askimo - AI toolkit for your workflows." src="public/askimo-logo-dark.svg">
  </picture>
</p>

<p align="center">
  <b><a href="https://askimo.chat">askimo.chat</a></b> · AI for your workflows — on desktop or in the terminal, with the freedom to choose any provider.
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
    <img src="https://img.shields.io/github/v/release/haiphucnguyen/askimo" alt="GitHub release">
  </a>
  <a href="./CONTRIBUTING.md#-enforcing-dco">
    <img src="https://img.shields.io/badge/DCO-Signed--off-green.svg" alt="DCO">
  </a>
</p>

# Askimo

Askimo is a **provider-agnostic AI toolkit** offering two powerful interfaces:

- **Askimo Desktop** — a native, multi-provider AI chat app
- **Askimo CLI** — a command-line tool for automation, DevOps, and workflows

Use OpenAI, Claude, Gemini, X AI, or local models like Ollama — all from one unified platform.

> **AI for your workflows — with the freedom to choose any provider.**

---

# 🚀 Askimo Products

## 💬 Askimo Desktop
A beautiful, native desktop app that lets you chat with multiple AI providers side-by-side.  
Perfect for writers, developers, researchers, and anyone who needs fast, consistent AI conversations.

### Key Desktop Features
- Switch between OpenAI, Claude, Gemini, X AI, and Ollama instantly
- Persistent chat history stored locally
- Rich markdown rendering, syntax highlighting, images, and file attachments
- Star and save important conversations
- Custom directives to shape AI responses
- Smart search across your entire chat history
- Local-first: no tracking, no cloud storage
- Prompt library, shortcuts, and beautiful UI

👉 **Get Askimo Desktop**: https://askimo.chat/desktop

---

## 🖥 Askimo CLI
A powerful AI automation tool built for developers, DevOps engineers, and data workflows.

### Key CLI Features
- Provider-agnostic commands
- Pipe logs, files, or command outputs directly into AI
- Reusable Recipes for automation
- RAG-enabled project workspaces using PostgreSQL + pgvector
- Non-interactive mode for CI/CD pipelines
- Extensible plug-in system

👉 **Install Askimo CLI**: https://askimo.chat/cli

---

# ✨ Why Askimo

* **Provider Freedom**  
  Use OpenAI, Gemini, X AI, Anthropic, or Ollama with the same interface.

* **Unified Workflow**  
  Switch between desktop chat and terminal automation seamlessly.

* **RAG-Enabled Projects**  
  Give AI full context about your codebase and docs using vector search.

* **Reusable Recipes**  
  Automate code review, logs, documentation generation, and more.

* **Extensible Platform**  
  Add custom providers, commands, and integrations.

---

# 🎬 Demo

## Askimo Desktop — Multi-Provider Chat
[![Askimo Demo 2](public/desktop-demo.gif)](https://askimo.chat/desktop)

## Askimo CLI — Files, Logs, Git, and Pipelines
[![Askimo Demo 1](public/cli-demo.gif)](https://askimo.chat/cli)

---

# 🧠 Core Capabilities

### AI Chat (Desktop & CLI)
- Multi-provider chat
- Instant provider switching
- Persistent history
- Rich formatting

### Knowledge Management (CLI)
- RAG projects powered by pgvector
- Automatic code/document indexing
- Context-rich answers

### Automation & DevOps (CLI)
- Non-interactive mode for pipelines
- Recipes for reusable AI workflows
- Stdin/stdout for flexible integration
- Log analysis & transformation

### Platform Features
- Provider-agnostic architecture
- Extensible plugin system
- Local-first privacy controls
- Multi-interface ecosystem

---

# 🛠 Installation

Askimo has dedicated installation guides for each product and platform:

- **Askimo Desktop → https://askimo.chat/docs/desktop/installation/**
- **Askimo CLI → https://askimo.chat/docs/cli/installation/**

All installation methods (Homebrew, Scoop, JAR, macOS, Windows, Linux) are available in the docs.

---

# 📚 Usage

- **Desktop Usage** → https://askimo.chat/desktop
- **CLI Usage** → https://askimo.chat/cli

---

# 🧩 Extending Askimo

- Create a new provider → https://askimo.chat/docs/development/creating-new-command/
- Add new commands → https://askimo.chat/docs/development/creating-new-chat-provider/

---

# 🤝 Contributing

Contributions are welcome!  
Check out:

- **CONTRIBUTING.md**
- **DCO guidelines**
- **Developer Setup Docs**

---

# 📄 License

Apache 2.0 — see [LICENSE](./LICENSE)
