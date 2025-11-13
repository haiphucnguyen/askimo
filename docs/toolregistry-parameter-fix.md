# ToolRegistry Parameter Name Resolution Fix

## Issue
The `gitcommit` recipe was passing `null` to the `GitTools.commit()` method because the `ToolRegistry` was unable to resolve parameter names correctly when invoking methods with `Map<*, *>` arguments.

### Root Cause
When using Java reflection (`java.lang.reflect.Parameter`), parameter names are NOT preserved in the bytecode by default. This resulted in parameters being named `arg0`, `arg1`, `arg2`, etc. instead of their actual names like `message`, `signoff`, `noVerify`.

**The Problem Flow:**
1. Recipe YAML has: `args: { message: "{{output}}" }`
2. RecipeExecutor renders templates: `{ "message": "Actual commit message" }`
3. ToolRegistry tries to match: `args["message"]` but parameter name is `arg0`
4. Result: `null` passed to `commit()` → NullPointerException

## Solution

### Use Kotlin Reflection for Parameter Names ✅

Kotlin reflection (`kotlin.reflect`) DOES preserve parameter names, so we updated the `ToolRegistry` to use Kotlin reflection when available.

**File**: `/cli/src/main/kotlin/io/askimo/cli/recipes/ToolRegistry.kt`

**Changes Made:**

1. **Added Kotlin Reflection Imports:**
```kotlin
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod
```

2. **Updated Map Argument Handling:**
```kotlin
is Map<*, *> -> {
    // Use Kotlin reflection to get actual parameter names
    val kFunction = target::class.declaredFunctions.find { it.javaMethod == m }
    if (kFunction != null) {
        val callArgs = Array<Any?>(kFunction.parameters.size - 1) { null } // -1 for 'this'
        kFunction.parameters.drop(1).forEachIndexed { i, kParam ->
            val paramName = kParam.name  // ✅ Gets "message", not "arg0"
            val raw = args[paramName]
            debug("Resolving parameter '$paramName' with value: $raw")
            callArgs[i] = coerce(raw, m.parameters[i].type)
        }
        m.invoke(target, *callArgs)
    } else {
        // Fallback to Java reflection (for edge cases)
        debug("Warning: Could not find Kotlin function, using Java reflection fallback")
        // ... fallback code ...
    }
}
```

## How It Works Now

### Before (Broken):
```
YAML: args: { message: "{{output}}" }
  ↓
Rendered: { "message": "Fix: update docs" }
  ↓
Java Reflection: p.name = "arg0"  // ❌ Wrong!
  ↓
Lookup: args["arg0"] = null       // ❌ Not found!
  ↓
commit(message = null)            // ❌ NPE!
```

### After (Fixed):
```
YAML: args: { message: "{{output}}" }
  ↓
Rendered: { "message": "Fix: update docs" }
  ↓
Kotlin Reflection: kParam.name = "message"  // ✅ Correct!
  ↓
Lookup: args["message"] = "Fix: update docs" // ✅ Found!
  ↓
commit(message = "Fix: update docs")         // ✅ Works!
```

## Debug Logging

Added debug logging to track parameter resolution:
```kotlin
debug("Resolving parameter '$paramName' with value: $raw")
```

This will show in logs:
```
Resolving parameter 'message' with value: Fix: update docs
Resolving parameter 'signoff' with value: false
Resolving parameter 'noVerify' with value: false
```

## Testing

### Test the Fix:
```bash
# Make some changes and stage them
git add .

# Run gitcommit recipe
askimo gitcommit

# Should now work correctly!
```

### With Debug Logging:
```bash
export LOG_LEVEL=DEBUG
askimo gitcommit
```

You should see:
```
Resolving parameter 'message' with value: <generated commit message>
```

## Why Kotlin Reflection Works

| Reflection Type | Parameter Names | Why |
|----------------|-----------------|-----|
| **Java Reflection** | `arg0`, `arg1`, ... | Names stripped at compile time (unless `-parameters` flag used) |
| **Kotlin Reflection** | `message`, `signoff`, ... | Kotlin compiler preserves names in metadata |

Kotlin's reflection API reads from Kotlin's own metadata annotations (`@Metadata`) which preserve parameter names, types, nullability, default values, etc.

## Fallback Behavior

If Kotlin reflection fails (edge case), the code falls back to Java reflection and logs a warning:
```
Warning: Could not find Kotlin function, using Java reflection fallback
```

This ensures the code doesn't crash even if Kotlin reflection is unavailable.

## Build Status

✅ Code compiles successfully
✅ Spotless formatting applied
✅ Ready to test with gitcommit recipe

## Related Fixes

This fix works in conjunction with the other improvements made to the gitcommit recipe:
1. Better error messages in `GitTools.commit()`
2. Debug logging in `RecipeExecutor`
3. Template resolution warnings
4. Null safety checks

Together, these provide a robust solution for the recipe system!

## Summary

The core issue was **Java reflection doesn't preserve parameter names** in Kotlin-compiled code. By switching to **Kotlin reflection**, we can now correctly match YAML argument names (`message`) to actual method parameters, fixing the NullPointerException in the gitcommit recipe.

🎉 **The gitcommit recipe should now work correctly!**

