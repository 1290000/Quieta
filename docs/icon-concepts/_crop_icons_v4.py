from pathlib import Path
from PIL import Image

src_dir = Path(
    r"C:\Users\i1290\XiaomiMiMoProjects\.mimo-sessions\2026-09-14\我想做一个类似InstallerX Revived(不是指具体功能类似)这种解决"
)
out_dir = Path(r"C:\Users\i1290\Documents\ChatGPT\Quieta\docs\icon-concepts")
out_dir.mkdir(parents=True, exist_ok=True)

files = [
    ("generated-1789388688319.png", "v4-a-bars.png"),
    ("generated-1789388694925.png", "v4-b-ripple.png"),
    ("generated-1789388701101.png", "v4-c-squircle.png"),
]

# Equal inset on all four sides keeps the centered glyph centered,
# while removing the bottom-right AI stamp area.
inset = 140

for src_name, dst_name in files:
    src = src_dir / src_name
    im = Image.open(src).convert("RGB")
    w, h = im.size
    box = (inset, inset, w - inset, h - inset)
    crop = im.crop(box)
    out = out_dir / dst_name
    crop.save(out, "PNG")
    print(f"{out} {crop.size} from {im.size}")
