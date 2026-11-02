import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
S, src = sys.argv[1], sys.argv[2]
im = Image.open(src).convert("RGB"); W, H = im.size; k = W / 898  # screenshot shown to me at 898 wide
def font(size, wght):
    f = ImageFont.truetype(f"{S}/gsflex.ttf", int(size)); 
    try: f.set_variation_by_axes([min(int(size), 144), wght])
    except Exception: pass
    return f
out = im.copy()
cut = int(690 * k)                       # keep status, clock, brightness and the tile row
# below the tiles: darken the blurred wallpaper into a calm panel
panel = im.crop((0, cut, W, H)).filter(ImageFilter.GaussianBlur(30))
panel = Image.blend(panel, Image.new("RGB", panel.size, (16, 20, 24)), 0.78); out.paste(panel, (0, cut))
fh = int(140 * k); top = im.crop((0, cut - fh, W, cut + fh)).convert("RGB")
target = Image.blend(im.crop((0, cut - fh, W, cut + fh)).filter(ImageFilter.GaussianBlur(30)), Image.new("RGB", (W, 2 * fh), (16, 20, 24)), 0.78)
mask = Image.linear_gradient("L").resize((W, 2 * fh))
out.paste(Image.composite(target, top, mask), (0, cut - fh))
d = ImageDraw.Draw(out)
tiles = [(32, 228, "Gaming PC", "Lit while the PC is on.", "Tap to wake it."),
         (245, 440, "AI engine", "Shows what's loaded on the GPU.", "Tap to load Strata or free the GPU."),
         (457, 652, "Snooze alerts", "Lit while alerts are snoozed.", "Tap to snooze them for 2 hours."),
         (669, 865, "DNS blocking", "Lit while Pi-hole is blocking.", "Tap to pause it for 5 minutes.")]
y = cut + int(170 * k)
d.text((int(32 * k), y), "Homelab Quick Settings tiles", font=font(40 * k, 700), fill=(236, 244, 242)); y += int(78 * k)
for x0, x1, name, l1, l2 in tiles:
    tile = im.crop((int(x0 * k), int(497 * k), int(x1 * k), int(640 * k)))
    tw = int(150 * k); th = int(tile.height * tw / tile.width); tile = tile.resize((tw, th), Image.LANCZOS)
    m = Image.new("L", tile.size, 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, *tile.size), radius=int(30 * k), fill=255)
    out.paste(tile, (int(32 * k), y), m)
    tx = int(32 * k) + tw + int(28 * k)
    d.text((tx, y + int(6 * k)), name, font=font(34 * k, 650), fill=(236, 244, 242))
    d.text((tx, y + int(52 * k)), l1, font=font(25 * k, 450), fill=(180, 200, 196))
    d.text((tx, y + int(84 * k)), l2, font=font(25 * k, 450), fill=(180, 200, 196))
    y += th + int(54 * k)
d.text((int(32 * k), y + int(10 * k)), "Every tile shows the live state when the panel opens", font=font(23 * k, 450), fill=(140, 164, 160))
d.text((int(32 * k), y + int(44 * k)), "and asks you to unlock first on the lock screen.", font=font(23 * k, 450), fill=(140, 164, 160))
out.save(sys.argv[3]); print(out.size, "legend ends at", y + int(80 * k), "of", H)
