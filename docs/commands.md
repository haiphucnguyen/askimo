---
title: Askimo Command Reference
nav_order: 4
description: Complete reference for all Askimo CLI commands with usage examples and options.
---

# 📜 Askimo Command Reference

This document lists all available Askimo commands, their purpose, parameters, and usage examples.

## Command Modes

Askimo supports two modes of operation:

### 🖥️ Interactive Mode (REPL)
Start the interactive shell by running `askimo` without arguments. Commands use the `:keyword` format:
```bash
askimo
askimo> :help
askimo> :models
```

### ⚡ Non-Interactive Mode (CLI)
Run commands directly from your terminal using the `--flag` format:
```bash
askimo --help
askimo --models
askimo --tools
```

#### 🔗 Composite Commands
You can combine multiple non-interactive commands in a single invocation. Commands are executed in the order they appear:

```bash
# Configure provider and multiple parameters at once
askimo --set-provider openai --set-param api_key sk-abc123 --set-param model gpt-4

# Set provider and check available models
askimo --set-provider ollama --models

# Configure multiple parameters
askimo --set-param temperature 0.7 --set-param max_tokens 2000

# Combine informational commands
askimo --list-providers --list-tools --version
```

This is especially useful for scripting and automation, allowing you to configure the entire environment with a single command.

## Available Commands

### Commands Available in BOTH Modes

The following commands work in both interactive (`:keyword`) and non-interactive (`--flag`) modes:

- `:help` / `--help` - Show available commands
- `:config` / `--config` - Show current provider, model, and settings
- `:providers` / `--providers` - List all supported model providers
- `:set-provider` / `--set-provider` - Set the current model provider
- `:models` / `--models` - List available models for the current provider
- `:params` / `--params` - Show current model parameters or list available param keys
- `:set-param` / `--set-param` - Set a model parameter
- `:tools` / `--tools` - List all available tools
- `:version` / `--version` - Show detailed version and build information

### Non-Interactive Mode Only Commands

The following commands are only available in non-interactive mode (CLI flags):

- `--create-recipe` - Create a provider-agnostic recipe from a YAML template
- `--recipes` - List all registered recipes in ~/.askimo/recipes
- `--delete-recipe` - Delete a registered recipe

### Interactive Mode Only Commands

The following commands are only available in interactive mode:

- `:clear` - Clear the current chat memory
- `:copy` - Copy the last AI response to the clipboard
- `:create-project` - Create a project, auto-start Postgres+pgvector, and index the folder
- `:projects` - List all saved Askimo projects
- `:project` - Activate a saved project (sets scope and enables RAG)
- `:delete-project` - Delete a saved project and drop its pgvector embedding table
- `:sessions` - List all saved sessions
- `:new-session` - Start a new chat session
- `:resume-session` - Resume a previous chat session
- `:agent` - Enable agent mode for autonomous task execution

---

# Command Reference

## :help / --help

**Description:**  
Display all available commands and their short descriptions.

**Syntax:**

```bash
# Interactive mode
:help

# Non-interactive mode
askimo --help
```

**Example (Interactive):**

```bash
askimo> :help
Available commands:
  :help           - Show available commands
  :config         - Show the current provider, model, and settings
  :models         - List available models for the current provider
  ...
```

**Example (Non-interactive):**

```bash
$ askimo --help
Available commands (non-interactive mode):
  --help             - Show available commands
  --config           - Show the current provider, model, and settings
  --models           - List available models for the current provider
  ...
```

## :clear

**Description:**  
Clear the chat history for the current session.  
This does not reset provider, model, or session parameters - only the conversation context is removed.

**Availability:** Interactive mode only

**Syntax:**

```bash
:clear
```

## :copy

**Description:**  
Copy the last AI response to your system clipboard.  
This is useful for quickly pasting the output into another application without manually selecting it.

**Availability:** Interactive mode only

**Syntax:**

```bash
:copy
```

## :params / --params

**Description:**  
Display the current session parameters, including:
- Active provider
- Selected model
- All configurable parameters available for `:set-param` / `--set-param`

**Syntax:**

```bash
# Interactive mode
:params
:params --list

# Non-interactive mode
askimo --params
askimo --params --list
```

**Example:**
```bash
askimo> :params --list
Available parameter keys for gpt-4o (OPENAI):
  model (String) – Model name to use (e.g., gpt-4, llama3)
  style (Enum(precise|balanced|creative)) – Output style (determinism vs. creativity)
  verbosity (Enum(short|normal|long)) – Controls response length/cost
  api_key (String) – OpenAI API key
```

**Notes:**
- The list under "Available parameters" shows what can be changed with `:set-param` or `--set-param`
- Parameter names and defaults may vary depending on the active provider

## :set-param / --set-param

**Description:**  
Set a parameter for the current AI provider.  
Available parameters depend on the provider and can be viewed using `:params` or `--params`.

**Syntax:**

```bash
# Interactive mode
:set-param <name> <value>

# Non-interactive mode
askimo --set-param <name> <value>
```

**Parameters:**
- `<name>` - Name of the parameter (e.g., `model`, `style`)
- `<value>` - New value to assign to the parameter

**Example:**

```bash
# Interactive mode
askimo> :set-param model gpt-4o

# Non-interactive mode
$ askimo --set-param model gpt-4o
```

## :config / --config

**Description:**  
Display the current Askimo configuration values.

**Syntax:**

```bash
# Interactive mode
:config

# Non-interactive mode
askimo --config
```

**Example:**

```bash
askimo> :config
🔧 Current configuration:
  Provider:    OPENAI
  Model:       gpt-4o
  Settings:
    apiKey:      sk-pr***
    presets: Presets(style=BALANCED, verbosity=NORMAL)
```

## :providers / --providers

**Description:**  
List all AI providers supported by Askimo.

**Syntax:**

```bash
# Interactive mode
:providers

# Non-interactive mode
askimo --providers
```

**Notes:**
- The list may vary depending on your installation and configuration
- Use `:set-provider <name>` or `askimo --set-provider <name>` to switch to one of the available providers

## :set-provider / --set-provider

**Description:**  
Switch to a different AI provider for the current session.

**Syntax:**

```bash
# Interactive mode
:set-provider <provider_name>

# Non-interactive mode
askimo --set-provider <provider_name>
```

**Notes:**
- Switching providers may change the available parameters and models
- After switching, you can use `:models` or `--models` to see available models for the new provider

## :models / --models

**Description:**  
List all models available for the current provider.  
The list is retrieved dynamically based on the active provider's settings.

**Syntax:**

```bash
# Interactive mode
:models

# Non-interactive mode
askimo --models
```

**Notes:**
- The available models depend on the provider and your local/remote configuration
- Use `:set-param model <model_name>` or `--set-param model <model_name>` to change the active model for the session

## :create-project

**Description:**
Create a new Askimo project, automatically start a Postgres database with pgvector extension using Testcontainers, and index the specified folder for Retrieval-Augmented Generation (RAG).

**Availability:** Interactive mode only

**Syntax:**

```bash
:create-project -n <project-name> -d <project-folder>
```

**Parameters:**
- `-n, --name` - Name of the project to create
- `-d, --dir, --folder` - Path to the project folder to index

**Example:**

```bash
askimo> :create-project -n myapp -d /Users/john/projects/myapp
```

**Notes:**
- The project name must be unique
- The folder path must exist and be a directory
- A Postgres+pgvector container will be started automatically if not already running
- All project files will be indexed for RAG-enabled chat

## :projects

**Description:**
List all saved Askimo projects registered in your configuration.

**Availability:** Interactive mode only

**Syntax:**

```bash
:projects
```

**Example:**

```bash
askimo> :projects
📚 Projects:
  1. myapp  →  /Users/john/projects/myapp
  2. webapp  →  /Users/john/projects/webapp
```

## :project

**Description:**
Activate a previously saved Askimo project by name. This sets the project scope and enables RAG (Retrieval-Augmented Generation) for the session.

**Availability:** Interactive mode only

**Syntax:**

```bash
:project <project-name>
```

**Parameters:**
- `<project-name>` - Name of the saved project to activate

**Example:**

```bash
askimo> :project myapp
```

**Notes:**
- The project must have been previously created with `:create-project`
- Postgres+pgvector will be started automatically if not already running
- RAG will be enabled for the active project

## :delete-project

**Description:**
Delete a saved project from the Askimo registry (~/.askimo/projects.json) and drop its pgvector embedding table from the database, or delete all projects at once.

**Availability:** Interactive mode only

**Syntax:**

```bash
:delete-project <project-name>
:delete-project --all
```

**Parameters:**
- `<project-name>` - Name of the project to delete
- `--all` - Delete all saved projects and their embeddings

**Examples:**

```bash
# Delete a specific project
askimo> :delete-project myapp

# Delete all projects
askimo> :delete-project --all
```

**Notes:**
- This action removes the project(s) from the registry and deletes all indexed embeddings
- The original project folders remain untouched
- When using `--all`, you'll see a list of all projects before confirmation
- This operation cannot be undone

## --create-recipe

**Description:**
Create a provider-agnostic recipe from a YAML template file. Recipes are reusable prompt templates that can be used across different AI providers.

**Availability:** Non-interactive mode only

**Syntax:**

```bash
askimo --create-recipe [name] -f <file.yml>
askimo --create-recipe [name] -i
```

**Parameters:**
- `[name]` - Optional name for the recipe (can also be specified in the YAML file)
- `-f, --file` - Path to the YAML template file
- `-i, --interactive` - Create recipe interactively with guided prompts

**Example:**

```bash
$ askimo --create-recipe myrecipe -f ~/templates/code-review.yml
```

**Notes:**
- If the name is not provided as an argument, it must be specified in the YAML file's `name:` field
- The recipe will be saved to ~/.askimo/recipes/
- Use `askimo -r <recipe-name>` to run the recipe from the command line

## --recipes

**Description:**
List all registered recipes stored in ~/.askimo/recipes.

**Availability:** Non-interactive mode only

**Syntax:**

```bash
askimo --recipes
```

**Example:**

```bash
$ askimo --recipes
📦 Registered recipes (3)
────────────────────────────
code-review - Review code for bugs and improvements
documentation - Generate documentation for code
refactoring - Suggest refactoring improvements
```

## --delete-recipe

**Description:**
Delete a registered recipe from ~/.askimo/recipes, or delete all recipes at once.

**Availability:** Non-interactive mode only

**Syntax:**

```bash
askimo --delete-recipe <name>
askimo --delete-recipe --all
```

**Parameters:**
- `<name>` - Name of the recipe to delete
- `--all` - Delete all registered recipes

**Examples:**

```bash
# Delete a specific recipe
$ askimo --delete-recipe myrecipe

# Delete all recipes
$ askimo --delete-recipe --all
```

**Notes:**
- You will be prompted to confirm the deletion
- When using `--all`, you'll see a list of all recipes before confirmation
- This operation cannot be undone

## :tools / --tools

**Description:**
List all available tools that can be used in recipes and by the AI agent. This includes tools from GitTools and LocalFsTools.

**Syntax:**

```bash
# Interactive mode
:tools

# Non-interactive mode
askimo --tools
```

**Example:**

```bash
$ askimo --tools
🔧 Available Tools
──────────────────────────────

📦 GitTools
  • branch
    Current branch name
  • commit
    Write .git/COMMIT_EDITMSG and run git commit -F -
  • stagedDiff
    Unified diff of staged changes (git diff --cached)
  • status
    Concise git status (-sb)

📦 LocalFsTools
  • readFile
    Read text file from path
  • writeFile
    Write text file to path
  • runCommand
    Run shell commands in a persistent terminal...
  • searchFileContent
    Search for text content within files in a directory...
  • searchFilesByGlob
    Search for files by name/pattern with smart matching...
  (and more...)
  
──────────────────────────────
Total: 14 tools
```

**Notes:**
- Tools are organized by their provider class (GitTools, LocalFsTools)
- Each tool shows its name and description
- These tools can be invoked by recipes or used by AI agents during code generation

## :sessions

**Description:**
List all saved chat sessions for the current provider and model.

**Availability:** Interactive mode only

**Syntax:**

```bash
:sessions
```

**Example:**

```bash
askimo> :sessions
💬 Saved Sessions:
  1. [2025-11-06 14:30] Session with 15 messages
  2. [2025-11-05 09:15] Session with 8 messages
  3. [2025-11-04 16:45] Session with 23 messages
```

**Notes:**
- Sessions are saved automatically during interactive chat
- Each session is tied to a specific provider and model combination
- Use `:resume-session` to continue a previous conversation

## :new-session

**Description:**
Start a new chat session, clearing the current conversation history and creating a fresh context.

**Availability:** Interactive mode only

**Syntax:**

```bash
:new-session
```

**Example:**

```bash
askimo> :new-session
✨ Started new session
```

**Notes:**
- This command clears the current conversation context
- The previous session is saved and can be resumed later with `:resume-session`
- Provider, model, and configuration settings are preserved

## :resume-session

**Description:**
Resume a previous chat session by selecting from your saved sessions.

**Availability:** Interactive mode only

**Syntax:**

```bash
:resume-session
:resume-session <session-id>
```

**Parameters:**
- `<session-id>` - Optional session ID to resume directly

**Example:**

```bash
# Interactive selection
askimo> :resume-session
💬 Saved Sessions:
  1. [2025-11-06 14:30] Session with 15 messages
  2. [2025-11-05 09:15] Session with 8 messages
Select session to resume [1-2]:

# Direct resume
askimo> :resume-session 1
```

**Notes:**
- If no session ID is provided, you'll be prompted to select from available sessions
- Resuming a session loads all previous messages and context
- Sessions are specific to the provider and model they were created with

## :agent

**Description:**
Enable agent mode for autonomous task execution. In agent mode, the AI can use available tools to complete complex tasks independently.

**Availability:** Interactive mode only

**Syntax:**

```bash
:agent <task-description>
```

**Parameters:**
- `<task-description>` - Description of the task you want the agent to accomplish

**Example:**

```bash
askimo> :agent Analyze the git repository, find all TODO comments, and create a summary report
🤖 Agent mode activated
📋 Task: Analyze the git repository, find all TODO comments, and create a summary report
🔧 Using tools: searchFileContent, readFile, writeFile
...
✅ Task completed. Report saved to TODO-summary.md
```

**Notes:**
- Agent mode allows the AI to autonomously use tools like `searchFileContent`, `runCommand`, `readFile`, etc.
- The agent will break down complex tasks into steps and execute them
- You can see which tools the agent is using in real-time
- Use `:tools` to see all available tools the agent can use

## :version / --version

**Description:**
Show detailed version and build information for Askimo.

**Syntax:**

```bash
# Interactive mode
:version

# Non-interactive mode
askimo --version
```

**Example:**

```bash
$ askimo --version
Askimo v0.2.0
Build: 2025-11-06
Commit: a1b2c3d
Platform: macOS (arm64)
```
