# Retry Utility Usage Guide

## Overview

The `RetryUtils` class provides a reusable retry mechanism for handling transient errors in the askimo codebase. It supports configurable retry attempts, delay strategies, and exception filtering.

## Basic Usage

### Simple Retry
```kotlin
import io.askimo.core.util.RetryUtils
import io.askimo.core.util.RetryConfig

val result = RetryUtils.retry(
    RetryConfig(maxAttempts = 3)
) {
    // Your operation that might fail
    someOperationThatMightFail()
}
```

### Custom Retry Configuration
```kotlin
val config = RetryConfig(
    maxAttempts = 5,
    initialDelayMs = 500,
    delayIncrement = 500, // Linear backoff: 500ms, 1000ms, 1500ms, etc.
    retryCondition = { exception ->
        // Only retry for specific exceptions
        exception is IOException || exception.message?.contains("timeout") == true
    },
    onRetry = { attempt, maxAttempts, exception, delayMs ->
        println("Retry attempt $attempt/$maxAttempts after ${delayMs}ms due to: ${exception.message}")
    }
)

val result = RetryUtils.retry(config) {
    // Your operation
    performNetworkOperation()
}
```

## Predefined Configurations

### Ollama/LangChain4j Transient Errors
For handling common Ollama and LangChain4j issues:
```kotlin
import io.askimo.core.util.RetryPresets

RetryUtils.retry(RetryPresets.OLLAMA_TRANSIENT_ERRORS) {
    // Recipe execution or model operations
    executeRecipe()
}
```

This preset handles:
- `IllegalArgumentException` with "Model returned empty output"
- `NullPointerException` from LangChain4j tool service
- General LangChain4j errors

### Streaming Errors
For streaming response issues:
```kotlin
RetryUtils.retry(RetryPresets.STREAMING_ERRORS) {
    // Streaming operations
    sendStreamingRequest()
}
```

This preset handles:
- Empty response errors
- Streaming connection issues
- LangChain4j streaming errors

### Exponential Backoff
For more sophisticated retry strategies:
```kotlin
val exponentialConfig = RetryPresets.exponentialBackoff(
    maxAttempts = 4,
    initialDelayMs = 1000,
    multiplier = 2.0 // 1s, 2s, 4s, 8s
) { attempt, max, ex, delay ->
    println("Exponential retry $attempt/$max in ${delay}ms")
}

RetryUtils.retry(exponentialConfig) {
    performCriticalOperation()
}
```

## Configuration Options

### RetryConfig Parameters

- **maxAttempts**: Maximum number of attempts (default: 3)
- **initialDelayMs**: Initial delay between retries (default: 1000ms)
- **delayIncrement**: Amount to increase delay for each retry (default: 1000ms)
- **retryableExceptions**: Set of exception types that should trigger retries
- **retryCondition**: Custom function to determine if an exception should trigger a retry
- **onRetry**: Callback function called before each retry attempt

### Retry Logic Priority

1. If `retryCondition` is provided, it takes precedence
2. If `retryableExceptions` is provided, only those exception types are retried
3. If neither is provided, all exceptions trigger retries

## Examples in askimo Codebase

### Recipe Execution (ChatCli.kt)
```kotlin
private fun runYamlCommand(session: Session, name: String, overrides: Map<String, String>) {
    RetryUtils.retry(RetryPresets.OLLAMA_TRANSIENT_ERRORS) {
        executor.run(name = name, opts = RecipeExecutor.RunOpts(overrides = overrides))
    }
}
```

### Streaming Responses (ChatServiceExtensions.kt)
```kotlin
fun ChatService.sendStreamingMessageWithCallback(prompt: String, onToken: (String) -> Unit): String {
    return RetryUtils.retry(RetryPresets.STREAMING_ERRORS) {
        // Streaming implementation with error detection
        performStreamingOperation(prompt, onToken)
    }
}
```

## Best Practices

1. **Use appropriate presets** for common scenarios
2. **Set reasonable retry limits** to avoid infinite loops
3. **Implement proper logging** in onRetry callbacks
4. **Be specific with retry conditions** to avoid retrying non-transient errors
5. **Consider the total timeout** when setting maxAttempts and delays
6. **Test retry behavior** with unit tests

## Error Handling

The retry utility will:
- Rethrow the original exception if it doesn't match retry conditions
- Rethrow the last exception after all attempts are exhausted
- Preserve the original stack trace for debugging
