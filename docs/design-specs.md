# Design Specifications: Project Neon Glass

## 🎨 Theme Updates (Portrait Mode)

### 1. Glassmorphism Card
- **Background**: Semi-transparent dark surface with blur simulation.
  - Color: `MaterialTheme.colorScheme.surface` (or Black)
  - Alpha: `0.7f` (70% opacity)
  - Note: Real-time blur (`Modifier.blur`) is expensive. We will use a semi-transparent surface on top of a "living" background to create the depth effect.
- **Border**: Transparent/Subtle when inactive.

### 2. Neon Gradient Border (Active State)
- **Gradient**: Linear Gradient
  - Colors: Cyan (`#00FFFF`) → Purple (`#9D00FF`)
  - Direction: Top-Left to Bottom-Right
-- Verify the UI remains unchanged (or acceptable if shared components are touched).

## Portrait Mode: Neon Glass UI

### 1. Concept: "Cyberpunk Glass"
- **Background**: Living Background (Animated dark gradients).
- **Cards**: Glassmorphism (Dark semi-transparent layers).
- **Accents**: Neon Cyan (`#00FFFF`) & Neon Purple (`#9D00FF`).

### 2. Floating Control Panel
- **Layout**: Bottom control panel overlays the news list.
- **Background**: `BottomPanelGradient` (Transparent Top -> Solid Black Bottom) for seamless "see-through" effect.
- **Interaction**: Thumb-friendly buttons on a floating glass layer.

### 3. Component Details
- **NewsCard**:
    - `GlassBackground`: `#202020` @ 70% opacity.
    - `GlassBorder`: White @ 30% opacity (Unselected).
    - `NeonBorder`: Cyan-Purple Gradient (Selected).
    - Elevation: 0dp (Flat glass look).
- **Glow Effect**:
  - Shadow: Colored shadow matching the border primary color (`Cyan`).
  - Elevation: `8.dp` to `16.dp` when selected.

### 3. Living Background
- **Concept**: A subtle, dark gradient mesh or particle field that moves slowly.
- **Implementation**: `Box` background with a `Brush.radialGradient` or `Brush.linearGradient` that animates its offset.
- **Colors**: Deep Blue / Black / Dark Purple mix.

### 4. Typography & Icons
- **Headings**: Bold, White/Cyan.
- **Body**: White (High Emphasis).
- **Icons**: Neon accented.
