# MagicMirror 🧭✨

**MagicMirror** aims to add an adventure-friendly recovery mechanic to minecraft, similar to the [Magic Mirror](https://terraria.fandom.com/wiki/Magic_Mirrors) in Terraria. It uses the underutilized **Recovery Compass**, with added particle and sound effects!

## 💡 How to use
Get yourself a **recovery compass**, and right click anywhere. It will start a countdown, then teleport you back to your `/sethome`, player spawn, or world spawn; whichever is available first.

## ⚙️ Features
- **Zero configuration** - even by players. Works out of the box. Players without a set home will be warped to their respawn point or world spawn, whichever has been set.
- **Survival-focused** - prevent players from abusing teleportation using windup.
- **Immersive** - particle and sound effects.
- **Flexible** - `/sethome` command for players to set their respawn location to any location. The `item-name` setting allows you to make it such that only specifically named recovery compasses can act as magic mirrors.
- **Anti-suffocation** - checks players `/sethome` homes are free before they are teleported.
- **Configurable** - most features can be turned off.
- **Performant** - simple, optimized, doesn't do anything it doesn't need to.

## 🔧 Commands

| Command          | Description                              |
|------------------|------------------------------------------|
| `/sethome`       | Sets your personal home location.        |
| `/reloadmagicmirror`  | Reloads plugin configuration in-game.    |

## 🔧 Permissions

| Permission             | Default    | Description                              |
|------------------------|------------|------------------------------------------|
| `magicmirror.use`      | **true**   | Allows players to use the magic mirror and `/sethome` command.        |
| `magicmirror.reload`   | **op**     | Allows players to reload the config in-game.    |


## 📝 Configuration and default values
`config.yml`
```yaml
item-name: "" # If set to anything other than an empty string, the recovery compass must also be named this exact name in an anvil in order to be used.
windup: 3 # The amount of seconds the player must wait after clicking until the teleport happens. Setting this to 0 teleports the player instantly.
enable-particles: true # Enable/disable particle effects
enable-messages: false # Enable/disable countdown message when windup is ticking
enable-sounds: true # Enable/disable sounds
sound-effects-volume: 1.0 # Volume for sound effects. 1.0 is 100%
