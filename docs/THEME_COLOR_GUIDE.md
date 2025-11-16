# Systematic Theme Color Management Guide

## Problem That Was Solved

Components were using inconsistent color approaches, leading to:
- Some components not reacting to accent color changes
- Text colors not properly contrasting with backgrounds
- Mix of explicit colors and inherited colors
- No clear pattern for when to use which approach

## The Systematic Solution

### 1. Three-Level Color Hierarchy

We established a clear 3-level color system:

| Level | Opacity | ComponentColors Function | Use Case |
|---|---|---|---|
| **Prominent** | 30% accent | `primaryCardColors()` | Selected items, important headers, active states |
| **Subtle** | 15% accent | `bannerCardColors()` | Section headers, informational banners, list items |
| **Neutral** | 0% accent | `surfaceVariantCardColors()` | Unselected options, inactive states |

### 2. Theme Color Scheme Definition (Theme.kt)

All accent-reactive colors are defined in `getLightColorScheme()` and `getDarkColorScheme()`:

```kotlin
// Light Mode
primaryContainer = accentColor.lightColor.copy(alpha = 0.3f)      // 30% - Prominent
onPrimaryContainer = Color.Black

secondaryContainer = accentColor.lightColor.copy(alpha = 0.15f)   // 15% - Subtle  
onSecondaryContainer = Color.Black

surfaceVariant = baseScheme.surfaceVariant                        // 0% - Neutral (from base)
onSurfaceVariant = baseScheme.onSurfaceVariant

// Dark Mode - Same pattern with Color.White for onContainer colors
```

**Key principle:** When you set a `container` color, ALWAYS set its corresponding `onContainer` color.

### 3. ComponentColors.kt - Single Source of Truth

All component color functions are centralized:

```kotlin
object ComponentColors {
    @Composable
    fun primaryCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    
    @Composable
    fun bannerCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    
    @Composable
    fun surfaceVariantCardColors(): CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

## Rules to Follow (Prevent Future Issues)

### Rule 1: Always Use ComponentColors Functions

❌ **WRONG:**
```kotlin
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    )
)
```

✅ **CORRECT:**
```kotlin
Card(colors = ComponentColors.primaryCardColors())
```

### Rule 2: Explicitly Set Text Colors for Accent-Colored Cards

When using accent-colored cards (`primaryCardColors()` or `bannerCardColors()`), explicitly set text colors:

❌ **WRONG (Material 3 Cards don't propagate contentColor reliably in Compose Desktop):**
```kotlin
Card(colors = ComponentColors.bannerCardColors()) {
    Text("Title")  // May not inherit color properly
}
```

✅ **CORRECT:**
```kotlin
Card(colors = ComponentColors.bannerCardColors()) {
    Text(
        "Title",
        color = MaterialTheme.colorScheme.onSecondaryContainer  // Explicit!
    )
    Icon(..., tint = MaterialTheme.colorScheme.onSecondaryContainer)
}
```

### Rule 3: Match Colors to Card Type

| Card Type | Background Color | Text/Icon Color |
|---|---|---|
| `primaryCardColors()` | `primaryContainer` | `onPrimaryContainer` |
| `bannerCardColors()` | `secondaryContainer` | `onSecondaryContainer` |
| `surfaceVariantCardColors()` | `surfaceVariant` | `onSurfaceVariant` |

### Rule 4: Use the Right Card Type for the Right Purpose

**Prominent (30% accent) - `primaryCardColors()`:**
- Selected theme option
- Selected accent color option
- Active session in sidebar
- Provider/Model header in ChatView
- Selected model in dialogs

**Subtle (15% accent) - `bannerCardColors()`:**
- Chat Configuration section header
- Font Settings section header
- Session cards in SessionsView
- Search result indicator

**Neutral (0% accent) - `surfaceVariantCardColors()`:**
- Unselected theme options
- Unselected accent color options
- File attachments
- About page cards
- General informational cards

### Rule 5: Never Read Colors Directly from MaterialTheme in Card Content

❌ **WRONG:**
```kotlin
Card(colors = ComponentColors.bannerCardColors()) {
    ComponentColors.CardContent(
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer  // BAD!
    ) {
        Text("Content")
    }
}
```

The `CardContent` wrapper reading from `MaterialTheme.colorScheme` can get stale values. Instead, set colors explicitly on each component.

✅ **CORRECT:**
```kotlin
Card(colors = ComponentColors.bannerCardColors()) {
    Text(
        "Content",
        color = MaterialTheme.colorScheme.onSecondaryContainer
    )
}
```

### Rule 6: When Adding New Color-Reactive Features

1. **First:** Decide which level (Prominent/Subtle/Neutral)
2. **Then:** Add color to Theme.kt if needed
3. **Next:** Add function to ComponentColors.kt
4. **Finally:** Use it consistently everywhere

## Checklist for New Components

When adding a new card/component:

- [ ] Chose the right color level (Prominent/Subtle/Neutral)
- [ ] Used `ComponentColors.xxxCardColors()` function
- [ ] Set explicit `color` on all Text components
- [ ] Set explicit `tint` on all Icon components
- [ ] Matched text/icon colors to card type (onPrimaryContainer, onSecondaryContainer, or onSurfaceVariant)
- [ ] Tested with all accent colors (Green, Blue, Purple, Orange, Pink, Teal)
- [ ] Tested in both light and dark modes

## Quick Reference: Color Mapping

```kotlin
// Prominent Cards (30% accent)
Card(colors = ComponentColors.primaryCardColors()) {
    Text("Text", color = MaterialTheme.colorScheme.onPrimaryContainer)
    Icon(..., tint = MaterialTheme.colorScheme.onPrimaryContainer)
}

// Subtle Cards (15% accent)  
Card(colors = ComponentColors.bannerCardColors()) {
    Text("Text", color = MaterialTheme.colorScheme.onSecondaryContainer)
    Icon(..., tint = MaterialTheme.colorScheme.onSecondaryContainer)
}

// Neutral Cards (0% accent)
Card(colors = ComponentColors.surfaceVariantCardColors()) {
    Text("Text", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Icon(..., tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

## Why This Works

1. **Centralization:** All color logic in ComponentColors.kt and Theme.kt
2. **Explicit Pairing:** Container colors always paired with onContainer colors
3. **Direct Assignment:** Text/icons get explicit colors, no reliance on propagation
4. **Clear Hierarchy:** Three distinct levels with clear purposes
5. **Reactive:** All colors read from MaterialTheme.colorScheme which updates on theme changes

## Common Mistakes to Avoid

1. ❌ Using `CardDefaults.cardColors()` directly
2. ❌ Setting only `containerColor` without `contentColor`
3. ❌ Relying on automatic color inheritance in Compose Desktop
4. ❌ Reading colors through wrappers that may cache values
5. ❌ Mixing explicit colors with inherited colors inconsistently
6. ❌ Using wrong onContainer color for a card type

## Result

✅ All components react to accent color changes
✅ Text always contrasts properly with backgrounds
✅ Clear visual hierarchy (Prominent > Subtle > Neutral)
✅ Systematic, predictable, maintainable
✅ Easy to add new themed components

---

**Follow these rules, and theme color issues will never happen again!** 🎨✨

