import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont
S, R = sys.argv[1], sys.argv[2]
names = [("overview.png","Overview"),("ai.png","Local AI"),("storage.png","Storage"),
         ("infra.png","Infrastructure"),("quick-settings.png","Quick Settings"),("widget-annotated.png","Widget")]
def font(size, wght):
    f = ImageFont.truetype(f"{S}/gsflex.ttf", size)
    try: f.set_variation_by_axes([min(size,144), wght])
    except Exception: pass
    return f
pw, ph = 1344, 2992; gap, pad, cap = 192, 160, 220
cols, rows = 3, 2
W = pad*2 + cols*(pw+56) + (cols-1)*gap; H = pad*2 + rows*(ph+16+cap) + (rows-1)*80
canvas = Image.new("RGBA", (W, H), (0,0,0,0))
for i,(f,label) in enumerate(names):
    c, r = i % cols, i // cols
    x = pad + c*(pw+56+gap); y = pad + r*(ph+16+cap+80)
    im = Image.open(f"{S}/orig/{f}").convert("RGB").resize((pw, ph), Image.LANCZOS)
    fr = Image.new("RGBA", (pw+56, ph+56), (0,0,0,0))
    m = Image.new("L", fr.size, 0); ImageDraw.Draw(m).rounded_rectangle((0,0,*fr.size), radius=148, fill=255)
    fr.paste(Image.new("RGBA", fr.size, (22,24,26,255)), (0,0), m)
    sm = Image.new("L", (pw,ph), 0); ImageDraw.Draw(sm).rounded_rectangle((0,0,pw,ph), radius=120, fill=255)
    fr.paste(im, (28,28), sm)
    sh = Image.new("RGBA", (fr.width+160, fr.height+160), (0,0,0,0))
    sh.paste((0,0,0,110), (80,124), fr.split()[3]); sh = sh.filter(ImageFilter.GaussianBlur(60))
    canvas.alpha_composite(sh, (x-80, y-80)); canvas.alpha_composite(fr, (x, y))
    d = ImageDraw.Draw(canvas); f2 = font(100, 600); tw = d.textlength(label, font=f2)
    # caption pill readable on both light and dark GitHub themes
    px0 = x + (pw+56-tw)/2 - 20
    d.rounded_rectangle((px0, y+ph+100, px0+tw+120, y+ph+100+152), radius=76, fill=(31,77,69,255))
    d.text((px0+60, y+ph+120), label, font=f2, fill=(236,246,242))
canvas.save(f"{R}/screenshots/gallery.png", optimize=True, compress_level=9); print(canvas.size)
