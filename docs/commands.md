---
title: Askimo Command Reference
nav_order: 4
description: Complete reference for all Askimo CLI commands with usage examples and options.
---

# 📜 Askimo Command Reference

This document lists all available Askimo commands, their purpose, parameters, and usage examples.  
Commands are entered directly into the Askimo REPL and start with a colon (`:`).

---

**Description:**  
Display all available commands and their short descriptions.

**Syntax:**

:help

**Example:**

```bash
> :help
Available commands:

  :clear          - Clear the current chat memory for the active provider/model.
  :config         - Show the current provider, model, and settings.
  :copy           - Copy the last AI response to the clipboard
  :help           - Show available commands
  :models         - List available models for the current provider
  :params         - Show current model parameters or list available param keys
  :providers      - List all supported model providers
  :set-param      - Set a model parameter (use :params --list for available keys)
  :set-provider   - Set the current model provider (e.g., :set-provider openai)
  :create-project - Create a project, auto-start Postgres+pgvector, and index the folder
  :projects       - List all saved Askimo projects
  :project        - Activate a saved project (sets scope and enables RAG)
  :delete-project - Delete a saved project and drop its pgvector embedding table
  :create-recipe  - Create a provider-agnostic recipe from a YAML template
  :recipes        - List all registered recipes in ~/.askimo/recipes
  :delete-recipe  - Delete a registered recipe from ~/.askimo/recipes
```

## :clear

**Description:**  
Clear the chat history for the current session.  
This does not reset provider, model, or session parameters - only the conversation context is removed.

**Syntax:**
:clear

## :copy

**Description:**  
Copy the last AI response to your system clipboard.  
This is useful for quickly pasting the output into another application without manually selecting it.

**Syntax:**

:copy

## :params

**Description:**  
Display the current session parameters, including:
- Active provider
- Selected model
- All configurable parameters available for `:set-param`

**Syntax:**

:params

**Example:**
```bash
> :params --list
Available parameter keys for gpt-4o (OPEN_AI):
  model (String) – Model name to use (e.g., gpt-4, llama3)
  style (Enum(precise|balanced|creative)) – Output style (determinism vs. creativity)
  verbosity (Enum(short|normal|long)) – Controls response length/cost
  api_key (String) – OpenAI API key
```

**Notes:**
- The list under "Available parameters" shows what can be changed with `:set-param`.
- Parameter names and defaults may vary depending on the active provider.

## :set-param

**Description:**  
Set a parameter for the current AI provider.  
Available parameters depend on the provider and can be viewed using `:params`.

**Syntax:**

:set-param \<name\> \<value\>

**Parameters:**
- `<name>` - Name of the parameter (e.g., `model`, `syle`).
- `<value>` - New value to assign to the parameter.

**Example:**

```bash
:set-param model gpt-4o
```

## :config

**Description:**  
Display the current Askimo configuration values.  

**Syntax:**

:config

**Example:**

```bash
> :config
🔧 Current configuration:
  Provider:    OPEN_AI
  Model:       gpt-4o
  Settings:
    apiKey:      sk-pr***
    presets: Presets(style=BALANCED, verbosity=NORMAL)
> 
```

## :providers

**Description:**  
List all AI providers supported by Askimo.  

**Notes:**
- The list may vary depending on your installation and configuration.
- Use `:set-provider <name>` to switch to one of the available providers.

## :set-provider

**Description:**  
Switch to a different AI provider for the current session.  

**Syntax:**

:set-provider \<provider_name\>

**Notes:**
- Switching providers may change the available parameters and models.
- After switching, you can use `:models` to see available models for the new provider.

## :models

**Description:**  
List all models available for the current provider.  
The list is retrieved dynamically based on the active provider’s settings.

**Syntax:**

:models

**Notes:**
- The available models depend on the provider and your local/remote configuration.
- Use `:set-param model <model_name>` to change the active model for the session.

## :create-project

**Description:**
Create a new Askimo project, automatically start a Postgres database with pgvector extension using Testcontainers, and index the specified folder for Retrieval-Augmented Generation (RAG).

**Syntax:**

:create-project -n \<project-name\> -d \<project-folder\>

**Parameters:**
- `-n, --name` - Name of the project to create
- `-d, --dir, --folder` - Path to the project folder to index

**Example:**

```bash
:create-project -n myapp -d /Users/john/projects/myapp
```

**Notes:**
- The project name must be unique
- The folder path must exist and be a directory
- A Postgres+pgvector container will be started automatically if not already running
- All project files will be indexed for RAG-enabled chat

## :projects

**Description:**
List all saved Askimo projects registered in your configuration.

**Syntax:**

:projects

**Example:**

```bash
> :projects
📚 Projects:
  1. myapp  →  /Users/john/projects/myapp
  2. webapp  →  /Users/john/projects/webapp
```

## :project

**Description:**
Activate a previously saved Askimo project by name. This sets the project scope and enables RAG (Retrieval-Augmented Generation) for the session.

**Syntax:**

:project \<project-name\>

**Parameters:**
- `<project-name>` - Name of the saved project to activate

**Example:**

```bash
:project myapp
```

**Notes:**
- The project must have been previously created with `:create-project`
- Postgres+pgvector will be started automatically if not already running
- RAG will be enabled for the active project

## :delete-project

**Description:**
Delete a saved project from the Askimo registry (~/.askimo/projects.json) and drop its pgvector embedding table from the database.

**Syntax:**

:delete-project \<project-name\>

**Parameters:**
- `<project-name>` - Name of the project to delete

**Example:**

```bash
:delete-project myapp
```

**Notes:**
- This action removes the project from the registry and deletes all indexed embeddings
- The original project folder remains untouched
- This operation cannot be undone

## :create-recipe

**Description:**
Create a provider-agnostic recipe from a YAML template file. Recipes are reusable prompt templates that can be used across different AI providers.

**Syntax:**

:create-recipe [name] -template \<file.yml\>

**Parameters:**
- `[name]` - Optional name for the recipe (can also be specified in the YAML file)
- `-f, --file` - Path to the YAML template file

**Example:**

```bash
:create-recipe myrecipe -f ~/templates/code-review.yml
```

**Notes:**
- If the name is not provided as an argument, it must be specified in the YAML file's `name:` field
- The recipe will be saved to ~/.askimo/recipes/
- Use `askimo -r <recipe-name>` to run the recipe from the command line

## :recipes

**Description:**
List all registered recipes stored in ~/.askimo/recipes.

**Syntax:**

:recipes

**Example:**

```bash
> :recipes
📦 Registered recipes (3)
────────────────────────────
code-review
documentation
refactoring
```

## :delete-recipe

**Description:**
Delete a registered recipe from ~/.askimo/recipes.

**Syntax:**

:delete-recipe \<name\>

**Parameters:**
- `<name>` - Name of the recipe to delete

**Example:**

```bash
:delete-recipe myrecipe
```

**Notes:**
- You will be prompted to confirm the deletion
- This operation cannot be undone