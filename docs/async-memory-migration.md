# Migration Guide: Async TokenAwareSummarizingMemory

## What Changed

The `TokenAwareSummarizingMemory` has been upgraded to a **production-ready, thread-safe, non-blocking implementation**.

### Key Improvements

1. **✅ Non-blocking**: Summarization happens asynchronously - user responses are never delayed
2. **✅ Thread-safe**: Safe for concurrent access (web servers, multi-user environments)
3. **✅ Graceful degradation**: Falls back to basic summary if AI fails
4. **✅ Timeout protection**: Won't hang on slow AI calls (30s default timeout)
5. **✅ Proper cleanup**: Implements `AutoCloseable` for resource management

## Migration Steps

### Before (Blocking, Not Thread-Safe)

```kotlin
val memory = TokenAwareSummarizingMemory(
    maxTokens = 4000,
    tokenEstimator = { message -> /* estimate */ },
    summarizationThreshold = 0.75,
    summarizer = summarizer::summarize
)

// Problem: This blocked user response for 2-5 seconds!
chatMemory.add(userMessage)
chatMemory.add(aiResponse)  // Could block here during summarization
```

### After (Async, Thread-Safe)

```kotlin
val memory = TokenAwareSummarizingMemory.builder()
    .maxTokens(4000)
    .tokenEstimator { message -> /* estimate */ }
    .summarizationThreshold(0.75)
    .summarizer(summarizer::summarize)
    .asyncSummarization(true)  // ← Non-blocking (default)
    .build()

// ✅ Never blocks - summarization happens in background
chatMemory.add(userMessage)
chatMemory.add(aiResponse)  // Returns immediately

// Remember to close when done
memory.use { mem ->
    // Use memory
} // Automatically closes and waits for pending summarization
```

## Usage Examples

### Production: Full AI Summarization

```kotlin
// Create separate model instance for summarization
val summarizerModel = OpenAiChatModel.builder()
    .apiKey(apiKey)
    .modelName("gpt-4o-mini")  // Cheap model for summaries
    .build()

val summarizer = DefaultConversationSummarizer(summarizerModel)

val memory = TokenAwareSummarizingMemory.builder()
    .maxTokens(4000)
    .summarizer(summarizer::summarize)
    .asyncSummarization(true)  // Non-blocking
    .summarizationTimeout(30)   // 30 second timeout
    .build()

// Use in chat service
val chatService = ChatService(userChatModel, memory)
```

### Testing: Synchronous Mode

```kotlin
// For tests, use synchronous mode for predictable behavior
val testMemory = TokenAwareSummarizingMemory.builder()
    .maxTokens(2000)
    .asyncSummarization(false)  // Blocking for tests
    .build()

// No need to wait for background threads in tests
```

### Simple: No AI Summarization

```kotlin
// Basic mode: no AI, just extractive summary
val basicMemory = TokenAwareSummarizingMemory.builder()
    .maxTokens(4000)
    // No summarizer = basic extractive summarization
    .build()
```

## Breaking Changes

### Constructor is Now Private

**Before:**
```kotlin
val memory = TokenAwareSummarizingMemory(
    maxTokens = 4000,
    // ...
)
```

**After:**
```kotlin
val memory = TokenAwareSummarizingMemory.builder()
    .maxTokens(4000)
    .build()
```

### Must Close When Done

The memory now implements `AutoCloseable` and must be properly closed:

```kotlin
// Option 1: use() block (recommended)
TokenAwareSummarizingMemory.builder().build().use { memory ->
    // Use memory
} // Automatically closes

// Option 2: Manual close
val memory = TokenAwareSummarizingMemory.builder().build()
try {
    // Use memory
} finally {
    memory.close()  // Shuts down executor, waits for pending summarization
}
```

## Configuration Options

### New Builder Methods

```kotlin
TokenAwareSummarizingMemory.builder()
    .maxTokens(4000)                    // Max tokens in memory
    .tokenEstimator { /* ... */ }       // Custom token counter
    .summarizationThreshold(0.75)       // Trigger at 75% of maxTokens
    .summarizer(summarizer::summarize)  // Optional AI summarizer
    .asyncSummarization(true)           // Enable async (default: true)
    .summarizationTimeout(30)           // Timeout in seconds (default: 30)
    .build()
```

## Performance Impact

### Before (Blocking)
- User request → Add message → **Block 2-5s** → Response to user
- Total user wait: **Normal latency + 2-5s**

### After (Async)
- User request → Add message → Response to user (immediate)
- Summarization happens in background thread
- Total user wait: **Normal latency only**

## Thread Safety

The implementation is now fully thread-safe:

```kotlin
// Safe for concurrent access
val memory = TokenAwareSummarizingMemory.builder().build()

// Multiple threads can safely call add() simultaneously
thread { memory.add(message1) }
thread { memory.add(message2) }
thread { memory.add(message3) }

// messages() returns a thread-safe snapshot
val snapshot = memory.messages()
```

## Backward Compatibility

### If You Can't Update Immediately

The old blocking behavior is still available for testing:

```kotlin
val memory = TokenAwareSummarizingMemory.builder()
    .maxTokens(4000)
    .asyncSummarization(false)  // ← Synchronous mode
    .build()

// Behaves like the old version (blocks on summarization)
```

## Troubleshooting

### Memory Not Closing Properly

**Problem**: Application hangs on shutdown

**Solution**: Always close memory explicitly:
```kotlin
memory.close()  // or use .use { } block
```

### Summarization Taking Too Long

**Problem**: Summarization timeout errors in logs

**Solution**: Increase timeout:
```kotlin
.summarizationTimeout(60)  // 60 seconds instead of default 30
```

### Tests Failing Due to Async Behavior

**Problem**: Tests expect immediate summarization

**Solution**: Use synchronous mode in tests:
```kotlin
.asyncSummarization(false)
```

## Questions?

- Check `ConversationSummarizerExamples.kt` for complete usage examples
- See `DefaultConversationSummarizerTest.kt` for testing patterns
- Refer to class documentation in `TokenAwareSummarizingMemory.kt`

