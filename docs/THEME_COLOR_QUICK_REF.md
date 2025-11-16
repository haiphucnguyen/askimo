# Theme Color Quick Reference Card

## 🎨 The Three Levels

| Level | Function | Opacity | When to Use |
|---|---|---|---|
| **Prominent** | `primaryCardColors()` | 30% | Selected items, active states |
| **Subtle** | `bannerCardColors()` | 15% | Banners, section headers, list items |
| **Neutral** | `surfaceVariantCardColors()` | 0% | Unselected items, inactive states |

## 🔧 Code Templates

### Prominent Card (Selected/Active)
```kotlin
Card(colors = ComponentColors.primaryCardColors()) {
    Text("Selected", color = MaterialTheme.colorScheme.onPrimaryContainer)
    Icon(..., tint = MaterialTheme.colorScheme.onPrimaryContainer)
}
```

### Subtle Card (Banner/Section)
```kotlin
Card(colors = ComponentColors.bannerCardColors()) {
    Text("Section Header", color = MaterialTheme.colorScheme.onSecondaryContainer)
    Icon(..., tint = MaterialTheme.colorScheme.onSecondaryContainer)
}
```

### Neutral Card (Default/Inactive)
```kotlin
Card(colors = ComponentColors.surfaceVariantCardColors()) {
    Text("Unselected", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Icon(..., tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

## ✅ The Golden Rules

1. ✅ **Always use `ComponentColors` functions** - Never `CardDefaults.cardColors()` directly
2. ✅ **Always set explicit text colors** - Don't rely on inheritance
3. ✅ **Match colors to card type** - Use the right `onContainer` color
4. ✅ **Test with all accents** - Green, Blue, Purple, Orange, Pink, Teal
5. ✅ **Test both modes** - Light and Dark

## 🚫 Common Mistakes

- ❌ `CardDefaults.cardColors(containerColor = ...)`
- ❌ `Text("Title")` without explicit `color`
- ❌ Using `onPrimaryContainer` on a `bannerCardColors()` card
- ❌ Using `CardContent` wrapper with `MaterialTheme.colorScheme`

## 🗺️ Real Examples in Codebase

**Prominent (30%):**
- ChatView: Provider/Model header
- SettingsView: Selected theme option
- Sidebar: Selected session

**Subtle (15%):**
- SettingsView: Chat Configuration, Font Settings
- SessionsView: Session cards
- ChatView: Search indicator

**Neutral (0%):**
- SettingsView: Unselected options
- ChatView: File attachments
- AboutView: Info cards

---
**💡 When in doubt, check `docs/THEME_COLOR_GUIDE.md` for detailed explanations!**

