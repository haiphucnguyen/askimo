---
title: 📜 Askimo Command Reference
nav_order: 4
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

  :clear   - Clear the current chat memory for the active provider/model.
  :config        - Show the current provider, model, and settings.
  :copy          - Copy the last AI response to the clipboard
  :help          - Show available commands
  :models        - List available models for the current provider
  :params        - Show current model parameters or list available param keys
  :providers     - List all supported model providers
  :setparam      - Set a model parameter (use :params --list for available keys)
  :setprovider   - Set the current model provider (e.g., :setprovider openai)
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
- All configurable parameters available for `:setparam`

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
- The list under "Available parameters" shows what can be changed with `:setparam`.
- Parameter names and defaults may vary depending on the active provider.

## :setparam

**Description:**  
Set a parameter for the current AI provider.  
Available parameters depend on the provider and can be viewed using `:params`.

**Syntax:**

:setparam \<name\> \<value\>

**Parameters:**
- `<name>` - Name of the parameter (e.g., `model`, `syle`).
- `<value>` - New value to assign to the parameter.

**Example:**

```bash
:setparam model gpt-4o
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
- Use `:setprovider <name>` to switch to one of the available providers.

## :setprovider

**Description:**  
Switch to a different AI provider for the current session.  

**Syntax:**

:setprovider \<provider_name\>

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
- Use `:setparam model <model_name>` to change the active model for the session.