# Chat Overlay — RuneLite Plugin

Splits the OSRS chatbox into draggable, resizable, and independently configurable overlays.

![Alt text](./images/example_image.png "optional title")
![active chat bubble and peek mode](https://github.com/user-attachments/assets/88545b7e-88af-419d-8b74-91248f8af5d6)
![game messages example](https://github.com/user-attachments/assets/7f87dc68-15d8-4018-b2d4-730a734e8368)
![chat overlay example gif](https://github.com/user-attachments/assets/fb22d90d-311a-4015-9b6d-16949d7bd168)

## Features

### Main Chat overlay
- Shows **Public**, **Clan** (including GIM and guest clan), and **Friends Chat** messages
- Optional **Show Private Chat** toggle — when enabled, PMs appear here too (great for consolidating everything into one overlay)
- Optional **Show Game Chat** toggle — routes game/system messages into this overlay in addition to the dedicated Game Chat overlay
- Bubble-style layout with sender name and message body in OSRS colors
- Configurable width, background color, message duration, and max message count
- Optional `[HH:MM]` timestamp prefix per message
- Messages fade out after a configurable duration; set to 0 to keep them indefinitely

### Private Chat overlay
- Separate overlay for **incoming** (`From PlayerName`) and **outgoing** (`To PlayerName`) PMs
- Positioned above the chatbox by default, out of the way of Main Chat
- Same bubble rendering as Main Chat, with its own width, background, and duration settings
- Can be combined with Main Chat via the **Show Private Chat** toggle in Main Chat settings

### Game Chat overlay
- Shows **game messages**, **engine messages**, **broadcasts**, **notifications**, and **welcome messages**
- Respects the in-game **Game chat filter setting**: when set to Filter, noisy/spam-type messages are hidden exactly as they would be in the chatbox; when set to Off, the overlay shows nothing
- Two display modes:
  - **Pinned to Player** — bubbles float above your character, always in view
  - **Free Overlay** — a draggable panel you can place anywhere
- Messages auto-expire after a configurable duration (default 4 seconds)
- **Cooldown deduplication** — with Filter Game Chat Patterns enabled and Game chat collapsing disabled, identical messages within a configurable window (default 3 s) are shown only once
- Optional `[HH:MM]` or `[HH:MM:SS]` timestamp prefix

### Message Filtering
- **Game Chat** has its own category filters, **Game Chat Patterns**, and duplicate cooldown
- **Use Game Chat Filters**, directly below Main Chat's **Enable Filtering**, optionally applies all Game Chat filters to game messages in Main; off by default. It works independently of Main's **Enable Filtering**, adds to any enabled Main Filter patterns, and works even when the dedicated Game overlay is disabled
- **Filter Examine Messages** in Game Chat settings hides item, NPC, and object examines; off by default. Main inherits this only with **Use Game Chat Filters** enabled
- Hover over Game category filters to see every matching pattern, with each pattern on its own line. Built-in status filters exclude player chat, examines, friend activity, and broadcasts
- Add unwanted Game messages to **Game Chat Patterns**, for example `your inventory is full,your inventory is too full,you already have full prayer points`. Keep **Filter Game Chat Patterns** enabled to use them
- **Main Chat**, **Private Chat**, and **Clan Chat** each have an optional **Enable Filtering** checkbox and **Filter** box, empty and disabled by default
- Each overlay's Filter applies only to messages displayed there. Private/Clan messages merged into Main use Main's Filter; Game messages in Main use Main's optional Filter, plus Game's rules only when **Use Game Chat Filters** is enabled
- Enter one pattern per line or separate patterns with commas. Matching ignores formatting tags and letter case. Plain patterns match substrings; patterns containing `*` match the whole message, with `*` matching any text (for example, `*spam*`)
- Changes apply immediately to retained messages, including Peek Mode. Filters affect visibility; messages remain in history until the normal history count limit or a history clear

### Text Styling & Accents
- Choose between multiple text styling options: **Standard**, **Shadow**, **Outline**, or **Outline + Shadow**
- **Special Pixel Font Optimization**: Custom layout metrics applied to the **RuneScape Small** font when using outline rendering to ensure text remains crisp, clean, and perfectly legible

### Peek Mode
- Hold a configurable hotkey (default: **Alt**) to instantly reveal all faded or expired messages at full opacity across every overlay
- A border is drawn around all bubbles while peeking so you can tell the mode is active; customize **Peek Border Color** directly below **Peek Mode** in General settings (amber by default)
- Messages are never pruned by time — only by count — so they are always available to peek at

### Player Icons
- Ironman and J-Mod crown icons are automatically displayed between the timestamp and sender name
- Layout: `[HH:MM] [icon] Username: message`
- Icons are sourced from the game client and alpha-fade with the bubble

### Bubble Borders
- Each overlay supports an optional 1 px rounded border around every chat bubble
- Independently toggleable per overlay with its own color picker
- Overrides to the configured **Peek Border Color** automatically while Peek Mode is active

### General behavior
- **In-Game Chat Filter Sync** — The overlays automatically synchronize with your active in-game chat box filter buttons (Public, Private, Clan, Friends Chat, Game) using robust real-time widget checks. If a filter is turned off or hidden in-game, the overlay immediately hides it
- **Right-Click Overlay Toggles** — Right-click the in-game chat box tab buttons (**Game, Public, Clan, Private**) to instantly show/hide their respective overlays (`Chat Overlay: Show` or `Chat Overlay: Hide`)
- **Clear history sync** — Right-clicking a chat tab in-game and selecting "Clear history" also clears that tab's overlay
- **Login / world-hop clear** — All overlays clear automatically on logout or world switch
- **Hide when chatbox visible** — Optionally hide each overlay while the standard chatbox is open
- **Typing preview** — A live bubble under Main Chat shows what you are currently typing in the chatbox
