#!/usr/bin/env python3
"""Generate Sleepy launcher icons + promo banner from the capsule logo spec.

Source of truth: /Users/lingion_k/Downloads/icon.jpg (capsule geometry extracted
programmatically). Rendered as vectors at 4x supersample, so every density is
pixel-clean.

Color system (keeps the repo's debug-inversion convention, see
app/src/debug/res/drawable/ic_launcher_background.xml):
  main   bg #1A1A2E  purple #9D82E8  gray  #4B4E66  (gray recessive on dark)
  debug  bg #E5E5D1  olive #627601   dark  #2F3403  (same mapping as old icons)
  light  bg #FFFFFF  purple #9176DF  gray  #C2C4D0  (faithful to icon.jpg)
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import numpy as np
import os

REPO = os.path.expanduser("~/sleepy")
SS = 4  # supersample factor

# (x, y, w, h) on the 512 source canvas, from icon.jpg component analysis
PURPLE = [
    (63, 117, 36, 118), (63, 335, 36, 60), (121, 82, 36, 198), (121, 346, 36, 72),
    (180, 59, 35, 95), (180, 220, 35, 118), (180, 404, 35, 49), (238, 48, 36, 95),
    (238, 208, 36, 96), (238, 369, 36, 95), (297, 59, 35, 49), (297, 174, 35, 118),
    (297, 358, 35, 95), (355, 232, 35, 197),
]
GRAY = [
    (355, 94, 35, 72), (414, 117, 35, 60), (414, 277, 35, 118),
]
# note: (355,232,35,197) is the tall capsule right of center; icon.jpg colors put
# it in the purple group per sampled median (194,196,208)=gray actually. Fix:
PURPLE = [c for c in PURPLE if c != (355, 232, 35, 197)]
GRAY = GRAY + [(355, 232, 35, 197)]

MARK_X0, MARK_X1 = 63, 449
MARK_Y0, MARK_Y1 = 48, 453
MCX = (MARK_X0 + MARK_X1) / 2  # mark's own bbox center, not canvas center
MCY = (MARK_Y0 + MARK_Y1) / 2


def draw_mark(draw, scale, center_xy, purple, gray, alpha=255):
    """Draw all capsules scaled about the MARK center, placed at center_xy."""
    tx, ty = center_xy
    for group, color in ((PURPLE, purple), (GRAY, gray)):
        for (x, y, w, h) in group:
            cx = (x + w / 2 - MCX) * scale + tx
            cy = (y + h / 2 - MCY) * scale + ty
            sw, sh = w * scale, h * scale
            r = min(sw, sh) / 2
            draw.rounded_rectangle(
                [cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2],
                radius=r, fill=color + (alpha,),
            )


def render_tile(size, bg, purple, gray, mark_frac=0.62, circle=False):
    """Rounded-square (or circle) app tile with centered mark."""
    n = size * SS
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if circle:
        d.ellipse([0, 0, n - 1, n - 1], fill=bg + (255,))
    else:
        r = int(n * 0.225)
        d.rounded_rectangle([0, 0, n - 1, n - 1], radius=r, fill=bg + (255,))
    span = max(MARK_X1 - MARK_X0, MARK_Y1 - MARK_Y0)
    scale = (n * mark_frac) / span
    draw_mark(d, scale, (n / 2, n / 2), purple, gray)
    return img.resize((size, size), Image.LANCZOS)


def render_foreground(size, purple, gray, mark_frac=0.52):
    """Transparent adaptive-icon foreground layer (safe zone ~66/108)."""
    n = size * SS
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    span = max(MARK_X1 - MARK_X0, MARK_Y1 - MARK_Y0)
    scale = (n * mark_frac) / span
    draw_mark(d, scale, (n / 2, n / 2), purple, gray)
    return img.resize((size, size), Image.LANCZOS)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print(f"wrote {path} {img.size}")


# ---------- colors ----------
MAIN_BG = (26, 26, 46)        # #1A1A2E
MAIN_PURPLE = (157, 130, 232)  # #9D82E8
MAIN_GRAY = (75, 78, 102)      # #4B4E66
DBG_BG = (229, 229, 209)       # #E5E5D1
DBG_PURPLE = (98, 118, 1)      # #627601
DBG_GRAY = (47, 52, 3)         # #2F3403
LIL_BG = (255, 255, 255)
LIL_PURPLE = (145, 118, 223)   # #9176DF exact from icon.jpg
LIL_GRAY = (194, 196, 208)     # #C2C4D0

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FG_DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

for flavor, (bg, pu, gr) in {
    "main": (MAIN_BG, MAIN_PURPLE, MAIN_GRAY),
    "debug": (DBG_BG, DBG_PURPLE, DBG_GRAY),
}.items():
    base = f"{REPO}/app/src/{flavor}/res"
    for dens, px in DENSITIES.items():
        save(render_tile(px, bg, pu, gr), f"{base}/mipmap-{dens}/ic_launcher.png")
        save(render_tile(px, bg, pu, gr, circle=True), f"{base}/mipmap-{dens}/ic_launcher_round.png")
    for dens, px in FG_DENSITIES.items():
        save(render_foreground(px, pu, gr), f"{base}/mipmap-{dens}/ic_launcher_foreground.png")

# ---------- brand assets ----------
save(render_tile(1024, MAIN_BG, MAIN_PURPLE, MAIN_GRAY, mark_frac=0.58), f"{REPO}/assets/logo_1024.png")
save(render_tile(432, MAIN_BG, MAIN_PURPLE, MAIN_GRAY, mark_frac=0.58), f"{REPO}/docs/logo.png")

# ---------- promo 1920x1080 ----------
def render_promo(dark=True):
    W, H = 1920 * 2, 1080 * 2  # supersample x2
    if dark:
        bg_col, glow = (26, 26, 46), (124, 92, 220)
        pu, gr, pu_soft, gr_soft = (157, 130, 232), (75, 78, 102), (157, 130, 232), (75, 78, 102)
        text_main, text_sub = (244, 242, 255), (176, 168, 210)
    else:
        bg_col, glow = (255, 255, 255), (203, 189, 246)
        pu, gr, pu_soft, gr_soft = LIL_PURPLE, LIL_GRAY, LIL_PURPLE, LIL_GRAY
        text_main, text_sub = (26, 26, 46), (122, 116, 150)

    img = Image.new("RGBA", (W, H), bg_col + (255,))
    # radial glow behind the mark cluster
    glow_layer = Image.new("L", (W, H), 0)
    gd = ImageDraw.Draw(glow_layer)
    gd.ellipse([int(W * 0.52), int(H * 0.05), int(W * 1.05), int(H * 0.95)], fill=90)
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(220))
    tint = Image.new("RGBA", (W, H), glow + (255,))
    img = Image.composite(tint, img, glow_layer)

    d = ImageDraw.Draw(img, "RGBA")

    # background capsule field: sparse, soft, out-of-focus depth
    import random
    random.seed(7)
    field = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    fd = ImageDraw.Draw(field)
    for _ in range(26):
        x = random.randint(0, W)
        y = random.randint(0, H)
        w = random.randint(26, 60)
        h = w * random.randint(2, 5)
        col = (pu_soft if random.random() < 0.62 else gr_soft) + (random.randint(26, 70),)
        fd.rounded_rectangle([x, y, x + w, y + h], radius=w / 2, fill=col)
    field = field.filter(ImageFilter.GaussianBlur(14))
    img = Image.alpha_composite(img, field)
    d = ImageDraw.Draw(img, "RGBA")

    # hero mark cluster, right side
    span = max(MARK_X1 - MARK_X0, MARK_Y1 - MARK_Y0)
    scale = (H * 0.66) / span
    draw_mark(d, scale, (W * 0.745, H * 0.50), pu, gr)

    # wordmark, left
    f_big = ImageFont.truetype("/System/Library/Fonts/SFNSRounded.ttf", 300)
    f_sub = ImageFont.truetype("/System/Library/Fonts/PingFang.ttc", 150, index=4)  # SC Semibold
    lx = int(W * 0.062)
    d.text((lx, int(H * 0.30)), "Sleepy", font=f_big, fill=text_main + (255,))
    d.text((lx + 14, int(H * 0.585)), "课程表", font=f_sub, fill=text_sub + (255,))

    return img.resize((1920, 1080), Image.LANCZOS).convert("RGB")


os.makedirs(f"{REPO}/assets/promo", exist_ok=True)
render_promo(dark=True).save(f"{REPO}/assets/promo/promo_1920x1080_dark.jpg", quality=92)
render_promo(dark=False).save(f"{REPO}/assets/promo/promo_1920x1080_light.jpg", quality=92)
print("promo saved")
