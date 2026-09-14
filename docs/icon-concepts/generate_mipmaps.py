from pathlib import Path
from PIL import Image

root = Path(r"C:\Users\i1290\Documents\ChatGPT\Quieta")
src = root / "docs" / "icon-concepts" / "SELECTED-app-icon.png"
res = root / "app" / "src" / "main" / "res"

# Adaptive foreground: center glyph on transparent, safe zone ~66%
fg_out = res / "drawable-nodpi" / "ic_launcher_foreground.png"
fg_out.parent.mkdir(parents=True, exist_ok=True)
base = Image.open(src).convert("RGBA")
# Use full selected art as adaptive foreground (already centered square)
base.save(fg_out)

# Legacy / round fallbacks at standard densities
densities = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
legacy = Image.open(src).convert("RGBA")
for name, size in densities.items():
    folder = res / f"mipmap-{name}"
    folder.mkdir(parents=True, exist_ok=True)
    img = legacy.resize((size, size), Image.LANCZOS)
    img.save(folder / "ic_launcher.png")
    img.save(folder / "ic_launcher_round.png")

print("icons written", fg_out)
