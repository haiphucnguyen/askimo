<p align="center">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="public/askimo-logo.svg">
        <img alt="Askimo - AI toolkit for your workflows." src="public/askimo-logo-dark.svg">
      </picture>
</p>

<p align="center">
  <b><a href="https://askimo.chat">askimo.chat</a></b> · AI for your workflows, with the freedom to choose any provider.
</p>

<p align="center">
  <a href="https://github.com/haiphucnguyen/askimo/actions/workflows/release.yml">
    <img src="https://github.com/haiphucnguyen/askimo/actions/workflows/release.yml/badge.svg" alt="Build">
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

Askimo is a **provider-agnostic AI toolkit** that brings powerful AI capabilities to your command line and automation workflows.  
From chatting with LLMs to automating DevOps pipelines, Askimo connects to any provider - OpenAI, X AI, Gemini, Anthropic, or local models like Ollama.

> **AI for your workflows, with the freedom to choose any provider.**

---

## ✨ Why Askimo

* **Provider Freedom**  
Switch between OpenAI, Gemini, X AI, Anthropic, or Ollama with the same commands. No vendor lock-in as the AI landscape evolves.

* **Automation-First Design**  
Built for DevOps and automation workflows. Pipe files, logs, or command output into Askimo and let AI handle analysis, transformation, and decision-making.

* **RAG-Enabled Projects**  
Create intelligent project workspaces with built-in vector search (pgvector). Your AI assistant knows your codebase, documentation, and project context.

* **Reusable Recipes**  
Build and share parameterized AI workflows. Create templates for code reviews, log analysis, documentation generation, and more.

* **Dual Interface**  
Choose your workflow: interactive chat for exploration, or non-interactive mode perfect for scripts, CI/CD pipelines, and automation.

* **Extensible Platform**  
Add custom providers, commands, and integrations. Askimo grows with your team's needs.

---

## 🎬 Demo

**Summarizing files, generating commit messages, and integrating with Git**

[![Askimo Demo 1](https://raw.githubusercontent.com/haiphucnguyen/askimo/main/public/demo1.gif)](https://askimo.chat)

**Chatting with multiple AI providers**

[![Askimo Demo 2](https://raw.githubusercontent.com/haiphucnguyen/askimo/main/public/demo2.gif)](https://askimo.chat)

---

## 🧠 Core Capabilities

### AI Chat
- Interactive conversations with multiple AI providers

### Knowledge Management
- **RAG-enabled projects** with automatic document indexing
- **Vector search** powered by PostgreSQL + pgvector
- **Project workspaces** that give AI context about your codebase
- **Contextual responses** based on your project's files and documentation

### Automation & DevOps
- **Pipeline-friendly** non-interactive mode for CI/CD integration
- **Recipe system** for reusable, parameterized AI workflows
- **Log analysis** and system monitoring with AI insights
- **Stdin/stdout** support for seamless integration with existing tools

### Platform Features
- **Provider-agnostic** architecture (OpenAI, Gemini, X AI, Ollama)
- **Extensible** plugin system for custom providers and commands
- **Configuration management** with per-provider parameter tuning

---

## ⚙️ Quickstart

### macOS / Linux (Homebrew)
```bash
brew tap haiphucnguyen/askimo
brew install askimo
askimo
```

### Windows (Scoop)
```bash
scoop bucket add askimo https://github.com/haiphucnguyen/scoop-askimo
scoop install askimo
askimo
```

Other ways to install → [Installation Guide](https://askimo.chat/reference/installation/)

👉 Once installed, you can connect Askimo to providers like Ollama, OpenAI, Gemini, or X AI and start chatting.

📖 See [Getting Started](https://askimo.chat/reference/getting-started/) for tutorials on setting up Ollama, adding API keys (OpenAI, Gemini, X AI), switching providers, and running real workflow examples.

---

## 💬 CLI Usage

Askimo supports **two modes** for running commands:

### Interactive Mode
Start Askimo without arguments to enter interactive mode:
```bash
askimo
askimo> :help
```

### Non-Interactive Mode
Run commands directly from the command line:
```bash
askimo --help
askimo --list-providers
askimo --set-provider openai
echo "function add(a, b) { return a + b; }" | askimo -p "Convert this to TypeScript"
```

### Direct Chat (Non-Interactive)
Send a single message to AI without entering interactive mode:
```bash
askimo -p "Your prompt here"
askimo --prompt "Your prompt here"
```

**With piped input:**
```bash
# Analyze code from stdin
echo "function add(a, b) { return a + b; }" | askimo -p "Convert this to TypeScript"

# Process file contents
cat myfile.js | askimo --prompt "Explain this code"

# Analyze git changes
git diff | askimo -p "Summarize these changes"

# Process command output
ls -la | askimo -p "Explain these file permissions"
```

---

## 🧩 Extending Askimo

Askimo is designed to be pluggable, so you can tailor it to your needs:

- Add a new chat model → [Creating a New Chat Model](https://askimo.chat/development/creating-new-chat-model/)
- Create a new command → [Creating a New Command](https://askimo.chat/development/creating-new-command/)

---

## 🤝 Contributing

- Fork & clone the repo
- Create a feature branch
- Open a PR
