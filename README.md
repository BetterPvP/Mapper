# Mapper

A powerful Bukkit/Paper plugin for creating, visualizing, and managing regions in your Minecraft world.

## Disclaimer

Mapper is not intended to replace WorldEdit or WorldGuard. It is a lightweight region definition tool designed to be used as a library for developers who need to define and manage regions in their own plugins. While it provides region visualization and management capabilities, it does not include the terrain manipulation features of WorldEdit or the protection features of WorldGuard.

Developers can integrate Mapper into their own projects to leverage its region definition system without having to implement region handling from scratch.

## Overview

Mapper allows server administrators and world editors to define various types of regions in the world - from simple points to complex cuboids and polygons. These regions can be used for defining areas of interest, creating custom features, or integrating with other plugins.

## Features

- **Multiple Region Types**
    - Point Regions
    - Perspective Regions (a point plus a facing)
    - Cuboid Regions
    - Polygon Regions
    - Path Regions (an ordered route)

- **Intuitive Tools**
    - Special tools for creating each type of region
    - Simple selection interface for cuboid regions
    - Point-and-click creation for simple regions
    - Region deletion tool
    - A command for every tool, for when you want to name a region without a prompt

- **Collaborative Editing**
    - One session per world, shared by everyone editing it
    - Owner / Editor / Viewer roles
    - Every change is shown to the other members as it happens

- **Data Management**
    - Tags, registered by consuming plugins or written free-form
    - Validation before save, extensible by consumers
    - JSON export/import
    - Persistent storage of regions

## Installation

1. Download the latest JAR file from the releases page
2. Place the JAR into your server's `plugins` folder
3. Restart your server
4. The plugin will generate necessary configuration files on first run

## Usage

### Region Creation

1. Start an editing session with `/mapper edit`
2. Use the provided tools to create regions:
    - Beacon: Point Region Creator
    - Armor Stand: Perspective Region Creator
    - Blaze Rod: Cuboid Region Wand
    - Breeze Rod: Polygon Region Wand
    - Shears: Region Deletion Tool
    - Lead: Path Region Wand
    - Book: Region Clipboard - right-click a region to copy it, sneak +
      right-click to paste it where you stand

### Creating a Cuboid Region

1. Left-click with the Blaze Rod to set the first corner
2. Right-click to set the second corner
3. Shift + Right-click to complete the region
4. Enter a name and select a color for your region

Clicking a block puts the corner on that block. Clicking into the air puts it on the block you are
standing in, so a corner can sit in mid-air without a block to aim at. The Breeze Rod's corners work
the same way.

### Picking a Region

While you hold the Shears, the Name Tag or the Book, the region under your crosshair is outlined for
you and named in your action bar. That is the region a click acts on.

- Points and path waypoints are picked from your whole line of sight, nearest first, so a datapoint
  inside a cuboid is picked before the cuboid.
- When several regions are under your crosshair, sneak and scroll to step through them.
- Deleting a cuboid or polygon that holds other regions asks for a second click within 5 seconds.
  The regions inside are not deleted with it.

### Creating a Polygon Region

1. Left-click with the Breeze Rod to set the first corner of a polygon part
2. Right-click to set the second corner
3. Shift + Right-click to add that cuboid part to the polygon
4. Repeat steps 1-3 for each additional part
5. Shift + Right-click again without an active corner pair to finalize the polygon

### Editing Together

A session belongs to a **world**, not to a player. `/mapper edit` joins the session for the world
you are standing in, opening it if nobody is editing there yet. Everyone in it shares one set of
regions and sees each other's changes immediately.

This is why the regions file is only read when the session opens: two editors loading their own
private copy of the same file is how one person's work gets silently overwritten by the other's save.

Whoever opens a session is its **Owner** and is the only one who can save or close it. Later joiners
are **Editors**. `/mapper watch` joins as a **Viewer**, who sees everything and can change nothing.
Use `/mapper role <player> <role>` to change that.

`/mapper leave` leaves the session running for everyone else; `/mapper close` ends it for all.

### Saving Your Work

`/mapper save` validates the regions and writes them to the world folder. The session stays open, so
a save is a checkpoint rather than the end of everyone else's work.

Validation errors block the save; run `/mapper save force` to write anyway. Warnings are reported
and saved. Every reported problem is clickable and teleports you to the region it is about.

## Commands

### Session

| Command                       | Description                                            |
|-------------------------------|--------------------------------------------------------|
| `/mapper edit`                | Join or start this world's session, as an editor       |
| `/mapper watch`               | Join this world's session as a viewer                  |
| `/mapper session`             | Show who is editing and how many regions are loaded    |
| `/mapper role <player> <role>`| Change a member's role (owner only)                    |
| `/mapper leave`               | Leave, leaving the session open for the others         |
| `/mapper close`               | End the session for everyone (owner only)              |
| `/mapper save [force]`        | Validate and write the regions (owner only)            |
| `/mapper validate`            | Report problems without saving                         |
| `/mapper export [strategy]`   | Export to a timestamped file. Defaults to JSON         |

### Finding regions

| Command                  | Description                                        |
|--------------------------|----------------------------------------------------|
| `/mapper list [filter]`  | Browse the session's regions, filtered by name/tag |
| `/mapper goto <region>`  | Teleport to a region                               |
| `/mapper tags <region>`  | List the tags available for a region name          |
| `/mapper pick <n>`       | Answer a "which one did you mean" prompt           |

### Creating and editing

| Command                              | Description                                        |
|--------------------------------------|----------------------------------------------------|
| `/mapper pos1` / `/mapper pos2`      | Set the cuboid corners you are looking at          |
| `/mapper cuboid [name]`              | Create a cuboid from those corners                 |
| `/mapper point [name]`               | Create a point where you stand                     |
| `/mapper perspective [name]`         | Create a point that also records your facing       |
| `/mapper polygon add\|create [name]` | Add a polygon part, or finish the polygon          |
| `/mapper path add\|undo\|create [name]` | Append, undo or finish a path                   |
| `/mapper paste [name]`               | Paste what the clipboard tool copied               |
| `/mapper cancel`                     | Clear your in-progress selection                   |
| `/mapper delete <region>`            | Delete a region by name                            |
| `/mapper tag <region>`               | Open a region's tag editor                         |
| `/mapper copy <region> [name]`       | Duplicate a region at your feet                    |
| `/mapper offset <region> <x> <y> <z>`| Move a region by an exact delta                    |
| `/mapper mirror <region> <axis>`     | Mirror a region across the plane you stand on      |

Region names are not unique on purpose - a world holds many `npc_resident` points. Any command that
names an ambiguous region lists every match with its coordinates and tags rather than guessing, and
you pick the one you meant.

## Permissions

- `mapper.use` - Allows use of the plugin

## Technical Details

Regions are stored in the world folder as `dataPoints.json`. The plugin uses Jackson for
serialization and deserialization of region data.

### For developers

The plugin provides a clean API for working with regions through the `Region` interface and
supporting classes. Three extension points are worth knowing about:

- **`TagRegistry`** - register `Tag` definitions so builders get a curated, validated vocabulary in
  the tag editor instead of typing free-form strings.
- **`ValidationRegistry`** - register a `RegionValidator` to enforce your own rules at save time.
  Mapper only knows the rules that hold for any map; that an `npc_route` needs an `order`, or that a
  `route:` must name a resident that exists, is yours to state. A problem reported here surfaces next
  to the region that caused it, rather than hours later as a stack trace during world load.
- **`RegionsSavedEvent`** - fired after a world's regions are written. Listen to it to reload
  whatever you derive from the file without restarting the server.
