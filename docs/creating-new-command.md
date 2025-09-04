---
title: Creating a New Command in Askimo
nav_order: 5
description: Learn how to extend Askimo by building your own custom CLI command.
---

# Creating a New Command in Askimo

This guide explains how to implement a new command in the Askimo CLI. By following these steps, you can add custom functionality to the command-line interface.

## Architecture Overview

Askimo uses a simple command handling architecture with the following key components:

1. **CommandHandler**: Interface that defines the contract for all command handlers
2. **Command implementations**: Classes that implement the CommandHandler interface
3. **Command registration**: Process of adding commands to the CLI application

Each command is identified by a keyword (starting with a colon) and has a description and handling logic.

## Implementation Steps

### 1. Create a New Command Handler

First, create a new class that implements the `CommandHandler` interface. This class will handle your specific command:

```kotlin
// File: io.askimo.cli.commands.YourCommandHandler.kt

/**
 * Handles the command to [describe what your command does].
 * 
 * This class provides functionality to [explain the purpose and functionality of your command].
 */
class YourCommandHandler(
    private val session: Session, // Include any dependencies your command needs
) : CommandHandler {
    override val keyword: String = ":yourcommand" // Command keyword (starts with colon)
    override val description: String = "Description of what your command does."

    override fun handle(line: ParsedLine) {
        // Implement your command logic here
        
        // Example: Access command arguments
        val args = line.words().drop(1) // Skip the command itself
        
        // Example: Access the current session
        val provider = session.getActiveProvider()
        val modelName = session.params.getModel(provider)
        
        // Example: Print output to the console
        println("✅ Your command executed successfully!")
    }
}
```

### 2. Register Your Command

To make your command available in the CLI, add it to the list of command handlers in the `main` function in `ChatCli.kt`:

```kotlin
// In ChatCli.kt

val commandHandlers: List<CommandHandler> =
    listOf(
        HelpCommandHandler(),
        ConfigCommand(session),
        ParamsCommandHandler(session),
        SetParamCommandHandler(session),
        ListProvidersCommandHandler(),
        SetProviderCommandHandler(session),
        ModelsCommandHandler(session),
        CopyCommandHandler(session),
        ClearMemoryCommandHandler(session),
        YourCommandHandler(session), // Add your command here
    )
```

Don't forget to add the import for your new command handler at the top of the file:

```
// Add this to the imports section at the top of ChatCli.kt
import io.askimo.cli.commands.YourCommandHandler
```

### 3. Command Implementation Example

Let's look at a real example: the `ClearMemoryCommandHandler` which clears the chat memory for the current provider and model:

```kotlin
// File: io.askimo.cli.commands.ClearMemoryCommandHandler.kt

/**
 * Handles the command to clear the chat memory.
 * 
 * This class provides functionality to reset the conversation history for the current
 * provider and model combination. It allows users to start fresh conversations without
 * changing their model configuration.
 */
class ClearMemoryCommandHandler(
    private val session: Session,
) : CommandHandler {
    override val keyword: String = ":clear"
    override val description: String = "Clear the current chat memory for the active provider/model."

    override fun handle(line: ParsedLine) {
        val provider = session.getActiveProvider()
        val modelName = session.params.getModel(provider)

        session.removeMemory(provider, modelName)

        println("🧹 Chat memory cleared for $provider / $modelName")
    }
}
```

This command:
1. Gets the current provider and model from the session
2. Calls the session's `removeMemory` method to clear the chat history
3. Prints a confirmation message to the user

### 4. Working with Command Arguments

If your command needs to handle arguments, you can access them from the `ParsedLine` object:

```kotlin
override fun handle(line: ParsedLine) {
    val args = line.words().drop(1) // Skip the command itself
    
    if (args.isEmpty()) {
        println("❌ This command requires arguments.")
        println("Usage: ${keyword} <argument1> <argument2>")
        return
    }
    
    val arg1 = args[0]
    val arg2 = args.getOrNull(1) // Safely get optional arguments
    
    // Process arguments...
}
```

### 5. Accessing Session Data

The `Session` object provides access to various aspects of the application state:

```kotlin
// Get the active provider
val provider = session.getActiveProvider()

// Get the current model for a provider
val modelName = session.params.getModel(provider)

// Get a parameter value
val paramValue = session.params.get(ParamKey.SYSTEM_PROMPT)

// Set a parameter value
session.params.set(ParamKey.SYSTEM_PROMPT, "New value")

// Get the current chat model
val model = session.getChatModel()

// Store the last response
session.lastResponse = "Response text"
```

## Best Practices

### Command Naming Conventions

- Command keywords should start with a colon (`:`)
- Use lowercase for command names
- Use simple, descriptive names that clearly indicate the command's purpose

### Command Implementation

- Keep commands focused on a single responsibility
- Provide clear feedback to the user about what happened
- Handle errors gracefully with helpful error messages
- Use emojis for visual feedback (e.g., ✅, ❌, 🧹)
- Include detailed documentation in the class comment

### Command Description

- Keep descriptions concise but informative
- Start with a verb (e.g., "Clear", "Set", "Show")
- Make sure the description clearly explains what the command does

## Testing Your Command

After implementing your command, you can test it by:

1. Building and running the Askimo CLI
2. Using your command with the appropriate syntax:
   ```
   askimo> :yourcommand [arguments]
   ```
3. Verifying that the command produces the expected output and behavior

## Conclusion

By following these steps, you can extend the Askimo CLI with custom commands. The command handler architecture makes it easy to add new functionality while maintaining a consistent interface for users.

Remember to handle errors gracefully and provide clear feedback to users when something goes wrong with your command.