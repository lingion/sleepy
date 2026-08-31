#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sleepy widget 选择列表预览图生成器 (Task 7)。

为什么是 Python 而不是单测: 仓库无 Robolectric(纯 JUnit), android.graphics
管线无法在 JVM 跑 → 预览图改用本地工具生成 placeholder PNG, 产物直接入库。

用法:
    cd /Users/lingion_k/Desktop/sleepy && python3 tools/gen_widget_previews.py

输出 10 张 PNG 到 app/src/main/res/drawable-nodpi/:
    widget_preview_{today,twoday,weeklist,weekview,weekgrid}{,_small}.png
    大档 ~560x420px, 小档 280x280px (drawable-nodpi → launcher 自行缩放)。

重新生成时机: 改了配色/尺寸规格, 或新增 widget 变体时。脚本依赖 Pillow
(本机已装 11.3.0; 若 Pillow 缺失则退化为纯 zlib/struct 无文字版, 见 PILLOW 检查)。

配色取自 app/src/main/java/com/lingion/sleepy/ui/theme/Theme.kt LightScheme
(widget 渲染 WeekGridWidgetProvider 用 scheme.surface/primaryContainer/...,
动态取色下选择列表预览用中性亮色基线即可)。
"""

import os
import struct
import zlib
import sys

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable-nodpi")

# ── LightScheme 中性色 (Theme.kt LightScheme 同源) ──
SURFACE = "#FEF7FF"          # widget 背景底色
SURFACE_CONTAINER = "#F3EDF7"  # 骨架块底
SURFACE_VARIANT = "#E7E0EC"  # 描边/次级块
PRIMARY = "#6750A4"          # 强调色
PRIMARY_CONTAINER = "#EADDFF"  # 课程块填充
ON_SURFACE = "#1D1B20"       # 标题文字
ON_SURFACE_VAR = "#49454F"   # 次级文字

try:
    from PIL import Image, ImageDraw, ImageFont
    HAS_PIL = True
except ImportError:
    HAS_PIL = False

# CJK 字体: macOS 自带 PingFang; 找不到就无文字 (纯圆角矩形)
_FONT_CANDIDATES = [
    "/System/Library/Fonts/PingFang.ttc",
    "/System/Library/Fonts/Hiragino Sans GB.ttc",
    "/System/Library/Fonts/STHeiti Light.ttc",
    "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
]


def _load_font(size):
    for path in _FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                pass
    return None


def _hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def _draw_with_pil(path, w, h, label, sub, blocks):
    """Pillow 版: 圆角底 + 标题 + 课程骨架块 + 底部次级文字。
    blocks: [(x0, y0, x1, y1, fill, radius)] 相对坐标 0~1。"""
    img = Image.new("RGBA", (w, h), _hex_rgb(SURFACE) + (255,))
    d = ImageDraw.Draw(img)

    # 外框 (圆角描边, 模拟 widget 卡片)
    d.rounded_rectangle([1, 1, w - 2, h - 2], radius=28, outline=_hex_rgb(SURFACE_VARIANT), width=2)

    # 标题
    font = _load_font(max(26, w // 14))
    if font:
        d.text((36, 30), label, fill=_hex_rgb(ON_SURFACE), font=font)
        if sub:
            sfont = _load_font(max(20, w // 20))
            d.text((36, 30 + max(34, w // 11)), sub, fill=_hex_rgb(ON_SURFACE_VAR), font=sfont)

    # 课程骨架块
    for x0, y0, x1, y1, fill, radius in blocks:
        d.rounded_rectangle(
            [int(x0 * w), int(y0 * h), int(x1 * w), int(y1 * h)],
            radius=radius, fill=_hex_rgb(fill),
        )

    img.save(path, "PNG")


def _draw_fallback(path, w, h):
    """无 Pillow 兜底: 纯 zlib/struct 写纯色圆角矩形 PNG (无文字)。"""
    r, g, b = _hex_rgb(SURFACE)
    radius = min(w, h) // 12
    rows = []
    for y in range(h):
        row = bytearray([0])
        for x in range(w):
            # 圆角: 距四角超 radius 的点透明
            cx = radius if x < radius else (w - 1 - radius if x >= w - radius else x)
            cy = radius if y < radius else (h - 1 - radius if y >= h - radius else y)
            inside = (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2 or (radius <= x < w - radius) or (radius <= y < h - radius)
            if inside:
                row += bytes((r, g, b, 255))
            else:
                row += bytes((0, 0, 0, 0))
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


# (label, sub, 尺寸, 骨架块布局) — 布局按真实 widget 内容密度近似
VARIANTS = {
    "today": (
        "今日课程", None, (560, 420), [
            (0.07, 0.42, 0.93, 0.62, PRIMARY_CONTAINER, 18),
            (0.07, 0.68, 0.93, 0.88, SURFACE_CONTAINER, 18),
        ],
    ),
    "today_small": (
        "今日课程", "小", (280, 280), [
            (0.10, 0.46, 0.90, 0.90, PRIMARY_CONTAINER, 14),
        ],
    ),
    "twoday": (
        "最近两天", None, (560, 420), [
            (0.06, 0.42, 0.49, 0.92, PRIMARY_CONTAINER, 16),
            (0.54, 0.42, 0.94, 0.92, SURFACE_CONTAINER, 16),
        ],
    ),
    "twoday_small": (
        "最近两天", "小", (280, 280), [
            (0.10, 0.46, 0.90, 0.90, PRIMARY_CONTAINER, 14),
        ],
    ),
    "weeklist": (
        "本周课表", "（列表）", (560, 420), [
            (0.07, 0.44, 0.93, 0.56, PRIMARY_CONTAINER, 12),
            (0.07, 0.60, 0.93, 0.72, SURFACE_CONTAINER, 12),
            (0.07, 0.76, 0.93, 0.88, SURFACE_CONTAINER, 12),
        ],
    ),
    "weeklist_small": (
        "本周课表", "（列表）小", (280, 280), [
            (0.10, 0.50, 0.90, 0.64, PRIMARY_CONTAINER, 10),
            (0.10, 0.70, 0.90, 0.84, SURFACE_CONTAINER, 10),
        ],
    ),
    "weekview": (
        "本周课表", "（周视图）", (560, 420), [
            (0.06, 0.44, 0.24, 0.92, PRIMARY_CONTAINER, 12),
            (0.26, 0.44, 0.44, 0.92, SURFACE_CONTAINER, 12),
            (0.46, 0.44, 0.64, 0.92, SURFACE_CONTAINER, 12),
            (0.66, 0.44, 0.86, 0.92, PRIMARY_CONTAINER, 12),
            (0.88, 0.44, 0.94, 0.92, SURFACE_VARIANT, 12),
        ],
    ),
    "weekview_small": (
        "本周课表", "（周视图）小", (280, 280), [
            (0.08, 0.46, 0.28, 0.90, PRIMARY_CONTAINER, 10),
            (0.32, 0.46, 0.52, 0.90, SURFACE_CONTAINER, 10),
            (0.56, 0.46, 0.76, 0.90, PRIMARY_CONTAINER, 10),
            (0.80, 0.46, 0.92, 0.90, SURFACE_VARIANT, 10),
        ],
    ),
    "weekgrid": (
        "本周课表", "（网格）", (560, 420), [
            (0.06, 0.44, 0.48, 0.66, PRIMARY_CONTAINER, 12),
            (0.52, 0.44, 0.94, 0.66, SURFACE_CONTAINER, 12),
            (0.06, 0.70, 0.48, 0.92, SURFACE_CONTAINER, 12),
            (0.52, 0.70, 0.94, 0.92, PRIMARY_CONTAINER, 12),
        ],
    ),
    "weekgrid_small": (
        "本周课表", "（网格）小", (280, 280), [
            (0.10, 0.46, 0.52, 0.68, PRIMARY_CONTAINER, 10),
            (0.58, 0.46, 0.90, 0.68, SURFACE_CONTAINER, 10),
            (0.10, 0.72, 0.52, 0.90, SURFACE_CONTAINER, 10),
            (0.58, 0.72, 0.90, 0.90, PRIMARY_CONTAINER, 10),
        ],
    ),
}


def main():
    out = os.path.normpath(OUT_DIR)
    os.makedirs(out, exist_ok=True)
    for name, (label, sub, (w, h), blocks) in VARIANTS.items():
        path = os.path.join(out, f"widget_preview_{name}.png")
        if HAS_PIL:
            _draw_with_pil(path, w, h, label, sub, blocks)
        else:
            _draw_fallback(path, w, h)
        print(f"wrote {path} ({w}x{h})")
    print(f"done: {len(VARIANTS)} previews, PIL={HAS_PIL}")


if __name__ == "__main__":
    sys.exit(main())
