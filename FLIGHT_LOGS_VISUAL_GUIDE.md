# Flight Logs UI - Visual Design Guide

## Color Specifications

### Primary Palette
```
Blue Accent:
- Light: #60A5FA
- Medium: #1E88E5  
- Dark: #1565C0

Green Success:
- Light: #34D399
- Medium: #10B981
- Dark: #059669

Red Error/Delete:
- Light: #FF5252
- Medium: #EF4444
- Dark: #DC2626

Orange Warning:
- Standard: #FFA500
```

### Background Palette
```
Dark Navy:
- Primary: #0A0E27
- Secondary: #0F1419

Dark Blue:
- Light: #1A1F3A
- Card: #1E293B
- Accent: #1E3A8A
- Variant: #1E2844
```

## Component Specifications

### Header Section
```
┌─────────────────────────────────────────────────────┐
│  [○]  Flight Logs                    [Export All]  │
│  48dp  28sp Bold                     Gradient Btn   │
│       16 missions recorded           48dp height    │
│       14sp, 60% opacity                             │
└─────────────────────────────────────────────────────┘

Spacing: 20dp padding, 20dp bottom margin
Background: Transparent (on gradient bg)
```

### Statistics Card
```
┌─────────────────────────────────────────────────────┐
│   Gradient Background: #1E3A8A → #1E293B            │
│   Shadow: 12dp, Rounded: 20dp                       │
│                                                     │
│   [○]                    │           [○]           │
│   56dp circle           2dp          56dp circle    │
│   Blue glow            gradient       Green glow    │
│                        divider                      │
│     24                  │              15h 30m      │
│   24sp Bold            │            24sp Bold       │
│  Total Flights         │           Flight Time     │
│   13sp Medium          │            13sp Medium     │
│                                                     │
└─────────────────────────────────────────────────────┘

Inner Padding: 24dp
Icon Size: 32dp inside 56dp circle
```

### Active Flight Card
```
┌─────────────────────────────────────────────────────┐
│   Gradient: #10B981 (25%) → #059669 (30%)          │
│   Shadow: 10dp, Rounded: 16dp                       │
│                                                     │
│   [○]  🟢 Flight Active         [End Flight]       │
│   48dp   18sp Bold               Gradient Btn       │
│        Flight ID: 12345          Red gradient       │
│        14sp Medium               44dp height        │
│                                                     │
└─────────────────────────────────────────────────────┘

Padding: 20dp all sides
End Button: 22dp rounded, 6dp shadow
```

### Flight Item Card
```
┌─────────────────────────────────────────────────────┐
│   Gradient: #1E293B → #0F172A                      │
│   Shadow: 8dp, Rounded: 18dp                        │
│                                                     │
│   [○]    Dec 08, 2025               [↓]  [×]       │
│   52dp    18sp Bold                 40dp  40dp      │
│         15:30                     circles circles    │
│         14sp, Blue                                  │
│                                                     │
│   ┌──────────────────┐  ┌──────────────────┐      │
│   │ [○] 2h 30m      │  │ [○] 15.50 ha    │      │
│   │ 20dp Duration   │  │ 20dp Area       │      │
│   │ Blue bg         │  │ Green bg        │      │
│   └──────────────────┘  └──────────────────┘      │
│                                                     │
└─────────────────────────────────────────────────────┘

Main Padding: 20dp
Detail Cards: 12dp padding, 12dp rounded
Card Gap: 12dp between cards
```

### Export Dialog
```
┌─────────────────────────────────────────────────────┐
│   [○]  Export Flight Log                            │
│   40dp  20sp Bold                                   │
│                                                     │
│   Choose export format:                             │
│   14sp, 70% opacity                                 │
│                                                     │
│   ┌─────────────────────────────────────────────┐  │
│   │ [○]  CSV Format                    [→]      │  │
│   │ 36dp  16sp Bold                    20dp      │  │
│   │      Comma-separated values                 │  │
│   │      12sp, 60% opacity                      │  │
│   └─────────────────────────────────────────────┘  │
│   12dp gap between options                          │
│   ┌─────────────────────────────────────────────┐  │
│   │ [○]  KML Format                    [→]      │  │
│   │ ...                                         │  │
│   └─────────────────────────────────────────────┘  │
│                                                     │
│                                        [Cancel]     │
└─────────────────────────────────────────────────────┘

Dialog Padding: 16dp
Option Cards: Shadow 4dp, Rounded 12dp
```

### Delete Dialog
```
┌─────────────────────────────────────────────────────┐
│   [⚠]  Delete Flight                                │
│   40dp  20sp Bold, Red                              │
│                                                     │
│   Are you sure you want to delete this flight?     │
│   15sp Medium                                       │
│                                                     │
│   ⚠️ This action cannot be undone.                 │
│   13sp, Red 80%                                     │
│                                                     │
│                       [Cancel]  [[× Delete]]        │
│                         Blue      Red gradient      │
│                                   Shadow 6dp        │
└─────────────────────────────────────────────────────┘

Delete Button: 20dp rounded, Icon + Text
Button Padding: 20dp horizontal, 10dp vertical
```

### Empty State
```
┌─────────────────────────────────────────────────────┐
│   Background: #1E2844 (60%)                         │
│   Shadow: 8dp, Rounded: 20dp                        │
│                                                     │
│                   [  ✈️  ]                          │
│                   80dp circle                       │
│                 Radial gradient                     │
│                                                     │
│            No flights recorded yet                  │
│               18sp Medium, 70%                      │
│                                                     │
│          Start a mission to begin logging          │
│               14sp, 50%                             │
│                                                     │
└─────────────────────────────────────────────────────┘

Padding: 48dp all sides
Icon: 48dp inside 80dp gradient circle
```

## Typography Scale

```
Hero Title:        28sp, Bold         (Flight Logs)
Section Title:     20sp, Bold         (Recent Flights)
Card Title:        18sp, Bold         (Flight date)
Body Large:        16sp, Medium/Bold  (Stats values)
Body Medium:       14-15sp, Medium    (Flight details)
Body Small:        13sp, Medium       (Labels)
Caption:           11-12sp, Regular   (Descriptions)
```

## Spacing System

```
Micro:    4dp    (Icon-text gaps, small separators)
Small:    8dp    (Element spacing within cards)
Medium:   12dp   (Card gaps, section spacing)
Large:    16dp   (Padding, margins)
XLarge:   20dp   (Major section padding)
XXLarge:  24dp   (Card inner padding)
```

## Shadow Depths

```
Level 1:  4dp    (Subtle depth - small cards)
Level 2:  6dp    (Medium depth - buttons)
Level 3:  8dp    (Standard depth - cards)
Level 4:  10dp   (High depth - active states)
Level 5:  12dp   (Maximum depth - stats card)
```

## Corner Radius Scale

```
Small:    10-12dp   (Small elements, mini cards)
Medium:   14-16dp   (Standard cards)
Large:    18-20dp   (Large cards, containers)
XLarge:   22-24dp   (Pill-shaped buttons)
Circle:   50%       (Icon badges, avatars)
```

## Gradient Definitions

### Background Gradients
```kotlin
// Main screen background
Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0A0E27),
        Color(0xFF1A1F3A),
        Color(0xFF0F1419)
    )
)

// Stats card
Brush.linearGradient(
    colors = listOf(
        Color(0xFF1E3A8A).copy(alpha = 0.8f),
        Color(0xFF1E293B).copy(alpha = 0.9f)
    )
)

// Flight item card
Brush.linearGradient(
    colors = listOf(
        Color(0xFF1E293B).copy(alpha = 0.9f),
        Color(0xFF0F172A).copy(alpha = 0.95f)
    )
)
```

### Button Gradients
```kotlin
// Export button (blue)
Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF1E88E5),
        Color(0xFF1565C0)
    )
)

// End flight button (red)
Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFEF4444),
        Color(0xFFDC2626)
    )
)

// Delete button (red)
Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFEF4444),
        Color(0xFFDC2626)
    )
)
```

### Icon Badge Gradients
```kotlin
// Green success badge
Brush.radialGradient(
    colors = listOf(
        Color(0xFF10B981),
        Color(0xFF059669)
    )
)

// Blue info badge
Brush.linearGradient(
    colors = listOf(
        Color(0xFF2196F3),
        Color(0xFF1976D2)
    )
)

// Icon glow effect
Brush.radialGradient(
    colors = listOf(
        color.copy(alpha = 0.3f),
        Color.Transparent
    )
)
```

## Icon Sizes

```
Large Icons:     48dp    (Empty state, large badges)
Medium Icons:    28-32dp (Card status, stat icons)
Standard Icons:  24dp    (Header icons)
Small Icons:     20dp    (Action buttons, inline icons)
Tiny Icons:      16-18dp (Indicators, mini buttons)
```

## Opacity Levels

```
Primary Text:      100%   (White, full opacity)
Secondary Text:    70-80% (Lighter, readable)
Tertiary Text:     50-60% (Subtle, supportive)
Disabled:          30-40% (Inactive elements)
Background Tint:   10-30% (Colored backgrounds)
```

## Animation Timing (Future Enhancement)

```
Fast:     150ms   (Micro-interactions)
Normal:   250ms   (Standard transitions)
Slow:     400ms   (Complex animations)
Easing:   ease-in-out (Smooth, natural)
```

## Accessibility Notes

- Minimum touch target: 48dp x 48dp ✅
- Text contrast ratio: 4.5:1 minimum ✅
- Icon size: Minimum 24dp ✅
- Spacing: Adequate breathing room ✅
- Color independence: Icons + text labels ✅

## Design System Consistency

All components follow these principles:
✅ Consistent spacing (8dp grid)
✅ Consistent colors (defined palette)
✅ Consistent typography (defined scale)
✅ Consistent shadows (defined depths)
✅ Consistent corners (defined radius)
✅ Consistent gradients (defined patterns)

---
**Design System Version**: 1.0
**Last Updated**: December 8, 2025

