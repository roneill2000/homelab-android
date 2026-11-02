import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
S, src, dst = sys.argv[1], sys.argv[2], sys.argv[3]
im = Image.open(src).convert("RGB"); W, H = im.size; k = W / 898
def font(size, wght):
    f = ImageFont.truetype(f"{S}/gsflex.ttf", int(size))
    try: f.set_variation_by_axes([min(int(size), 144), wght])
    except Exception: pass
    return f
out = im.copy(); cut = int(1600 * k)          # keep status bar and the whole widget, replace the dock and search bar
fh = int(60 * k)
panel_src = im.crop((0, cut - fh, W, H)).filter(ImageFilter.GaussianBlur(34))
panel = Image.blend(panel_src, Image.new("RGB", panel_src.size, (16, 20, 24)), 0.82)
mask = Image.new("L", panel.size, 255); md = ImageDraw.Draw(mask)
for y in range(fh): md.line((0, y, W, y), fill=int(255 * y / fh))
out.paste(panel, (0, cut - fh), mask)
d = ImageDraw.Draw(out); x = int(40 * k); y = cut + int(28 * k)
d.text((x, y), "Home-screen widget", font=font(38 * k, 700), fill=(236, 244, 242)); y += int(62 * k)
for line in ["Gaming PC first: asleep, idle, model loaded or generating,",
             "with GPU power, temperature, load and VRAM.",
             "Then downloads and a problems line.",
             "Four sizes · refreshes every 15 minutes · keeps the",
             "last reading, marked Away, when you're not home."]:
    d.text((x, y), line, font=font(25 * k, 450), fill=(185, 204, 200)); y += int(36 * k)
out.save(dst); print(out.size, "text ends", y, "of", H)
