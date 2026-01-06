---
title: Creating a New Chat Model in Askimo
parent: Development & Customization
nav_order: 2
description: Step-by-step guide to adding and configuring a new chat model in Askimo CLI.
---
# Creating a New Chat Model in Askimo

This guide explains how to implement a new chat model provider in Askimo. By following these steps, you can integrate any chat model API with the Askimo CLI.

## Architecture Overview

Askimo uses a modular architecture for chat models with the following key components:

1. **ChatClient**: Interface that defines the contract for all chat models (created by LangChain4j's AiServices)
2. **ChatClientImpl**: Wrapper that adds session management and memory persistence to ChatClient
3. **ChatModelFactory**: Generic interface for creating chat model instances with type parameter `<T : ProviderSettings>`
4. **ProviderSettings**: Interface for model-specific configuration with methods for validation, field management, and deep copying
5. **ModelProvider**: Enum that identifies different model providers (OpenAI, XAI, Gemini, Ollama, Anthropic, LocalAI, LMStudio)
6. **ProviderRegistry**: Central registry that manages all model factories using a map-based structure
7. **TokenAwareSummarizingMemory**: Advanced memory implementation that automatically summarizes conversation history when approaching token limits
8. **SessionMemoryRepository**: Persistence layer for storing and retrieving conversation memory across sessions

### Memory Architecture

Each factory creates two key components:
- **ChatClient** (delegate): LangChain4j-generated proxy that handles AI communication
- **ChatClientImpl** (wrapper): Adds session awareness, auto-save, and memory persistence

Each model provider has its own implementation of these interfaces, along with optional marker interfaces like `HasApiKey` or `HasBaseUrl` for common configuration patterns.

## Implementation Steps

### 1. Add LangChain4j Dependency

First, add the appropriate LangChain4j dependency for your provider to the `build.gradle.kts` file:

```kotlin
dependencies {
    // Existing dependencies
    implementation("dev.langchain4j:langchain4j:1.2.0")
    implementation("dev.langchain4j:langchain4j-open-ai:1.2.0")
    implementation("dev.langchain4j:langchain4j-ollama:1.2.0")
    
    // Add your provider's LangChain4j implementation
    implementation("dev.langchain4j:langchain4j-your-provider:1.2.0")
}
```

You need to find the appropriate LangChain4j implementation for your provider. Check the [LangChain4j GitHub repository](https://github.com/langchain4j/langchain4j) or Maven Central for available implementations. If there isn't an existing implementation for your provider, you may need to create your own or adapt one of the existing implementations.

### 2. Create a New Provider Enum Value

First, add your provider to the `ModelProvider` enum in `io.askimo.core.providers.ModelProvider`:

```kotlin
@Serializable
enum class ModelProvider {
    @SerialName("OPENAI") OPENAI,
    @SerialName("XAI") XAI,
    @SerialName("GEMINI") GEMINI,
    @SerialName("OLLAMA") OLLAMA,
    @SerialName("ANTHROPIC") ANTHROPIC,
    @SerialName("LOCALAI") LOCALAI,
    @SerialName("LMSTUDIO") LMSTUDIO,
    @SerialName("YOUR_PROVIDER") YOUR_PROVIDER,  // Add your provider here
    @SerialName("UNKNOWN") UNKNOWN,
}
```

### 3. Create Provider Settings

Create a settings class that implements `ProviderSettings`. Use marker interfaces like `HasApiKey` or `HasBaseUrl` for common configuration:

```kotlin
// File: io.askimo.core.providers.yourprovider.YourProviderSettings.kt

@Serializable
data class YourProviderSettings(
    override var apiKey: String = "",                   // Use HasApiKey interface
    override val defaultModel: String = "model-name",   // Your provider's default model
    override var presets: Presets = Presets(Style.BALANCED),
) : ProviderSettings, HasApiKey {
    
    override fun describe(): List<String> {
        // Return human-readable description of settings (mask sensitive data)
    }

    override fun getFields(): List<SettingField> {
        // Return configurable fields for UI
        // Use createCommonPresetFields(presets) for standard preset fields
    }

    override fun updateField(fieldName: String, value: String): ProviderSettings {
        // Update a field and return new settings instance
        // Use updatePresetField() helper for preset fields
    }

    override fun validate(): Boolean {
        // Validate settings are properly configured
    }

    override fun getSetupHelpText(): String {
        // Return helpful guidance for setup
    }

    override fun getConfigFields(): List<ProviderConfigField> {
        // Return configuration fields for provider setup wizard
        // Check for existing stored keys (keychain/encrypted)
    }

    override fun applyConfigFields(fields: Map<String, String>): ProviderSettings {
        // Apply configuration field values
    }

    override fun deepCopy(): ProviderSettings = copy()
}
```

**For complete implementation examples, refer to:**
- `OpenAiSettings.kt` - Example with API key and secure storage handling
- `OllamaSettings.kt` - Example with base URL configuration

### 4. Implement the Model Factory

Create a factory class that implements `ChatModelFactory<T>` with your settings type:

```kotlin
// File: io.askimo.core.providers.yourprovider.YourProviderModelFactory.kt

import io.askimo.core.memory.TokenAwareSummarizingMemory
import io.askimo.core.providers.ChatClientImpl
import io.askimo.core.db.DatabaseManager

class YourProviderModelFactory : ChatModelFactory<YourProviderSettings> {
    
    override fun availableModels(settings: YourProviderSettings): List<String> {
        // Fetch available models from your provider (API call or hardcoded list)
        // Return empty list on error
    }
    
    override fun defaultSettings(): YourProviderSettings {
        // Return default settings instance
    }
    
    override fun getNoModelsHelpText(): String {
        // Return helpful guidance when no models are available
    }
    
    override fun create(
        model: String,
        settings: YourProviderSettings,
        retrievalAugmentor: RetrievalAugmentor?,
        executionMode: ExecutionMode,
    ): ChatClient {
        // 1. Build your provider's streaming chat model using LangChain4j
        val chatModel = YourProviderStreamingChatModel
            .builder()
            .apiKey(settings.apiKey)
            .modelName(model)
            .apply {
                // Apply sampling parameters (temperature, topP) from settings.presets.style
                val sampling = samplingFor(settings.presets.style)
                temperature(sampling.temperature)
                topP(sampling.topP)
            }
            .build()
        
        // 2. Create token-aware summarizing memory with provider-specific tokenizer
        val chatMemory = TokenAwareSummarizingMemory.builder()
            .maxTokens(8000)  // Adjust based on model's context window
            .tokenEstimator(YourProviderTokenEstimator(model)::estimateTokenCountInMessage)
            .summarizationThreshold(0.75)  // Summarize at 75% capacity
            .build()
        
        // 3. Build AiServices with ChatClient interface
        val builder = AiServices
            .builder(ChatClient::class.java)
            .streamingChatModel(chatModel)
            .chatMemory(chatMemory)
            .apply {
                // Enable tools conditionally (disable for DESKTOP mode)
                if (executionMode != ExecutionMode.DESKTOP) {
                    tools(LocalFsTools)
                }
            }
            .systemMessageProvider {
                systemMessage(
                    """
                    Tool response format:
                    • All tools return: { "success": boolean, "output": string, "error": string, "metadata": object }
                    • success=true: Tool executed successfully
                    • success=false: Tool failed, check "error" for reason
                    
                    Tool execution guidelines:
                    • Parse the tool response JSON before responding
                    • Check the "success" field before using "output"
                    • Explain errors from the "error" field
                    """.trimIndent()
                )
            }
        
        // Add retrievalAugmentor if provided (for RAG support)
        if (retrievalAugmentor != null) {
            builder.retrievalAugmentor(retrievalAugmentor)
        }
        
        // 4. Build the delegate (LangChain4j proxy)
        val delegate: ChatClient = builder.build()
        
        // 5. Wrap in ChatClientImpl for session management
        val sessionMemoryRepository = DatabaseManager.getInstance().getSessionMemoryRepository()
        return ChatClientImpl(delegate, chatMemory, sessionMemoryRepository)
    }
}
```

**Key Changes from Old Pattern:**

1. **No `memory` parameter** - Factory creates its own `TokenAwareSummarizingMemory`
2. **Returns `ChatClient` not `ChatService`** - Now returns `ChatClientImpl` wrapper
3. **Session management built-in** - Automatic save/restore of conversation context
4. **Provider-specific tokenizer** - Use your provider's token estimator for accurate counts
5. **Two-step creation** - Create delegate, then wrap in `ChatClientImpl`

**For complete implementation examples, refer to:**
- `OpenAiModelFactory.kt` - Example with API key, proxy support, OpenAI tokenizer, and ChatClientImpl wrapping
- `OllamaModelFactory.kt` - Example with base URL and local process integration
- `AnthropicModelFactory.kt` - Example with Anthropic tokenizer and memory configuration
- `ChatClientImpl.kt` - Session management wrapper implementation with memory serialization

### 5. Register Your Factory

Register your factory in the `ProviderRegistry` by adding it to the factories map. Modify `ProviderRegistry.kt`:

```kotlin
object ProviderRegistry {
    private val factories: Map<ModelProvider, ChatModelFactory<*>> =
        mapOf(
            OPENAI to OpenAiModelFactory(),
            XAI to XAiModelFactory(),
            GEMINI to GeminiModelFactory(),
            OLLAMA to OllamaModelFactory(),
            ANTHROPIC to AnthropicModelFactory(),
            LOCALAI to LocalAiModelFactory(),
            LMSTUDIO to LmStudioModelFactory(),
            YOUR_PROVIDER to YourProviderModelFactory(),  // Add your factory here
        )
    
    // ...rest of the implementation
}
```

The registry uses a map-based approach for better type safety and immutability. Once registered, your provider will be available throughout the application.

## Memory Management and Session Persistence

### TokenAwareSummarizingMemory

Your factory should create a `TokenAwareSummarizingMemory` instance that:
- **Tracks token usage** - Uses provider-specific tokenizers for accurate counts
- **Auto-summarizes** - When reaching 75% of max tokens, older messages are summarized
- **Preserves context** - Recent messages kept in full, older ones compressed into structured summaries

**Configuration guidelines:**
```kotlin
TokenAwareSummarizingMemory.builder()
    .maxTokens(8000)        // Set based on your model's context window
                            // OpenAI GPT-4: 8000, Claude: 100000, etc.
    .tokenEstimator(...)    // Use provider-specific tokenizer
    .summarizationThreshold(0.75)  // Trigger at 75% capacity
    .build()
```

### Session Management via ChatClientImpl

The `ChatClientImpl` wrapper automatically handles:
1. **Session Switching** - Save current session, load new session memory
2. **Auto-save** - Periodic saves every 5 minutes
3. **Shutdown Hook** - Save on graceful application exit
4. **Serialization** - Convert memory to/from JSON for database storage

**You don't need to implement session management** - just wrap your delegate in `ChatClientImpl`:
```kotlin
val delegate: ChatClient = builder.build()
val sessionMemoryRepository = DatabaseManager.getInstance().getSessionMemoryRepository()
return ChatClientImpl(delegate, chatMemory, sessionMemoryRepository)
```

### Integration with ChatSessionService

When users create or resume sessions, `ChatSessionService` automatically:
- **On createSession()** - Calls `chatClient.switchSession(sessionId)` to initialize memory
- **On resumeSession()** - Calls `chatClient.switchSession(sessionId)` to restore memory
- **On session switch** - Saves old session, loads new session

**CLI and Desktop code remain unchanged** - they just call `ChatSessionService` methods, and memory synchronization happens automatically.

## Example: Implementation Reference

For reference, here are the key components of existing implementations:

### OpenAI Implementation

- **Settings**: `OpenAiSettings` - Contains API key and presets
- **Factory**: `OpenAiModelFactory` - Creates OpenAI models with token-aware memory
- **Tokenizer**: `OpenAiTokenCountEstimator` - Accurate token counting per model
- **Memory**: 8000 max tokens with 0.75 threshold

### Ollama Implementation

- **Settings**: `OllamaSettings` - Contains base URL and presets
- **Factory**: `OllamaModelFactory` - Creates Ollama models and fetches available models
- **Memory**: 8000 max tokens with default word-count estimation

### Anthropic Implementation

- **Settings**: `AnthropicSettings` - Contains API key and presets
- **Factory**: `AnthropicModelFactory` - Creates Claude models with Anthropic tokenizer
- **Tokenizer**: `AnthropicTokenCountEstimator` - Claude-specific token counting
- **Memory**: 8000 max tokens with 0.75 threshold

## Testing Your Implementation

After implementing your provider, you can test it by:

1. Building and running the Askimo CLI
2. Setting your provider as the active provider:
   ```
   askmio> :set-provider YOUR_PROVIDER
   ```
3. Setting any required parameters:
   ```
   askimo> :set-param api_key your-api-key
   ```
4. Listing available models:
   ```
   askimo> :models
   ```
5. Chatting with a specific model:
   ```
   askimo> :set-param model your-model-name
   askimo> What is the capital of Viet Nam?
   ```


## Conclusion

By following these steps, you can integrate any chat model provider with Askimo. The modular architecture makes it easy to add new providers while maintaining a consistent interface for users.
