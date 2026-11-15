## Home wheel and softkeys

- Restore the shortcut crescent from the work preceding GitHub release `v1.0.0-alpha.4`: the middle icon should be roughly half visible when closed, with a slightly gentler taper so the outer icons peek through more.
- Keep the open wheel's curved path and scrolling/focus behavior.
- Ensure the expanded middle icon and its selection halo are not cut off by the rail bounds.
- Home must return focus to the grid action (and its spotlight); the collapsed rail must remain centered on its five-icon peek rather than item zero.
- Put the app-grid button between the two Yapchik labels **inside** Yapchik's bottom bar.
- Preserve unrelated local changes, including the app drawer search edits.

## Drawer and softkey follow-up

- Style the focused-app popup for both KaiOS and Material 2; keep Options on the left and Search on the right.
- In Material 2, inset the focused app-grid state slightly from the icon bounds.
- Show the current T9 letter jump in a visible overlay for a few seconds.
- Keep app search overlaid on the grid, not in the vertical layout flow.
- Keep the Home favorites wheel above Yapchik's softkey bar and prevent duplicate app-grid controls after theme changes.
- Keep Yapchik's Home background transparent without reducing the app-grid spotlight.
- Follow-up corrections: make the T9 letter feedback large and bottom-center; match KaiOS popup corner radii to settings; inset the Material 2 popup's focused row using the documented neutral/pressed focus colors.
