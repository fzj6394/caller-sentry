"""把 AI 生成的图标加工成安卓各分辨率的启动图标。

要点：AI 图是纯白底，直接贴在纯白画布上天然无缝，不需要抠图或圆形遮罩；
投影用"非白像素"当剪影自己生成，这样玻璃盾牌的轮廓能完整保留。
"""
from PIL import Image, ImageDraw, ImageFilter
import os

SRC = "icon-src/iOS_26_liquid_glass_app_icon___2026-11-18T05-00-37.png"
OUT_RES = "res"


def content_bbox(im, thr=250):
    g = im.convert("L")
    w, h = g.size
    px = g.load()
    minx, miny, maxx, maxy = w, h, 0, 0
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            if px[x, y] < thr:
                if x < minx: minx = x
                if y < miny: miny = y
                if x > maxx: maxx = x
                if y > maxy: maxy = y
    return minx, miny, maxx, maxy


def silhouette(im):
    """非白像素 → 剪影，用作投影的形状"""
    g = im.convert("L")
    return g.point(lambda v: max(0, min(255, (250 - v) * 5)))


def fit(im, box_w, box_h):
    ratio = min(box_w / im.width, box_h / im.height)
    return im.resize((max(1, int(im.width * ratio)), max(1, int(im.height * ratio))),
                     Image.LANCZOS)


def tile(shield, canvas=1024, scale=0.90):
    base = Image.new("RGB", (canvas, canvas), (255, 255, 255))

    # 很淡的蓝光，避免白底死白
    glow = Image.new("L", (canvas, canvas), 0)
    gd = ImageDraw.Draw(glow)
    r = canvas * 0.42
    gd.ellipse([canvas / 2 - r, canvas / 2 - r, canvas / 2 + r, canvas / 2 + r], fill=30)
    glow = glow.filter(ImageFilter.GaussianBlur(canvas * 0.10))
    base = Image.composite(Image.new("RGB", (canvas, canvas), (214, 236, 255)), base, glow)

    sh = fit(shield, int(canvas * scale), int(canvas * scale))
    ox, oy = (canvas - sh.width) // 2, (canvas - sh.height) // 2

    # 投影
    mask = silhouette(sh).filter(ImageFilter.GaussianBlur(canvas * 0.012))
    mask = mask.point(lambda v: int(v * 0.20))
    base.paste(Image.new("RGB", sh.size, (52, 86, 128)), (ox, oy + int(canvas * 0.010)), mask)

    # 主体（白底贴白底，无缝）
    base.paste(sh, (ox, oy))
    return base


def main():
    src = Image.open(SRC).convert("RGB")
    b = content_bbox(src)
    pad = 6
    shield = src.crop((max(0, b[0] - pad), max(0, b[1] - pad),
                       min(src.width, b[2] + pad), min(src.height, b[3] + pad)))
    print("盾牌裁切尺寸", shield.size, "（宽高比 %.2f）" % (shield.width / shield.height))

    os.makedirs(OUT_RES, exist_ok=True)
    tile1024 = tile(shield)
    tile1024.save("icon-src/icon_tile_1024.png")

    for name, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96,
                     "xxhdpi": 144, "xxxhdpi": 192}.items():
        d = os.path.join(OUT_RES, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        tile1024.resize((px, px), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))

    # 自适应图标前景：108dp 画布，图形缩到 58% 落在安全区内，白底与 background 同色
    for name, px in {"mdpi": 108, "hdpi": 162, "xhdpi": 216,
                     "xxhdpi": 324, "xxxhdpi": 432}.items():
        d = os.path.join(OUT_RES, "mipmap-" + name)
        canvas = Image.new("RGB", (px, px), (255, 255, 255))
        sh = fit(shield, int(px * 0.58), int(px * 0.58))
        canvas.paste(sh, ((px - sh.width) // 2, (px - sh.height) // 2))
        canvas.save(os.path.join(d, "ic_launcher_fg.png"))

    print("全部完成")


if __name__ == "__main__":
    main()
