# MCMapper Mod

A Minecraft Fabric mod for collaborative server mapping. Create, export, and share map data with the MCMapper community.

## Features

- **Grid-based Mapping** - Export maps aligned to 128x128 grid system
- **Multiple Map Modes** - Grid, Grid Fill, and Exploration views
- **Ping System** - Mark and share locations with other players
- **Live View** - See your position on the web map in real-time
- **Grid Beacon** - Visual laser beam to grid centers

## Installation

1. Install [Fabric Loader](https://fabricmc.net/)
2. Download the latest release from [Releases](../../releases)
3. Place the JAR in your `.minecraft/mods` folder

## Keybinds

| Key | Action |
|-----|--------|
| Right Alt | Open menu |
| R | Export map (while holding map) |
| G | Toggle grid beacon |
| P | Export ping at current location |
| H | Toggle UI visibility |

## Building from Source

```bash
git clone https://github.com/yourusername/mcmapper-mod
cd mcmapper-mod
./gradlew build
```

The built JAR will be in `build/libs/`

## Security Module

This mod includes `libs/mcmapper-security.jar` which contains hash verification code.

### Why is it obfuscated?

The hash function verifies that map uploads are genuine - created by actually being at the location in-game. If the algorithm was public, bad actors could flood the database with fake map data without ever joining a server.

### What does it do?

It generates a verification hash from your position, map colors, and player info. Nothing sketchy - just SHA-256 hashing of data that's already in the export file.

### Can I trust it?

- ✅ All other source code is 100% open - read it yourself
- ✅ No network calls in the security module - just math
- ✅ The mod makes NO connections except to mc-mapper.com (which you control via login)
- ✅ Security researchers can request source access for auditing

### Why not fully open source?

Same reason game anti-cheats aren't open source. Publishing the exact verification method makes it trivial to bypass.

## Contributing

Contributions are welcome! The security module is the only closed component - everything else is open for PRs.

## License

MIT License - See [LICENSE](LICENSE)
