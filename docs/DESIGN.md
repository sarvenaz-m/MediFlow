# Visual system

MediFlow's visual language is quiet and legible: warm ivory surfaces, lilac accents, restrained sage and peach status colors, open spacing, and gently rounded cards. The cross-shaped mark, capsule glyphs, and flower are original vector artwork implemented in Compose Canvas and SVG.

| Token | Light value | Purpose |
| --- | --- | --- |
| Ivory | `#F8F7F3` | Main background |
| Surface | `#FFFEFC` | Cards and dialogs |
| Ink | `#302A3E` | Primary text |
| Muted | `#686171` | Secondary text |
| Primary | `#6D5795` | Actions and selected states |
| Lavender | `#EDE7F5` | Quiet emphasis |
| Sage | `#487462` | Recorded/stock status |
| Peach | `#92572F` | Stock review state |

Manrope is bundled locally with its SIL Open Font License. Regular, semibold, and bold instances are used by Android; the variable font is embedded in the companion. No font request is sent at runtime.

Android uses Material 3 semantics and touch targets. Text scales with system font size, forms scroll, and narrow rows of options scroll horizontally. The companion includes visible keyboard focus, a skip link, dialog focus trapping, an inert background while dialogs are open, reduced-motion support, and light/dark schemes.

The browser is responsive from a 320px viewport. Native wide layouts switch to a navigation rail at 840dp. The two interfaces share a design language while using their own platform interaction patterns.

Automated accessibility checks are recorded in `validation/web-journey.json`. They cover selected states, not a complete manual accessibility certification. TalkBack, large-font device behavior, and hardware keyboard navigation should also be reviewed on real devices.
