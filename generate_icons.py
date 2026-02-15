#!/usr/bin/env python3
import os
import sys
from PIL import Image, ImageDraw, ImageFilter

def create_directory(path):
    os.makedirs(path, exist_ok=True)

def generate_launcher_icon(size, output_path):
    """生成自适应图标"""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    safe_size = int(size * 0.66)
    offset = (size - safe_size) // 2
    
    # 渐变背景圆形
    for i in range(safe_size):
        ratio = i / safe_size
        r = int(66 + (100 - 66) * ratio)
        g = int(133 + (181 - 133) * ratio)
        b = int(244 + (246 - 244) * ratio)
        draw.ellipse(
            [offset + i//2, offset + i//2, 
             size - offset - i//2, size - offset - i//2],
            fill=(r, g, b, 255)
        )
    
    # 3D立方体
    cube_size = int(safe_size * 0.5)
    cube_x = (size - cube_size) // 2
    cube_y = (size - cube_size) // 2
    
    # 前面
    draw.polygon([
        (cube_x, cube_y + cube_size//3),
        (cube_x + cube_size, cube_y + cube_size//3),
        (cube_x + cube_size, cube_y + cube_size),
        (cube_x, cube_y + cube_size)
    ], fill=(255, 255, 255, 230))
    
    # 顶面
    draw.polygon([
        (cube_x + cube_size//4, cube_y),
        (cube_x + cube_size//4 + cube_size, cube_y),
        (cube_x + cube_size, cube_y + cube_size//3),
        (cube_x, cube_y + cube_size//3)
    ], fill=(255, 255, 255, 200))
    
    # 右面
    draw.polygon([
        (cube_x + cube_size//4 + cube_size, cube_y),
        (cube_x + cube_size, cube_y + cube_size//3),
        (cube_x + cube_size, cube_y + cube_size),
        (cube_x + cube_size//4 + cube_size, cube_y + cube_size - cube_size//3)
    ], fill=(255, 255, 255, 180))
    
    # 阴影
    shadow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.ellipse(
        [offset - 5, size - offset - 10, size - offset + 5, size - offset + 10],
        fill=(0, 0, 0, 50)
    )
    img = Image.alpha_composite(shadow, img)
    img = img.filter(ImageFilter.SMOOTH)
    
    img.save(output_path, 'PNG')
    print(f"✅ 生成: {output_path}")

def generate_simple_icon(size, output_path, color=(66, 133, 244)):
    """生成简单图标"""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    margin = size // 8
    draw.rounded_rectangle(
        [margin, margin, size - margin, size - margin],
        radius=size//4,
        fill=(*color, 255)
    )
    
    # 白色立方体图标
    cube_size = size // 3
    x = (size - cube_size) // 2
    y = (size - cube_size) // 2
    
    draw.polygon([(x, y+cube_size//2), (x+cube_size, y+cube_size//2), 
                  (x+cube_size, y+cube_size), (x, y+cube_size)], 
                 fill=(255, 255, 255, 240))
    draw.polygon([(x+cube_size//4, y), (x+cube_size//4+cube_size, y),
                  (x+cube_size, y+cube_size//2), (x, y+cube_size//2)],
                 fill=(255, 255, 255, 200))
    draw.polygon([(x+cube_size//4+cube_size, y), (x+cube_size+cube_size//4, y+cube_size//2),
                  (x+cube_size, y+cube_size), (x+cube_size, y+cube_size//2)],
                 fill=(255, 255, 255, 160))
    
    img.save(output_path, 'PNG')

def generate_placeholder(size, output_path):
    """生成占位图"""
    img = Image.new('RGB', (size, size), (240, 240, 245))
    draw = ImageDraw.Draw(img)
    
    # 网格
    grid = size // 8
    for i in range(0, size, grid):
        draw.line([(i, 0), (i, size)], fill=(220, 220, 230), width=1)
        draw.line([(0, i), (size, i)], fill=(220, 220, 230), width=1)
    
    # 中心立方体
    cs = size // 4
    x = (size - cs) // 2
    y = (size - cs) // 2
    
    draw.polygon([(x, y+cs//2), (x+cs, y+cs//2), (x+cs, y+cs), (x, y+cs)], 
                 fill=(100, 150, 255))
    draw.polygon([(x+cs//2, y), (x+cs//2+cs, y), (x+cs, y+cs//2), (x, y+cs//2)],
                 fill=(150, 180, 255))
    draw.polygon([(x+cs//2+cs, y), (x+cs+cs//2, y+cs//2), (x+cs, y+cs), (x+cs, y+cs//2)],
                 fill=(80, 120, 220))
    
    img.save(output_path, 'PNG')

def main():
    base = "app/src/main/res"
    
    # 应用图标尺寸
    sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }
    
    for folder, size in sizes.items():
        path = f"{base}/{folder}"
        create_directory(path)
        generate_launcher_icon(size, f"{path}/ic_launcher.png")
        generate_launcher_icon(size, f"{path}/ic_launcher_round.png")
        generate_launcher_icon(size, f"{path}/ic_launcher_foreground.png")
        generate_simple_icon(size, f"{path}/ic_launcher_background.png", (255, 255, 255))
    
    # Drawable资源
    dpath = f"{base}/drawable"
    create_directory(dpath)
    generate_placeholder(512, f"{dpath}/placeholder_model.png")
    
    # 简单图标
    for name in ['ic_add', 'ic_back', 'ic_reset', 'ic_info']:
        generate_simple_icon(96, f"{dpath}/{name}.png", (255, 255, 255))
    
    print("\n🎨 所有图标生成完成！")

if __name__ == "__main__":
    main()
