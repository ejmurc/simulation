import numpy as np
from PIL import Image
import math
import random
import os

def lerp(a, b, t):
    return a + (b - a) * t

def color_lerp(c1, c2, t):
    return (
        int(lerp(c1[0], c2[0], t)),
        int(lerp(c1[1], c2[1], t)),
        int(lerp(c1[2], c2[2], t)),
    )

# Enhanced noise functions for more organic patterns
def hash2(x, y, seed):
    return math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453 % 1

def smooth_noise(x, y, seed):
    x0 = int(x)
    y0 = int(y)
    x1 = x0 + 1
    y1 = y0 + 1
    
    sx = x - x0
    sy = y - y0
    
    n00 = hash2(x0, y0, seed)
    n10 = hash2(x1, y0, seed)
    n01 = hash2(x0, y1, seed)
    n11 = hash2(x1, y1, seed)
    
    ix0 = lerp(n00, n10, sx)
    ix1 = lerp(n01, n11, sx)
    
    return lerp(ix0, ix1, sy)

def fbm(x, y, seed, octaves=5):
    value = 0.0
    amp = 0.5
    freq = 1.0
    
    for i in range(octaves):
        value += amp * smooth_noise(x * freq, y * freq, seed + i * 13)
        freq *= 2.0
        amp *= 0.5
    
    return value

def random_color():
    """Generate a random RGB color"""
    return (
        random.randint(0, 255),
        random.randint(0, 255),
        random.randint(0, 255)
    )

def create_splatter_mask(x, y, size, splatter_points):
    """Create a splatter/marble effect mask"""
    mask = 0.0
    nx = x / size
    ny = y / size
    
    for px, py, radius in splatter_points:
        dx = nx - px
        dy = ny - py
        dist = math.sqrt(dx*dx + dy*dy)
        if dist < radius:
            mask += (1.0 - dist/radius) * 0.5
    
    return min(1.0, mask)

def generate_splatter_texture(size, colors):
    """Generate texture with paint splatter/mix effect"""
    img = np.zeros((size, size, 3), dtype=np.uint8)
    
    # Create random splatter points
    num_splatters = random.randint(15, 30)
    splatter_points = []
    for _ in range(num_splatters):
        px = random.uniform(0.2, 0.8)
        py = random.uniform(0.2, 0.8)
        radius = random.uniform(0.05, 0.2)
        splatter_points.append((px, py, radius))
    
    # Random noise seed
    seed = random.randint(0, 10000)
    
    for y in range(size):
        for x in range(size):
            nx = x / size
            ny = y / size
            
            # Base noise for variation
            noise_val = fbm(nx * 4.0, ny * 4.0, seed, 4)
            
            # Splatter mask
            splatter = create_splatter_mask(x, y, size, splatter_points)
            
            # Create mixing factor
            mix_factor = (noise_val * 0.6 + splatter * 0.4)
            
            # Add some swirl/vortex effects
            angle = math.atan2(ny - 0.5, nx - 0.5)
            radius = math.sqrt((nx-0.5)**2 + (ny-0.5)**2)
            swirl = math.sin(angle * 8.0 - radius * 20.0) * 0.3
            mix_factor += swirl
            
            # Clamp and smooth
            mix_factor = max(0.0, min(1.0, mix_factor))
            
            # Mix colors based on splatter pattern
            if splatter > 0.3:
                # Use splatter colors with variation
                color_idx = int(splatter * len(colors))
                color_idx = min(color_idx, len(colors)-1)
                base_color = colors[color_idx]
                
                # Add variation
                variation = (noise_val * 0.3)
                r = int(base_color[0] * (0.7 + variation))
                g = int(base_color[1] * (0.7 + variation))
                b = int(base_color[2] * (0.7 + variation))
                img[y, x] = (min(255, r), min(255, g), min(255, b))
            else:
                # Blend colors with noise
                img[y, x] = color_lerp(colors[0], colors[1], mix_factor)
    
    return img

def generate_planet_texture(name, color_scheme):
    """Generate texture for a specific planet"""
    size = 1024
    
    if color_scheme == "mustafar":
        # Mustafar - volcanic/lava colors
        primary_colors = [
            (180, 40, 20),   # Dark red
            (220, 60, 30),   # Lava red
            (140, 30, 15),   # Dark volcanic
            (255, 80, 40),   # Bright lava
            (100, 25, 10)    # Dark rock
        ]
        secondary_colors = [
            (255, 100, 20),  # Orange lava
            (200, 50, 15),   # Red-orange
            (255, 60, 10),   # Bright orange
            (180, 40, 5)     # Deep orange
        ]
    else:  # kashyyyk - forest/earthy colors
        primary_colors = [
            (30, 80, 30),    # Dark forest
            (50, 120, 40),   # Mid green
            (20, 60, 20),    # Deep forest
            (60, 100, 35),   # Olive
            (40, 90, 25)     # Earthy green
        ]
        secondary_colors = [
            (80, 140, 50),   # Bright green
            (100, 160, 60),  # Vibrant green
            (60, 110, 45),   # Medium green
            (120, 100, 60)   # Brownish green
        ]
    
    # Randomly select 3-5 colors from each palette
    num_primary = random.randint(3, 5)
    num_secondary = random.randint(2, 4)
    
    selected_primary = random.sample(primary_colors, min(num_primary, len(primary_colors)))
    selected_secondary = random.sample(secondary_colors, min(num_secondary, len(secondary_colors)))
    
    all_colors = selected_primary + selected_secondary
    random.shuffle(all_colors)
    
    print("Generating {} texture with {} colors:".format(name, len(all_colors)))
    for i, color in enumerate(all_colors):
        print("  Color {}: RGB{}".format(i+1, color))
    
    # Generate the splatter texture
    img = generate_splatter_texture(size, all_colors)
    
    # Add some post-processing effects
    if color_scheme == "mustafar":
        # Add a subtle red/orange tint to Mustafar
        for y in range(size):
            for x in range(size):
                r, g, b = img[y, x]
                img[y, x] = (min(255, int(r * 1.1)), 
                            int(g * 0.9), 
                            int(b * 0.8))
    else:
        # Add a subtle green/blue tint to Kashyyyk
        for y in range(size):
            for x in range(size):
                r, g, b = img[y, x]
                img[y, x] = (int(r * 0.9), 
                            min(255, int(g * 1.05)), 
                            int(b * 0.95))
    
    return img

def main():
    print("=" * 50)
    print("Generating Star Wars Planet Textures")
    print("=" * 50)
    
    # Generate Mustafar texture (volcanic/lava)
    print("\nGenerating Mustafar texture...")
    mustafar_texture = generate_planet_texture("mustafar", "mustafar")
    mustafar_img = Image.fromarray(mustafar_texture)
    mustafar_img.save("mustafar.png")
    print("Saved mustafar.png ({}x{})".format(mustafar_texture.shape[0], mustafar_texture.shape[1]))
    
    # Generate Kashyyyk texture (forest/earthy)
    print("\nGenerating Kashyyyk texture...")
    kashyyyk_texture = generate_planet_texture("kashyyyk", "kashyyyk")
    kashyyyk_img = Image.fromarray(kashyyyk_texture)
    kashyyyk_img.save("kashyyyk.png")
    print("Saved kashyyyk.png ({}x{})".format(kashyyyk_texture.shape[0], kashyyyk_texture.shape[1]))
    
    print("\n" + "=" * 50)
    print("Texture generation complete!")
    print("Files created: mustafar.png, kashyyyk.png")
    print("=" * 50)
    
    # Optional: Create a preview collage
    try:
        from PIL import ImageDraw
        
        # Create a side-by-side preview
        preview = Image.new('RGB', (2048, 1024))
        preview.paste(mustafar_img, (0, 0))
        preview.paste(kashyyyk_img, (1024, 0))
        
        # Add labels
        draw = ImageDraw.Draw(preview)
        draw.text((50, 50), "Mustafar", fill=(255, 255, 255))
        draw.text((1074, 50), "Kashyyyk", fill=(255, 255, 255))
        
        preview.save("texture_preview.png")
        print("Saved texture_preview.png")
    except:
        pass

if __name__ == "__main__":
    main()
