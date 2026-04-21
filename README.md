<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="public/github-logo-dark.svg">
    <img alt="Askimo - AI toolkit for your workflows." src="public/github-logo-light.svg">
  </picture>
</p>

<p align="center">
  <b>One app. Every AI model. Your files stay local.</b>
</p>

<p align="center">
  Chat · Search your files · Run scripts · Build multi-step AI workflows - all offline-capable, all on your machine.
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
  <a href="https://github.com/haiphucnguyen/askimo/releases">
    <img src="https://img.shields.io/github/downloads/haiphucnguyen/askimo/total" alt="Total Downloads">
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

## Why Askimo?

Most AI desktop apps lock you into one model and one workflow. Askimo doesn't.

- **Tired of switching tabs** between ChatGPT, Claude, and Gemini? Askimo runs all of them in one place.
- **Worried about your files leaving your machine?** RAG runs locally - your documents never touch a cloud server.
- **Need more than chat?** Run Python/Bash scripts, chain multi-step AI workflows (Plans), and connect MCP tools - all from the same app.

---

## See It in Action

**Multi-step AI Plans** - fill in a form, get a finished deliverable:

[![Askimo Plans Demo](public/askimo_plan_1280.gif)](public/askimo_plan_1920.gif)

**RAG** - search and chat with your local files:

[![Askimo RAG Demo](public/askimo_rag_1280.gif)](public/askimo_rag_1920.gif)

**Script runner** - execute Python, Bash, and JavaScript from chat:

[![Askimo Run Script Demo](public/askimo_run_script_1280.gif)](public/askimo_run_script_1920.gif)

**MCP tools** - connect any MCP-compatible server:

[![Askimo MCP Demo](public/askimo_mcp_1280.gif)](public/askimo_mcp_1920.gif)

<p align="center">
  <img src="public/desktop_ai_provider_switcher.png" alt="Provider Switching" width="45%">
  <img src="public/mcp_tools_configure.png" alt="MCP Tools Configuration" width="45%">
  <img src="public/desktop_rag.png" alt="RAG" width="45%">
</p>

---

## Quick Start

**[Download for macOS, Windows, or Linux →](https://askimo.chat/download/)**

1. Install and open Askimo
2. Add a provider - paste an API key (OpenAI, Claude, Gemini…) or point it at a running Ollama instance
3. Start chatting

[Full setup guide →](https://askimo.chat/docs/desktop/ai-providers/)

### System Requirements

| | |
|---|---|
| **OS** | macOS 11+, Windows 10+, Linux (Ubuntu 20.04+, Debian 11+, Fedora 35+) |
| **Memory** | 50–300 MB (AI models require additional memory depending on provider) |
| **Disk** | 250 MB |

---

## Features

- **Multi-provider** - Switch between OpenAI, Claude, Gemini, Grok, Ollama, LM Studio, LocalAI, Docker AI, or any OpenAI-compatible endpoint per session
- **Local RAG** - Index local folders, files, and web URLs. Hybrid BM25 + vector retrieval with an AI classifier that skips retrieval when the query doesn't need it. Your data never leaves your machine.
- **Plans (agentic workflows)** - Chain multi-step AI pipelines from a form UI. Each step builds on the previous; progress shown live. Export as PDF or Word. Define your own plans in YAML or generate them by describing your workflow in plain English.
- **Script runner** - Execute Python, Bash, and JavaScript from chat. Python runs in an auto-managed virtualenv with automatic dependency installation.
- **MCP tool integration** - Connect MCP-compatible servers via stdio or HTTP, scoped globally or per project
- **Persistent sessions** - Conversations stored in a local SQLite database, restored on restart
- **Vision** - Attach images to conversations; works with any multimodal model
- **CLI** - Native binary (GraalVM). Scriptable, automatable, headless-friendly.
- **Local telemetry** - Token usage, cost estimates, RAG performance per provider. Nothing uploaded.
- **i18n** - English, Chinese (Simplified & Traditional), Japanese, Korean, French, Spanish, German, Portuguese, Vietnamese

---

## Plans - Multi-Step AI Workflows

A single prompt cannot reason properly across multiple stages. Ask one prompt to research, analyse, and conclude simultaneously and the AI skips the dependencies between those stages.

Plans mirror how experts actually think: each step has one focused job and one persona - researcher, analyst, strategist, writer. The output of each step feeds into the next as grounded context. No copy-pasting. No re-prompting.

**Built-in plans:**

| Plan | What it does |
|---|---|
| 💼 Job Application Writer | Analyses a job description, matches your CV, writes a tailored cover letter and ATS-optimised resume |
| ✍️ Blog Post Writer | Generates an outline, writes the full draft, adds SEO metadata, outputs the polished post |
| 🏆 Competitor Analysis | Profiles a competitor, compares against your product, produces a strategic opportunities report |
| 📋 Meeting Notes Processor | Structures raw notes, extracts action items with owners, produces shareable minutes |
| 📝 Research Report | Researches a topic and writes a structured report with executive summary and key findings |
| 📧 Email Writer | Drafts and self-refines a professional email from a one-line description |

**Create your own:** describe your workflow in plain English - the AI generates valid plan YAML instantly. Fine-tune in the built-in editor or duplicate any built-in plan as a starting point.

---

## Supported Providers

**Cloud:** OpenAI · Anthropic Claude · Google Gemini · xAI Grok  
**Local:** Ollama · LM Studio · LocalAI · Docker AI  
**Custom:** Any OpenAI-compatible endpoint via custom base URL

---

## CLI (Optional)

```bash
# macOS/Linux
curl -sSL https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/installation/install.sh | bash

# Windows (PowerShell)
iwr -useb https://raw.githubusercontent.com/haiphucnguyen/askimo/main/tools/installation/install.ps1 | iex
```

[CLI documentation →](https://askimo.chat/cli/)

---

## Building from Source

### Prerequisites

- JDK 21+
- Git

```bash
git clone https://github.com/haiphucnguyen/askimo.git
cd askimo

# Run the desktop app
./gradlew :desktop:run

# Build native installers
./gradlew :desktop:package

# Build CLI native binary (requires GraalVM)
./gradlew :cli:nativeCompile
```

### Project Structure

| Module | Description |
|---|---|
| `desktop/` | Compose Multiplatform desktop application |
| `desktop-shared/` | Shared UI components |
| `cli/` | JLine3 REPL + GraalVM native image |
| `shared/` | Core: providers, RAG, MCP, memory, tools, database, plans engine |

See [CONTRIBUTING.md](./CONTRIBUTING.md) for development guidelines and DCO requirements, or the [Development Getting Started Guide](https://askimo.chat/docs/development/getting-started/).

---

## Localization

English · 中文 (简体/繁體) · 日本語 · 한국어 · Français · Español · Deutsch · Português · Tiếng Việt

Want to add a language? [Open a discussion](https://github.com/haiphucnguyen/askimo/discussions).

---

## Getting Help

- [Documentation](https://askimo.chat/docs/)
- [GitHub Discussions](https://github.com/haiphucnguyen/askimo/discussions)
- [Issue Tracker](https://github.com/haiphucnguyen/askimo/issues)

---

## Contributing

Bug reports, feature requests, and pull requests are welcome. See [CONTRIBUTING.md](./CONTRIBUTING.md) for details.

---

## License

AGPLv3. See [LICENSE](./LICENSE).

---

## Star History

<a href="https://www.star-history.com/?repos=haiphucnguyen%2Faskimo&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=haiphucnguyen/askimo&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=haiphucnguyen/askimo&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=haiphucnguyen/askimo&type=date&legend=top-left" />
 </picture>
</a>

