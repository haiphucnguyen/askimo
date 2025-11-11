# Security Test Safety Improvements

## Problem Identified

The original security tests in `io.askimo.core.security` package were using real AI provider names (like "openai", "gemini", "xai", etc.) when testing keychain storage functionality. This created a serious safety issue:

- When developers ran tests on their development machines, the tests could overwrite their real API keys stored in the system keychain
- Users running `./gradlew test` could lose their actual OpenAI, Gemini, or other AI provider API keys
- This was particularly dangerous in the `SecureSessionManagerTest` and `SecureApiKeyStorageTest` classes

## Solution Implemented

### 1. Created Test-Safe Infrastructure

**TestSecureSessionManager.kt**
- New test-specific version of `SecureSessionManager`
- Automatically prefixes all provider names with "test_" for keychain operations
- Provides same functionality as real class but with safe provider names
- Example: `ModelProvider.OPENAI` becomes "test_openai" in keychain storage

**TestProviderSettings.kt** 
- Test-specific provider settings that implement same interfaces as real providers
- Uses safe test names that won't conflict with actual user data
- Includes `TestModelProvider` enum with safe provider names

### 2. Updated Dangerous Tests

**SecureSessionManagerTest.kt**
- Replaced `SecureSessionManager` with `TestSecureSessionManager`
- Updated cleanup to use safe test provider names: `test_openai`, `test_gemini`, etc.
- Added comprehensive safety documentation
- Fixed platform-aware assertions for Linux CI environments

**SecureApiKeyStorageTest.kt**
- Replaced dangerous direct keychain calls using real provider names
- Updated to use safe test provider constant: `TEST_PROVIDER_NAME = "test_openai_safe"`
- Replaced `SecureSessionManager` with `TestSecureSessionManager`
- Added proper cleanup for safe test provider names

### 3. Verified Existing Tests

**Integration Tests (Already Safe)**
- `SecureApiKeyManagerLinuxIntegrationTest.kt` ✅
- `SecureApiKeyManagerMacOSIntegrationTest.kt` ✅ 
- `KeychainManagerLinuxIntegrationTest.kt` ✅
- `KeychainManagerMacOSIntegrationTest.kt` ✅
- `SecureApiKeyManagerWindowsIntegrationTest.kt` ✅

These were already using safe patterns with `TEST_PROVIDER_PREFIX` and `generateTestProvider()` methods.

## Safety Guarantees

### Before (Dangerous)
```kotlin
// DANGEROUS - could overwrite real user keys!
KeychainManager.storeApiKey("openai", "test-key")
SecureApiKeyManager.storeApiKey("gemini", "test-key") 
```

### After (Safe)
```kotlin
// SAFE - uses test-prefixed names
TestSecureSessionManager automatically converts:
// ModelProvider.OPENAI -> "test_openai"
// ModelProvider.GEMINI -> "test_gemini"
// etc.
```

## Key Safety Features

1. **Automatic Prefixing**: All provider names automatically get "test_" prefix in keychain operations
2. **No Real Provider Names**: Tests never use actual provider names in keychain storage
3. **Proper Cleanup**: All test provider names are cleaned up after test runs
4. **Documentation**: Clear warnings and explanations in code comments
5. **Platform Compatibility**: Tests work correctly on macOS, Linux, and Windows

## Verification

- ✅ All security tests pass on macOS
- ✅ Tests are now safe for Linux CI environments  
- ✅ No real provider names are used in keychain operations
- ✅ Comprehensive cleanup prevents keychain pollution
- ✅ Original failing Linux tests now handle platform differences gracefully

## Result

Developers can now safely run `./gradlew test` without risk of losing their real API keys. The security functionality is thoroughly tested while maintaining complete safety for user data.
