#!/usr/bin/env python3
"""
生成简单的应用图标到 mipmap-* 目录
依赖: pillow
用法: python3 create_icon.py <res_root> <base_name>
"""
import sys, os
from PIL import Image, ImageDraw

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def make_icon(size, color=(98,0,238)):
    img = Image.new("RGBA", (size,size), (0,0,0,0))
    draw = ImageDraw.Draw(img)
    for i in range(size//2, 0, -1):
        ratio = i / (size/2)
        r = int(color[0]*ratio + 255*(1-ratio))
        g = int(color[1]*ratio + 255*(1-ratio))
        b = int(color[2]*ratio + 255*(1-ratio))
        bbox = [size//2 - i, size//2 - i, size//2 + i, size//2 + i]
        draw.ellipse(bbox, fill=(r,g,b,255))
    inset = size//6
    draw.ellipse([inset,inset,size-inset,size-inset], fill=(255,255,255,200))
    return img

def main():
    if len(sys.argv) < 3:
        print("Usage: create_icon.py <res_root> <base_name>")
        sys.exit(1)
    res_root = sys.argv[1]
    base = sys.argv[2]
    for folder, px in sizes.items():
        d = os.path.join(res_root, folder)
        os.makedirs(d, exist_ok=True)
        path = os.path.join(d, base + ".png")
        img = make_icon(px)
        img.save(path, format="PNG")
        print("Wrote", path)

if __name__ == "__main__":
    main()
