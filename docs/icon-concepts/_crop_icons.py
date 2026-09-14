from pathlib import Path
from PIL import Image

src_dir = Path(
    r"C:\Users\i1290\XiaomiMiMoProjects\.mimo-sessions\2026-09-14\我想做一个类似InstallerX Revived(不是指具体功能类似)这种解决"
)
out_dir = Path(r"C:\Users\i1290\Documents\ChatGPT\Quieta\docs\icon-concepts")
out_dir.mkdir(parents=True, exist_ok=True)

files = [
    ("generated-1789388430453.png", "v3-a-void.png"),
    ("generated-1789388435105.png", "v3-b-wave.png"),
    ("generated-1789388439436.png", "v3-c-seal.png"),
]

for src_name, dst_name in files:
    src = src_dir / src_name
    im = Image.open(src).convert("RGB")
    w, h = im.size
    side = int(min(w, h) * 0.88)
    crop = im.crop((0, 0, side, side))
    out = out_dir / dst_name
    crop.save(out, "PNG")
    print(f"{out} {crop.size}")
