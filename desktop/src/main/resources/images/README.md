# DMG Background Image

This directory contains the background image used in the macOS DMG installer.

## Creating the Background Image

The `dmg-background.png` file is generated using the Python script. To create or update it:

### Prerequisites

Install Pillow (Python imaging library):
```bash
pip3 install Pillow
```

### Generate the Image

From the project root directory, run:
```bash
python3 create_dmg_bg.py
```

Or from this directory:
```bash
python3 create_dmg_background.sh
```

This will create `dmg-background.png` with:
- Dimensions: 520x380 pixels
- Light blue gradient background
- Text: "Drag Askimo to Applications to install"
- Blue arrow pointing from left to right
- Positioned to align with the app icon (left) and Applications folder (right)

## Usage in Build

The background image is automatically used by the `createSignedDmg` Gradle task when building the macOS DMG installer. If the image is not found, the build will fall back to using a solid background color.

## Manual Creation (Alternative)

If you prefer not to use Python, you can create a 520x380 PNG image with:
- Light background color (light blue/cyan gradient recommended)
- Centered text at the top: "Drag Askimo to Applications to install"
- An arrow from position (200, 200) pointing right to (320, 200)

Save it as `dmg-background.png` in this directory.

