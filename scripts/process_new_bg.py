from PIL import Image
import os

input_path = r"C:\Users\Creator MPB\.gemini\antigravity\brain\80013bf0-878d-46a8-a5e7-787a7d99f1d7\shawarma_pattern_no_text_1786525683352.jpg"
output_dir = r"D:\PROJECT-APPS-NATIVE\POS\app\src\main\res\drawable-nodpi"
output_path = os.path.join(output_dir, "bg_login_pattern.webp")

os.makedirs(output_dir, exist_ok=True)

with Image.open(input_path) as img:
    if img.mode in ('RGBA', 'P'):
        img = img.convert('RGB')
        
    width, height = img.size
    target_w, target_h = 1920, 1080
    
    aspect_ratio = width / height
    target_aspect = target_w / target_h
    
    if aspect_ratio > target_aspect:
        new_height = target_h
        new_width = int(target_h * aspect_ratio)
    else:
        new_width = target_w
        new_height = int(target_w / aspect_ratio)
        
    img = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
    
    left = (new_width - target_w) / 2
    top = (new_height - target_h) / 2
    right = (new_width + target_w) / 2
    bottom = (new_height + target_h) / 2
    
    img = img.crop((left, top, right, bottom))
    
    img.save(output_path, 'WEBP', quality=85, method=6)
    print(f"Saved optimized shawarma background to {output_path} (Size: {os.path.getsize(output_path)} bytes)")
