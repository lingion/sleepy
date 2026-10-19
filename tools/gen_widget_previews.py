#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sleepy widget 选择器/画廊略缩图生成器 — 逐 dp 镜像 WidgetBitmapRenderers.kt。

为什么是 Python 而不是单测: 仓库无 Robolectric(纯 JUnit), android.graphics
管线无法在 JVM 跑 → 预览图改用本地工具生成 PNG, 产物直接入库。

用法:
    cd <repo-root> && python3 tools/gen_widget_previews.py

输出 13 张 PNG, 每张同时写 drawable/ 与 drawable-nodpi/ (字节一致):
    widget_preview_{today,today_small,today_wide,twoday,twoday_small,twoday_wide,
                    weekgrid,weekgrid_small,weeklist,weeklist_small,weeklist_wide,
                    weekview,weekview_small}.png
    画布 @ D=4.0, 尺寸严格 == info XML 声明 minWidth×minHeight
    (4×4=300×250→1200×1000, 2×2=110×110→440×440, 4×2=300×160→1200×640)。

字号镜像规则:
  - 大脸(4×4/4×2)逐 dp 镜像渲染器固定字号(11sp 胶囊/13dp 标题...)。
  - 2×2 小脸画布仅 110dp, 同字号占比过大 → 课名整段截成「高…」。
    预览对 5 张小脸统一乘字号缩放 k=0.75(行高同步收), 属刻意偏差:
    略缩图要「一眼读得清」, 真机上小 widget 仍按渲染器原字号绘制。

契约闸: app/src/test/.../WidgetPreviewFacesContractTest.kt 锁
    ① previewLayout 指向专属带 src 布局 ② 双目录同字节 ③ 像素比例==声明±2%
    ④ 画廊 previewImageRes 名集 == 启动器 previewImage 名集。
改配色/尺寸/新增变体后必须重跑本脚本再提交, 否则契约测试红。

依赖 Pillow。字体 PingFang.ttc (macOS); 缺字体退化为系统默认 (仅影响观感)。
规范依据: developer.android.com/develop/ui/views/appwidgets/previews
  + /design/ui/mobile/guides/widgets/{sizing,style,discovery-promotion}。
本脚本逻辑与 WidgetBitmapRenderers.kt @ origin/main 同源。
"""
# Spec basis: developer.android.com/develop/ui/views/appwidgets/previews
#   + /design/ui/mobile/guides/widgets/{sizing,style,discovery-promotion}
#   → preview must mirror the actual widget layout with realistic sample content,
#     accurate to the widget's targetCell size.
from PIL import Image, ImageDraw, ImageFont
import colorsys, math, os

D = 4.0   # density: 出图分辨率 = dp*4, 启动器缩到显示尺寸后仍锐利(275px 级显示 ≈ 2x)
FS = 1.0  # 字号缩放: 2×2 小脸置 0.75(见 docstring), 大脸保持 1.0 逐 dp 镜像
# 双输出目录: drawable(供 mdpi 基准/OEM 回退) + drawable-nodpi(密度无关)
# 两显示位(应用内画廊 painterResource / 启动器 previewImage+previewLayout)同源同图。
REPO = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
OUT_DIRS = [os.path.join(REPO, "app/src/main/res/drawable"),
            os.path.join(REPO, "app/src/main/res/drawable-nodpi")]

# ── M3 baseline light scheme (Theme.kt Default preset, ThemePresets.KEY_DEFAULT) ──
BG          = (0xFE,0xF7,0xFF)
PRIMARY     = (0x67,0x50,0xA4)
PCONTAINER  = (0xEA,0xDD,0xFF)
OPCONTAINER = (0x21,0x00,0x5D)
SCONTAINER  = (0xE8,0xDE,0xF8)
ONSURFACE   = (0x1D,0x1B,0x20)
ONSURFVAR   = (0x49,0x45,0x4F)
SCONT       = (0xF3,0xED,0xF7)
SCLOWEST    = (0xFF,0xFF,0xFF)
SVARIANT    = (0xE7,0xE0,0xEC)
OUTLINEVAR  = (0xCA,0xC4,0xD0)

def alpha(c, a): return (c[0], c[1], c[2], a)

# ── fonts (PingFang SC ≈ Noto Sans CJK on device) ──
FP = "/System/Library/Fonts/PingFang.ttc"
def f(sp, bold=False):
    idx = 8 if bold else 2  # SC Semibold / SC Regular
    return ImageFont.truetype(FP, max(int(sp*FS*D), 8), index=idx)

def course_color(hue, s=0.55, l=0.82):
    r,g,b = colorsys.hls_to_rgb(hue/360.0, l, s)
    return (int(r*255), int(g*255), int(b*255))

def text_on(bg):
    lum = 0.299*bg[0]+0.587*bg[1]+0.114*bg[2]
    return ONSURFACE if lum > 150 else (0xFF,0xFF,0xFF)

# ── sample timetable ──
# (name, startNode, step, room, hue)
WEEK = {
 1: [("高等数学",1,2,"A305",280), ("英文阅读",3,2,"B201",60), ("线性代数",5,2,"A305",150)],
 2: [("高等数学",1,2,"A305",280), ("英文阅读",3,2,"B201",60), ("体育",5,2,"体育馆",225), ("程序设计实践",7,2,"C102",320)],
 3: [("线性代数",1,2,"A305",150), ("研讨课",3,1,"D203",30), ("程序设计实践",4,2,"C102",320)],
 4: [("高等数学",2,2,"A305",280), ("英文阅读",5,2,"B201",60)],
 5: [("线性代数",1,2,"A305",150), ("研讨课",3,1,"D203",30), ("体育",4,2,"体育馆",225)],
 6: [],
 7: [],
}
DAYS = {1:"Mon",2:"Tue",3:"Wed",4:"Thu",5:"Fri",6:"Sat",7:"Sun"}
DAYS_CN = {1:"周一",2:"周二",3:"周三",4:"周四",5:"周五",6:"周六",7:"周日"}
DATES = {1:"10/19",2:"10/20",3:"10/21",4:"10/22",5:"10/23",6:"10/24",7:"10/25"}
TODAY = 2  # Tue 10/20

def canvas(wdp, hdp):
    img = Image.new("RGBA", (int(wdp*D), int(hdp*D)), (0,0,0,0))
    dr = ImageDraw.Draw(img)
    r = 20*D
    dr.rounded_rectangle([0,0,img.width-1,img.height-1], radius=r, fill=BG)
    dr.rounded_rectangle([0,0,img.width-1,img.height-1], radius=r, outline=OUTLINEVAR, width=2)
    return img, dr

def baseline(dr, font, y_top):
    asc, _ = font.getmetrics()
    return y_top + asc

def draw_capsule(dr, x, y, w, h, name, meta, bg, fs=11.0):
    """mirrors drawCourse(): radius 8dp, pad 3dp, bold name + (fs-2) meta, v-centered block
    课名/meta 一律按胶囊内宽 ellipsize —— 2×2 小脸胶囊窄, 不截断控制会溢出卡面"""
    dr.rounded_rectangle([x,y,x+w,y+h], radius=8*FS*D, fill=bg)
    fg = text_on(bg)
    pad = 3*D
    fn, fm = f(fs, True), f(fs-2, False)
    an,_ = fn.getmetrics(); am,_ = fm.getmetrics()
    nameH = an + _  # ascent+descent
    lineGap = 2*D
    totalH = nameH + (am + lineGap if meta else 0)
    top = y + (h - totalH)/2
    innerW = w - pad*2
    dr.text((x+pad, top), ellipsize(dr, name, fn, innerW), font=fn, fill=fg, anchor="la")
    if meta:
        dr.text((x+pad, top+nameH+lineGap), ellipsize(dr, meta, fm, innerW), font=fm, fill=fg, anchor="la")

def ellipsize(dr, text, font, maxw):
    if dr.textlength(text, font=font) <= maxw: return text
    n = text
    while n and dr.textlength(n+"…", font=font) > maxw: n = n[:-1]
    return n + "…"

# ════════════ TODAY family (renderTodayRegular) ════════════
def today_face(wdp, hdp, n_rows, header=True):
    img, dr = canvas(wdp, hdp)
    pad = 10*D
    y = pad
    if header:
        ft = f(13, True)
        fd = f(12, False)
        date = "10月20日"
        rw = dr.textlength(date, font=fd)
        # mirror renderTodayRegular: title ellipsized against right-slot width
        maxTitleW = wdp*D - pad - (rw + 6*D) - pad
        title = ellipsize(dr, "今天 · 周二", ft, maxTitleW)
        dr.text((pad, y+13*FS*D), title, font=ft, fill=PRIMARY, anchor="ls")
        dr.text((wdp*D-pad, y+13*FS*D), date, font=fd, fill=ONSURFVAR, anchor="rs")
        y += 20*FS*D
    rowH, rowGap = 30*FS*D, 4*D
    rowW = wdp*D - pad*2
    for i in range(n_rows):
        c = WEEK[2][i % len(WEEK[2])]
        name, sn, step, room, hue = c
        draw_capsule(dr, pad, y+i*(rowH+rowGap), rowW, rowH,
                     name, f"{sn}-{sn+step-1}节 · {room}", course_color(hue))
    return img


def save(img, name):
    """write the same PNG bytes into both drawable dirs (contract: byte-identical)."""
    import io
    buf = io.BytesIO(); img.save(buf, "PNG")
    data = buf.getvalue()
    for d in OUT_DIRS:
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, name), "wb") as fh:
            fh.write(data)

def with_small_fs(build):
    """2×2 小脸: 临时把字号/纵向几何缩放置 0.75 再绘制(见 docstring「字号镜像规则」)"""
    global FS
    FS = 0.75
    try:
        return build()
    finally:
        FS = 1.0

save(today_face(300,250,4), "widget_preview_today.png")
save(with_small_fs(lambda: today_face(110,110,2)), "widget_preview_today_small.png")
save(today_face(300,160,3), "widget_preview_today_wide.png")

# ════════════ TWODAY family (renderTwoDayRegular) ════════════
def twoday_face(wdp, hdp, fs, n0, n1):
    img, dr = canvas(wdp, hdp)
    pad = 12*D
    gap = 10*D
    colW = (wdp*D - pad*2 - gap)/2
    cols = [(2,"今天",n0),(3,"明天",n1)]
    for ci,(dow,label,cnt) in enumerate(cols):
        cx = pad + ci*(colW+gap)
        ft = f(12 if wdp>=250 else 10, True)
        dr.text((cx, pad+12*FS*D), label, font=ft, fill=PRIMARY, anchor="ls")
        tw = dr.textlength(label, font=ft)
        fd = f(10 if wdp>=250 else 8, False)
        dstr = DATES[dow]
        # narrow columns: only draw date if it fits the column width
        if tw + 6*D + dr.textlength(dstr, font=fd) <= colW:
            dr.text((cx+tw+6*D, pad+12*FS*D), dstr, font=fd, fill=ONSURFVAR, anchor="ls")
        cy = pad + 20*FS*D
        rowH, rowGap = (36 if wdp>=250 else 30)*FS*D, 6*D
        for i in range(cnt):
            c = WEEK[dow][i % len(WEEK[dow])]
            name, sn, step, room, hue = c
            draw_capsule(dr, cx, cy+i*(rowH+rowGap), colW, rowH,
                         name, f"{sn}-{sn+step-1}节 · {room}", course_color(hue), fs=fs)
        # column divider
        if ci == 0:
            sx = cx+colW+gap/2
            dr.line([(sx,pad),(sx,hdp*D-pad)], fill=alpha(ONSURFVAR,32), width=1)
    return img

save(twoday_face(300,250,10.0,4,3), "widget_preview_twoday.png")
save(with_small_fs(lambda: twoday_face(110,110,8.0,2,2)), "widget_preview_twoday_small.png")
save(twoday_face(300,160,9.0,2,2), "widget_preview_twoday_wide.png")

# ════════════ WEEKLIST family (renderWeekListRegular, capsule rows) ════════════
def weeklist_face(wdp, hdp, dows, chip=True):
    img, dr = canvas(wdp, hdp)
    outer = 6*D; gap = 4*D
    innerW = wdp*D - outer*2
    colW = (innerW - gap*(len(dows)-1))/len(dows)
    for i,dow in enumerate(dows):
        x = outer + i*(colW+gap)
        isT = dow == TODAY
        card = PCONTAINER if isT else SCONT
        dr.rounded_rectangle([x, outer, x+colW, hdp*D-outer], radius=14*D, fill=card)
        cy = outer + 12*FS*D
        ft = f(12, True)
        fg = OPCONTAINER if isT else ONSURFACE
        dr.text((x+colW/2, cy), DAYS[dow], font=ft, fill=fg, anchor="ms")
        cy += 14*FS*D
        courses = WEEK[dow]
        if not courses: continue
        if chip:
            chipText = f"{len(courses)} 门"
            chipW = (len(chipText)*6+12)*FS*D; chipH = 14*FS*D
            dr.rounded_rectangle([x+(colW-chipW)/2, cy, x+(colW+chipW)/2, cy+chipH], radius=chipH/2, fill=SVARIANT)
            fc = f(9, False)
            dr.text((x+colW/2, cy+chipH/2), chipText, font=fc, fill=ONSURFVAR, anchor="mm")
            cy += chipH + 6*FS*D
        fr = f(9, False)
        rowH, rowGap, cpad = 16*FS*D, 3*D, 3*D
        for (name,sn,step,room,hue) in courses:
            bg = course_color(hue)
            dr.rounded_rectangle([x+cpad, cy, x+colW-cpad, cy+rowH], radius=4*D, fill=bg)
            disp = ellipsize(dr, name, f(9,True), colW-cpad*2-4*D)
            fn = f(9, True)
            a,_ = fn.getmetrics()
            dr.text((x+cpad+2*D, cy+rowH/2), disp, font=fn, fill=text_on(bg), anchor="lm")
            cy += rowH + rowGap
    return img

save(weeklist_face(300,250,[1,2,3,4,5,6,7]), "widget_preview_weeklist.png")
save(with_small_fs(lambda: weeklist_face(110,110,[2,3,4])), "widget_preview_weeklist_small.png")
save(weeklist_face(300,160,[1,2,3,4,5,6,7]), "widget_preview_weeklist_wide.png")

# ════════════ WEEKVIEW family (renderWeekViewRegular, plain-text rows + separators) ════════════
def weekview_face(wdp, hdp, dows):
    img, dr = canvas(wdp, hdp)
    outer = 6*D; gap = 4*D
    innerW = wdp*D - outer*2
    colW = (innerW - gap*(len(dows)-1))/len(dows)
    for i,dow in enumerate(dows):
        x = outer + i*(colW+gap)
        isT = dow == TODAY
        card = PCONTAINER if isT else SCONT
        dr.rounded_rectangle([x, outer, x+colW, hdp*D-outer], radius=14*D, fill=card)
        cy = outer + 12*FS*D
        ft = f(12, True)
        dr.text((x+colW/2, cy), DAYS[dow], font=ft, fill=(OPCONTAINER if isT else ONSURFACE), anchor="ms")
        cy += 14*FS*D
        courses = WEEK[dow]
        if not courses: continue
        chipText = f"{len(courses)} 门"
        chipW = (len(chipText)*6+12)*FS*D; chipH = 14*FS*D
        dr.rounded_rectangle([x+(colW-chipW)/2, cy, x+(colW+chipW)/2, cy+chipH], radius=chipH/2, fill=SVARIANT)
        dr.text((x+colW/2, cy+chipH/2), chipText, font=f(9,False), fill=ONSURFVAR, anchor="mm")
        cy += chipH + 4*FS*D
        fr = f(9, False)
        a,d = fr.getmetrics(); lineH = a+d
        textPad = 4*D
        fg = alpha(OPCONTAINER,209) if isT else ONSURFVAR
        shown = courses[:3 if wdp<200 else 5]
        for j,(name,sn,step,room,hue) in enumerate(shown):
            disp = ellipsize(dr, name, fr, colW-textPad*2)
            dr.text((x+textPad, cy+a), disp, font=fr, fill=fg, anchor="ls")
            cy += lineH
            if j < len(shown)-1:
                cy += 1.5*D
                dr.line([(x+textPad,cy),(x+colW-textPad,cy)], fill=alpha(ONSURFVAR,102), width=1)
                cy += 1.5*D
            else:
                cy += 3*D
    return img

save(weekview_face(300,250,[1,2,3,4,5,6,7]), "widget_preview_weekview.png")
save(with_small_fs(lambda: weekview_face(110,110,[2,3,4])), "widget_preview_weekview_small.png")

# ════════════ WEEKGRID family (WeekGridWidgetProvider.renderBitmap) ════════════
def weekgrid_face(wdp, hdp):
    img, dr = canvas(wdp, hdp)
    outer = 6*D; headH = 56*D; timeW = 40*D; gapH = 1.5*D; gapW = 2.5*D
    bodyW = wdp*D - outer*2
    dayW = (bodyW - timeW - gapW*8)/7
    maxNode = 8
    bodyH = hdp*D - outer*2 - headH
    slotH = (bodyH - gapH*(maxNode+1))/maxNode
    # corner cell + day headers
    dr.rounded_rectangle([outer, outer, outer+timeW, outer+headH], radius=14*D, fill=SCONT)
    for idx,dow in enumerate([1,2,3,4,5,6,7]):
        cx0 = outer + timeW + gapW + idx*(dayW+gapW)
        isT = dow == TODAY
        dr.rounded_rectangle([cx0, outer, cx0+dayW, outer+headH], radius=14*D, fill=(PCONTAINER if isT else SCONT))
        ft = f(13 if dayW>34*D else 10, True)
        dr.text((cx0+dayW/2, outer+headH*0.40), DAYS_CN[dow], font=ft, fill=(PRIMARY if isT else ONSURFACE), anchor="ms")
        dr.text((cx0+dayW/2, outer+headH*0.72), DATES[dow], font=f(9,False), fill=ONSURFVAR, anchor="ms")
    bodyTop = outer + headH
    # today tint band
    ti = [1,2,3,4,5,6,7].index(TODAY)
    tx0 = outer + timeW + gapW + ti*(dayW+gapW)
    dr.rectangle([tx0, bodyTop, tx0+dayW, bodyTop+bodyH], fill=alpha(PCONTAINER,40))
    # grid lines
    gl = alpha(SVARIANT,90)
    for idx in range(8):
        lx = outer + timeW + gapW + idx*(dayW+gapW) - gapW/2
        if 0 < idx <= 7:
            dr.line([(lx, bodyTop),(lx, bodyTop+bodyH)], fill=gl, width=1)
    for row in range(maxNode+1):
        ry = bodyTop + gapH + row*(slotH+gapH) - gapH/2
        dr.line([(outer+timeW, ry),(wdp*D-outer, ry)], fill=gl, width=1)
    # time labels
    for row in range(maxNode):
        ry = bodyTop + gapH + row*(slotH+gapH)
        dr.text((outer+timeW-4*D, ry+slotH/2), f"{row+1}", font=f(9,False), fill=ONSURFVAR, anchor="rm")
    # course cards
    for idx,dow in enumerate([1,2,3,4,5,6,7]):
        cx0 = outer + timeW + gapW + idx*(dayW+gapW)
        for (name,sn,step,room,hue) in WEEK[dow]:
            y0 = bodyTop + gapH + (sn-1)*(slotH+gapH)
            hgt = step*slotH + (step-1)*gapH
            bg = course_color(hue)
            dr.rounded_rectangle([cx0+1, y0, cx0+dayW-1, y0+hgt], radius=6*D, fill=bg)
            fg = text_on(bg)
            fn = f(9 if dayW>34*D else 8, True)
            if hgt > 40*D:
                dr.text((cx0+dayW/2, y0+hgt/2-6*D), ellipsize(dr,name,fn,dayW-6*D), font=fn, fill=fg, anchor="mm")
                dr.text((cx0+dayW/2, y0+hgt/2+7*D), room, font=f(8,False), fill=fg, anchor="mm")
            else:
                dr.text((cx0+dayW/2, y0+hgt/2), ellipsize(dr,name,fn,dayW-6*D), font=fn, fill=fg, anchor="mm")
    return img

save(weekgrid_face(300,250), "widget_preview_weekgrid.png")
# weekgrid small = today face @2x2 (weekGridMinimumTodayData → renderToday, same pixels)
save(with_small_fs(lambda: today_face(110,110,2)), "widget_preview_weekgrid_small.png")

names = sorted(f for f in os.listdir(OUT_DIRS[0]) if f.startswith("widget_preview_"))
print("done:", len(names), "previews ×", len(OUT_DIRS), "dirs")
for fn in names:
    p = os.path.join(OUT_DIRS[0], fn)
    im = Image.open(p)
    print(f"  {fn:40s} {im.width}x{im.height}  ar={im.width/im.height:.3f}  {os.path.getsize(p)//1024}KB")
