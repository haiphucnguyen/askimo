<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="public/askimo-logo-dark.svg">
    <img alt="Askimo - AI at your command line." src="public/askimo-logo.svg">
  </picture>
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

Askimo is a command-line assistant that talks with LLMs - from online providers like OpenAI, X AI, Gemini to local models like Ollama.
> Askimo - AI for your workflows, with the freedom to choose any provider.

## Why Askimo?

* Switch providers anytime – Talk to OpenAI, Gemini, X AI, or Ollama with the same commands.

* Automation-first – Pipe files, logs, or command output into Askimo and let AI handle the rest.

* Your choice of interface – Use the CLI if you love the terminal, or the web UI if you prefer a browser.

* No lock-in – Designed to stay provider-neutral so you can change models as the AI landscape evolves.

## Demo

* Piping commands & switching providers in Askimo

![Demo](public/demo1.gif)

* Interacting with the local file system in Askimo

![Demo](public/demo2.gif)

💬 Simple Web Chat (Local Usage)

Askimo isn’t only for the terminal - you can also start a lightweight local web chat UI if you prefer a browser interface.
This feature is designed for quick testing or personal use, not for production deployment.

* **Start Askimo web server**
```
askimo --web
```


Then open your browser to the URL printed in the console (look for Web server running at http://127.0.0.1:8080). If port 8080 is busy, Askimo will pick the next free port-use the exact address shown in that log line.
The web UI supports real-time streaming responses and Markdown rendering.

![Askimo-web](public/askimo-web.png)

> ⚠️ Important: Before running askimo web, you must finish setting up your AI provider (e.g., Ollama, OpenAI) and select a model using the Askimo CLI.
> The web version is currently a simple chat page - it does not support configuring providers, models, or AI parameters. All configuration must be done beforehand via the CLI.

## Quickstart

### macOS / Linux (Homebrew)

```bash
brew tap haiphucnguyen/askimo
brew install askimo
askimo
```

### Windows (Scoop)
```
scoop bucket add askimo https://github.com/haiphucnguyen/scoop-askimo
scoop install askimo
askimo
```
Other ways to install → [Installation Guide](docs/installation.md)

👉 Once installed, you can connect Askimo to providers like Ollama, OpenAI, Gemini, or X AI and start chatting.

📖 See [Getting started](docs/getting-started.md) for tutorials on setting up Ollama, adding API keys (OpenAI, Gemini, X AI), switching providers, and running real workflow examples.

## Available Commands

| Command           | Description                                                                                                 | Example Usage                                      |
|-------------------|-------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| `:help`           | Show all available commands                                                                                 | `:help`                                           |
| `:set-param`      | Set a parameter for the current provider                                                                    | `:set-param style creative`                       |
| `:params`         | View current session parameters                                                                             | `:params`                                         |
| `:config`         | Show the current provider, model, and settings                                                              | `:config`                                         |
| `:providers`      | List all supported AI providers                                                                             | `:providers`                                      |
| `:set-provider`   | Switch to a different AI provider                                                                           | `:set-provider ollama`                            |
| `:models`         | List available models for the current provider                                                              | `:models`                                         |
| `:copy`           | Copy the last response to the clipboard                                                                     | `:copy`                                           |
| `:clear`          | Clear the chat history for the current session                                                              | `:clear`                                          |
| `:create-project` | Create a project, auto-start Postgres+pgvector (Testcontainers), and index the folder                      | `:create-project -n myapp -d /path/to/folder`     |
| `:projects`       | List all saved Askimo projects                                                                              | `:projects`                                       |
| `:use-project`    | Activate a saved project (sets scope and enables RAG)                                                       | `:project myapp`                                  |
| `:delete-project` | Delete a saved project: removes it from ~/.askimo/projects.json and drops its pgvector embedding table      | `:delete-project myapp`                           |
| `:create-recipe`  | Create a provider-agnostic recipe from a YAML template                                                      | `:create-recipe myrecipe -template recipe.yml`    |
| `:recipes`        | List all registered recipes in ~/.askimo/recipes                                                            | `:recipes`                                        |
| `:delete-recipe`  | Delete a registered recipe from ~/.askimo/recipes                                                           | `:delete-recipe myrecipe`                         |
| `:exit`           | Exit the Askimo REPL                                                                                        | `:exit`                                           |


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

