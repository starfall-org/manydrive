"""Generate ManyDrive's SVG and Android icons. Requires cairosvg (pip install cairosvg)."""
from pathlib import Path
import cairosvg

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
BRAND = ROOT / "public/brand"
BACKGROUND = "#101E32"
# A folded M: two upright storage pillars joined by a continuous ribbon.
SHAPES = [
    ("#67E8C5", "M26 76V36Q26 32 30 32H36L54 50L72 32H78Q82 32 82 36V76H68V53L54 67L40 53V76Z"),
    ("#35BCBE", "M40 40L54 54V67L40 53Z"),
    ("#70AAFF", "M54 50L72 32H78Q82 32 82 36V76H68V53L54 67Z"),
    ("#4C82E8", "M68 53L82 39V76H68Z"),
]

def svg(background=True):
    base = f'<rect width="108" height="108" rx="24" fill="{BACKGROUND}"/>' if background else ""
    paths = "".join(f'<path fill="{color}" d="{path}"/>' for color, path in SHAPES)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">{base}{paths}</svg>\n'

def vector(monochrome=False):
    shapes = SHAPES[:1] if monochrome else SHAPES
    paths = "\n".join(f'    <path android:fillColor="{"#FFFFFF" if monochrome else color}" android:pathData="{path}" />' for color, path in shapes)
    return f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n{paths}\n</vector>\n'

(BRAND / "manydrive-logo.svg").write_text(svg())
(BRAND / "manydrive-mark.svg").write_text(svg(False))
cairosvg.svg2png(bytestring=svg().encode(), write_to=str(BRAND / "manydrive-logo.png"), output_width=1024, output_height=1024)
for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    for name in ["launcher_icon", "ic_launcher"]:
        cairosvg.svg2png(bytestring=svg().encode(), write_to=str(RES / f"mipmap-{density}/{name}.png"), output_width=size, output_height=size)
(RES / "drawable/launcher_foreground.xml").write_text(vector())
(RES / "drawable/launcher_monochrome.xml").write_text(vector(True))
(RES / "drawable/launcher_background.xml").write_text(f'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="{BACKGROUND}" /></shape>\n')
for version in [26, 33]:
    mono = '\n    <monochrome android:drawable="@drawable/launcher_monochrome" />' if version == 33 else ""
    adaptive = f'<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/launcher_background" />\n    <foreground android:drawable="@drawable/launcher_foreground" />{mono}\n</adaptive-icon>\n'
    for name in ["launcher_icon", "ic_launcher"]:
        (RES / f"mipmap-anydpi-v{version}/{name}.xml").write_text(adaptive)
