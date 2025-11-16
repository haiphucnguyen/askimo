# New Themed Component Checklist

Use this checklist when adding any new component that should use theme colors.

## Planning Phase

- [ ] Decide which color level the component needs:
  - [ ] **Prominent (30% accent)** - Selected/Active states?
  - [ ] **Subtle (15% accent)** - Banner/Section header?
  - [ ] **Neutral (0% accent)** - Default/Inactive state?

## Implementation Phase

- [ ] Use the correct `ComponentColors` function:
  - [ ] `primaryCardColors()` for Prominent
  - [ ] `bannerCardColors()` for Subtle
  - [ ] `surfaceVariantCardColors()` for Neutral

- [ ] Set explicit colors on ALL text components:
  - [ ] `color = MaterialTheme.colorScheme.onPrimaryContainer` (for Prominent)
  - [ ] `color = MaterialTheme.colorScheme.onSecondaryContainer` (for Subtle)
  - [ ] `color = MaterialTheme.colorScheme.onSurfaceVariant` (for Neutral)

- [ ] Set explicit tints on ALL icon components:
  - [ ] `tint = MaterialTheme.colorScheme.onPrimaryContainer` (for Prominent)
  - [ ] `tint = MaterialTheme.colorScheme.onSecondaryContainer` (for Subtle)
  - [ ] `tint = MaterialTheme.colorScheme.onSurfaceVariant` (for Neutral)

- [ ] Verify no explicit colors from wrong category (e.g., no `onSurfaceVariant` on a `bannerCardColors()` card)

## Testing Phase

- [ ] Test with **all accent colors**:
  - [ ] Green
  - [ ] Blue
  - [ ] Purple
  - [ ] Orange
  - [ ] Pink
  - [ ] Teal

- [ ] Test in **both modes**:
  - [ ] Light mode
  - [ ] Dark mode

- [ ] Verify:
  - [ ] Background color changes when accent changes (for Prominent and Subtle)
  - [ ] Background stays neutral (for Neutral)
  - [ ] Text always readable (proper contrast)
  - [ ] Icons always visible
  - [ ] No visual glitches

## Code Review Checklist

- [ ] No direct use of `CardDefaults.cardColors()`
- [ ] No `CardContent` wrappers reading from `MaterialTheme.colorScheme`
- [ ] All Text components have explicit `color` property
- [ ] All Icon components have explicit `tint` property
- [ ] Colors match the card type (right `onContainer` color)
- [ ] Consistent with similar components in other views

## Documentation

- [ ] Add code comments if the component is complex
- [ ] Update relevant view documentation if needed
- [ ] Consider adding example to `THEME_COLOR_GUIDE.md` if it's a new pattern

---

**✅ If all boxes are checked, your themed component is ready!**

See `docs/THEME_COLOR_GUIDE.md` for detailed information.
See `docs/THEME_COLOR_QUICK_REF.md` for quick reference.

