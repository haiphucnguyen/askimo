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

1. **ChatService**: Interface that defines the contract for all chat models
2. **ChatModelFactory**: Generic interface for creating chat model instances with type parameter `<T : ProviderSettings>`
3. **ProviderSettings**: Interface for model-specific configuration with methods for validation, field management, and deep copying
4. **ModelProvider**: Enum that identifies different model providers (OpenAI, XAI, Gemini, Ollama, Anthropic, LocalAI, LMStudio)
5. **ProviderRegistry**: Central registry that manages all model factories using a map-based structure

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
    override var presets: Presets = Presets(Style.BALANCED, Verbosity.NORMAL),
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
        memory: ChatMemory,
        retrievalAugmentor: RetrievalAugmentor?,
        sessionMode: SessionMode,
    ): ChatService {
        // 1. Build your provider's streaming chat model using LangChain4j
        // 2. Apply sampling parameters (temperature, topP) from settings.presets.style
        // 3. Build AiServices with chat memory
        // 4. Enable tools conditionally (disable for DESKTOP mode)
        // 5. Set system message provider with tool response format instructions
        // 6. Add retrievalAugmentor if provided (for RAG support)
        // 7. Return the built ChatService
    }
    
    override fun createMemory(
        model: String,
        settings: YourProviderSettings,
    ): ChatMemory {
        // Optional: Customize memory settings, or use default
        // Default returns: MessageWindowChatMemory.withMaxMessages(200)
    }
}
```

**For complete implementation examples, refer to:**
- `OpenAiModelFactory.kt` - Example with API key, proxy support, and sampling parameters
- `OllamaModelFactory.kt` - Example with base URL and local process integration

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

## Example: Implementation Reference

For reference, here are the key components of existing implementations:

### OpenAI Implementation

- **Settings**: `OpenAiSettings` - Contains API key and presets
- **Factory**: `OpenAiModelFactory` - Creates OpenAI models and fetches available models

### Ollama Implementation

- **Settings**: `OllamaSettings` - Contains base URL and presets
- **Factory**: `OllamaModelFactory` - Creates Ollama models and fetches available models

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

Remember to handle errors gracefully and provide clear feedback to users when something goes wrong with your provider's API.