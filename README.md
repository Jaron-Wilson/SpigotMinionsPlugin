# Minecraft Minion Plugin
![image](https://github.com/user-attachments/assets/b1eccbfe-743e-4fe3-b964-86487a25b07f)

A comprehensive Minecraft plugin that adds customizable, automated minions to your server. These minions can perform various tasks like mining blocks, farming crops, and more while storing collected resources for players.

## Features

### Minions System
- **Multiple Minion Types**: Currently supports Block Miners and Farmers
- **Tier System**: Minions can be upgraded through 5 tiers, each with improved:
  - Reduced action delay
  - Increased efficiency
  - Extended operational range
  - Type-specific bonuses (fortune chance, bonemeal effects, etc.)

### Minion Bundle Storage System
- **Unlimited Storage**: Store all your minion-collected items in one convenient bundle
- **Multiple Views**: 
  - Raw Items view: See all items individually
  - Categories view: Items organized by type
  - Minions view: See minions available for placement

### Automation
- **Toggle Automation**: Enable or disable all your minions with a single command
- **Collection Management**:
  - Collect all items from your minions at once
  - Delete all minions and transfer their items to your bundle
  - Partial deletion options for minion management

### Advanced Functionality
- **Block Miners**: Mine and collect blocks automatically
- **Farmers**: Plant, grow, and harvest crops automatically
  - Can enable/disable seed replanting
  - Different growth states management
  - Crop-specific functionalities

### Inventory & GUI Management
- **Custom GUIs**: Intuitive interfaces for minion management
- **Confirmation Screens**: Prevent accidental actions with confirmation dialogs
- **Visual Indicators**: Clear indicators for minion status and settings

## Commands

| Command | Description |
|---------|-------------|
| `/createminion` | Creates a new minion at your location |
| `/showminioninventory` | Shows the inventory of the nearest minion |
| `/giveminionegg` | Gives a minion spawn egg for easier placement |
| `/minionautomation <start\|stop>` | Start or stop automation for all your minions |
| `/collectall` | Collect items from all your minions at once |
| `/getbundle` | Get a Minion Collection Bundle for infinite storage |
| `/deleteallminions` | Delete all your minions and transfer their storage to your bundle |

## Minion Upgrade Tiers

Each tier of minion gains the following benefits:

### General Improvements
| Tier | Delay | Efficiency | Range |
|------|-------|-----------|-------|
| 1    | 5s    | 1.0       | 2     |
| 2    | 4s    | 1.2       | 3     |
| 3    | 3s    | 1.5       | 3     |
| 4    | 2s    | 1.8       | 4     |
| 5    | 1s    | 2.0       | 5     |

### Type-specific Bonuses

#### Block Miner
| Tier | Fortune Chance |
|------|---------------|
| 1    | 0%            |
| 2    | 10%           |
| 3    | 20%           |
| 4    | 30%           |
| 5    | 50%           |

#### Farmer
| Tier | Bonemeal Chance | Double Crops |
|------|----------------|-------------|
| 1    | 0%             | 0%          |
| 2    | 10%            | 5%          |
| 3    | 20%            | -           |

## Installation

1. Place the plugin JAR file in your server's `plugins` folder
2. Restart your server or use a plugin manager to load the plugin
3. Configure the plugin settings in the generated YAML files (optional)

## Configuration

The plugin generates configuration files:
- `minion-upgrades.yml`: Configure tier-based abilities and timers
- Other configuration files will be generated for storage and settings

## Permissions

Basic permissions are automatically assigned. Admin permissions may be configured as needed.

## Future Development

- Additional minion types
- More customization options
- Enhanced storage and GUI features

## Support

For issues, suggestions, or contributions, please reach out through the project's issue tracker.

---

Created by Jaron
