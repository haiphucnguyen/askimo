# Creating a New Chat Model in Askimo

This guide explains how to implement a new chat model provider in Askimo. By following these steps, you can integrate any chat model API with the Askimo CLI.

## Architecture Overview

Askimo uses a modular architecture for chat models with the following key components:

1. **ChatService**: Interface that defines the contract for all chat models
2. **ChatModelFactory**: Interface for creating chat model instances
3. **ProviderSettings**: Interface for model-specific configuration
4. **ModelProvider**: Enum that identifies different model providers
5. **ModelRegistry**: Central registry that manages all model factories

Each model provider (like OpenAI or Ollama) has its own implementation of these interfaces.

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

First, add your provider to the `ModelProvider` enum in `io.askimo.cli.model.core.ModelProvider`:

```kotlin
@Serializable
enum class ModelProvider {
    @SerialName("OPEN_AI") OPEN_AI,
    @SerialName("OLLAMA") OLLAMA,
    @SerialName("YOUR_PROVIDER") YOUR_PROVIDER,  // Add your provider here
    @SerialName("UNKNOWN") UNKNOWN,
}
```

### 3. Create Provider Settings

Create a settings class that implements `ProviderSettings` interface. This class will store configuration specific to your provider:

```kotlin
// File: io.askimo.cli.model.providers.yourprovider.YourProviderSettings.kt

@Serializable
data class YourProviderSettings(
    var apiKey: String = "", 
    override var presets: Presets = Presets(Style.BALANCED, Verbosity.NORMAL),
) : ProviderSettings {
    override fun describe(): List<String> =
        listOf(
            "apiKey:      ${apiKey.take(5)}***",
            "presets:     $presets",
        )
}
```

### 4. Implement the Chat Service

Create a class that implements the `ChatService` interface. This class will handle the actual communication with your provider's API:

```kotlin
// File: io.askimo.cli.model.providers.yourprovider.YourProviderChatService.kt

class YourProviderChatService(
    private val modelName: String,
    private val settings: YourProviderSettings,
    private val memory: ChatMemory,
    private val systemPrompt: String?,
) : ChatService {
    override val id: String = modelName
    override val provider: ModelProvider = ModelProvider.YOUR_PROVIDER
    
    // Initialize your model client using LangChain4j
    private val chatModel by lazy {
        // Use the LangChain4j implementation for your provider
        // This example assumes a streaming chat model similar to OpenAI or Ollama
        val builder = YourProviderStreamingChatModel.builder()
            .apiKey(settings.apiKey)
            .modelName(modelName)
            
        // Apply sampling parameters (temperature, top-p)
        val sampling = samplingFor(settings.presets.style)
        builder.temperature(sampling.temperature).topP(sampling.topP)
        
        // Apply token limits based on verbosity
        val tokenLimit = tokensFor(settings.presets.verbosity)
        builder.maxTokens(tokenLimit)
        
        // Build and return the model
        builder.build()
    }
    
    override fun chat(
        prompt: String,
        onToken: (String) -> Unit,
    ): String {
        ...
    }
}
```

### 5. Implement the Model Factory

Create a factory class that implements `ChatModelFactory`. This class will be responsible for creating instances of your model:

```kotlin
// File: io.askimo.cli.model.providers.yourprovider.YourProviderModelFactory.kt

class YourProviderModelFactory : ChatModelFactory {
    override val provider: ModelProvider = ModelProvider.YOUR_PROVIDER
    
    override fun availableModels(settings: ProviderSettings): List<String> =
        try {
            // Implement logic to fetch available models from your provider
            // This could be an API call or a hardcoded list
            
            // Example:
            // val client = YourProviderClient(settings.apiKey)
            // client.listModels().map { it.id }
            
            listOf("model1", "model2", "model3")  // Replace with actual implementation
        } catch (e: Exception) {
            println("⚠️ Failed to fetch models from YourProvider: ${e.message}")
            emptyList()
        }
    
    override fun defaultModel(): String = "default-model-name"  // Set your default model
    
    override fun defaultSettings(): ProviderSettings =
        YourProviderSettings(
        )
    
    override fun create(
        model: String,
        settings: ProviderSettings,
        memory: ChatMemory,
    ): ChatService {
        require(settings is YourProviderSettings) {
            "Invalid settings type for YourProvider: ${settings::class.simpleName}"
        }
        
        return YourProviderChatService(model, settings, memory, null)
    }
}
```

### 6. Register Your Factory

Register your factory in the `ModelRegistry`. The best place to do this is by modifying the `init` block in `ModelRegistry.kt`:

```kotlin
init {
    // Register known factories
    register(OpenAiModelFactory())
    register(OllamaModelFactory())
    register(YourProviderModelFactory())  // Add your factory here
}
```

Alternatively, you can register your factory programmatically at runtime:

```kotlin
ModelRegistry.register(YourProviderModelFactory())
```

## Example: Implementation Reference

For reference, here are the key components of existing implementations:

### OpenAI Implementation

- **Settings**: `OpenAiSettings` - Contains API key and presets
- **Chat Service**: `OpenAiChatService` - Implements chat functionality using OpenAI's API
- **Factory**: `OpenAiModelFactory` - Creates OpenAI models and fetches available models

### Ollama Implementation

- **Settings**: `OllamaSettings` - Contains base URL and presets
- **Chat Service**: `OllamaChatService` - Implements chat functionality using Ollama's API
- **Factory**: `OllamaModelFactory` - Creates Ollama models and fetches available models

## Testing Your Implementation

After implementing your provider, you can test it by:

1. Building and running the Askimo CLI
2. Setting your provider as the active provider:
   ```
   askmio> :setprovider YOUR_PROVIDER
   ```
3. Setting any required parameters:
   ```
   askimo> :setparam api_key your-api-key
   ```
4. Listing available models:
   ```
   askimo> :models
   ```
5. Chatting with a specific model:
   ```
   askimo> :setparam model your-model-name
   askimo> What is the capital of Viet Nam?
   ```

## Conclusion

By following these steps, you can integrate any chat model provider with Askimo. The modular architecture makes it easy to add new providers while maintaining a consistent interface for users.

Remember to handle errors gracefully and provide clear feedback to users when something goes wrong with your provider's API.