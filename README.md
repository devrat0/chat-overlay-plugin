# Chat Overlay — RuneLite Plugin

Splits the OSRS chatbox into three independent overlays, each draggable and independently configurable.

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
- **Spam filter** — suppresses repetitive messages matching configurable patterns
- **Cooldown deduplication** — identical messages within a configurable window (default 3 s) are shown only once
- Optional `[HH:MM]` timestamp prefix

### Peek Mode
- Hold a configurable hotkey (default: **Alt**) to instantly reveal all faded or expired messages at full opacity across every overlay
- An **amber border** is drawn around all bubbles while peeking so you can tell the mode is active
- Messages are never pruned by time — only by count — so they are always available to peek at

### Player Icons
- Ironman and J-Mod crown icons are automatically displayed between the timestamp and sender name
- Layout: `[HH:MM] [icon] Username: message`
- Icons are sourced from the game client and alpha-fade with the bubble

### Bubble Borders
- Each overlay supports an optional 1 px rounded border around every chat bubble
- Independently toggleable per overlay with its own color picker
- Overrides to amber automatically while Peek Mode is active

### General behavior
- **Chat filter sync** — the Game Chat overlay reads the same filter varbit OSRS uses, so the overlay always matches what the chatbox would show
- **Clear history sync** — right-clicking a chat tab in-game and selecting "Clear history" also clears that tab's overlay
- **Login / world-hop clear** — all overlays clear automatically on logout or world switch
- **Hide when chatbox visible** — optionally hide each overlay while the standard chatbox is open
- **Typing preview** — a live bubble under Main Chat shows what you are currently typing in the chatbox

---

## Configuration

Open **Plugin Panel → Chat Overlay** (wrench icon, search "Chat Overlay").

### General
| Setting | Default | Description |
|---|---|---|
| Bubble Padding (Horizontal) | 3px | Left/right padding inside each bubble |
| Bubble Padding (Vertical) | 3px | Top/bottom padding inside each bubble |
| Bubble Spacing | 2px | Gap between consecutive bubbles |
| Font | RuneScape | Font used across all overlays |
| Font Size | 15 | Size in points |
| Show Chatbox Message | On | Live bubble showing what you are currently typing |
| Peek Mode | On | Hold the peek key to reveal all faded messages |
| Peek Key | Alt | Hotkey to hold for peek |
| Show Player Icons | On | Display Ironman / J-Mod icons next to sender names |

### Main Chat
| Setting | Default | Description |
|---|---|---|
| Hide When Chatbox Visible | On | Hide this overlay while the chatbox is open |
| Word Wrap | On | Wrap long messages across multiple lines |
| Enable Fading Messages | On | Messages fade out before expiring |
| Show Public Chat | On | Public player messages |
| Show Clan Chat | On | Clan and GIM clan messages |
| Show Friends Chat | On | Friends Chat (FC) messages |
| Show Private Chat | Off | Also show PMs in this overlay |
| Show Game Chat | Off | Also route game/system messages into this overlay |
| Overlay Width | 400px | Range 200–800px |
| Background Color | Dark gray 78% | RGBA picker |
| Show Background | On | Toggle background off for a transparent look |
| Show Bubble Border | Off | Draw a 1px rounded border around each bubble |
| Bubble Border Color | `#B4343434` | Color of the bubble border |
| Message Duration | 60s | Seconds before a message fades (0 = never) |
| Max Messages | 10 | Maximum messages shown at once |
| Show Timestamp | On | Prefix each message with `[HH:MM]` |

### Private Chat
| Setting | Default | Description |
|---|---|---|
| Hide When Chatbox Visible | On | Hide this overlay while the chatbox is open |
| Word Wrap | On | Wrap long messages across multiple lines |
| Enable Fading Messages | On | Messages fade out before expiring |
| Show Private Chat | On | Toggle the private chat overlay |
| Max Messages | 5 | Maximum PMs shown at once |
| Background Color | Dark gray 78% | RGBA picker |
| Show Background | On | Toggle background |
| Show Bubble Border | Off | Draw a 1px rounded border around each bubble |
| Bubble Border Color | `#B4343434` | Color of the bubble border |
| Overlay Width | 400px | Range 200–800px |
| Message Duration | 120s | Seconds before a PM fades (0 = never) |
| Show Timestamp | On | Prefix each message with `[HH:MM]` |

### Game Chat
| Setting | Default | Description |
|---|---|---|
| Hide When Chatbox Visible | Off | Hide this overlay while the chatbox is open |
| Word Wrap | Off | Wrap long messages across multiple lines |
| Enable Fading Messages | On | Messages fade out before expiring |
| Overlay Mode | Pinned to Player | Pinned to Player or Free Overlay |
| Show Game Chat | On | Toggle the game chat overlay |
| Message Duration | 4s | Range 1–15 seconds |
| Max Visible Messages | 3 | Range 1–8 |
| Background Color | Dark gray 78% | RGBA picker |
| Show Bubble Border | Off | Draw a 1px rounded border around each bubble |
| Bubble Border Color | `#B4343434` | Color of the bubble border |
| Filter Spam | On | Suppress messages matching the patterns below |
| Spam Patterns | (see below) | Comma-separated patterns to filter |
| Spam Cooldown | 3s | Min seconds between identical messages (0 = allow all) |
| Show Level-Up Alerts | On | Show level-up messages |
| Show Loot/Drop Alerts | On | Show drop/loot messages |
| Show Timestamp | Off | Prefix each message with `[HH:MM]` |

**Default spam patterns:** `you can't reach that`, `i can't reach that`, `nothing interesting happens`, `you can't do that right now`, `please finish what you're doing`, `you need to be closer`, `you can't use that here`

**Pattern matching rules:**
- If a pattern contains `*` anywhere, it is treated as a **wildcard** — `*` matches any sequence of characters (e.g. `you*reach` matches "you can't reach that", `*reach*` matches anything containing "reach")
- Otherwise the pattern is a plain **substring** match (case-insensitive)

--- 
