import numpy as np
from PIL import Image, ImageDraw
import math

def generate_platform_obj(filename, radius=15.0, segments=64):
    """Generate a circular platform OBJ file with texture coordinates"""
    
    vertices = []
    tex_coords = []
    faces = []
    
    # Center vertex
    vertices.append((0.0, -0.5, 0.0))
    tex_coords.append((0.5, 0.5))
    
    # Generate ring vertices
    for i in range(segments + 1):
        angle = 2.0 * math.pi * i / segments
        x = radius * math.cos(angle)
        z = radius * math.sin(angle)
        vertices.append((x, -0.5, z))
        
        # Texture coordinates (radial mapping)
        u = 0.5 + 0.5 * math.cos(angle)
        v = 0.5 + 0.5 * math.sin(angle)
        tex_coords.append((u, v))
    
    # Generate faces (triangles from center to ring)
    for i in range(segments):
        # Triangle 1
        faces.append((0, i + 1, i + 2))
    
    # Write OBJ file
    with open(filename, 'w') as f:
        f.write("# Platform OBJ file\n")
        f.write("# Generated for Star Wars Simulation\n\n")
        
        # Write vertices
        for v in vertices:
            f.write("v {:.6f} {:.6f} {:.6f}\n".format(v[0], v[1], v[2]))
        
        # Write texture coordinates
        for vt in tex_coords:
            f.write("vt {:.6f} {:.6f}\n".format(vt[0], vt[1]))
        
        # Write faces
        for face in faces:
            f.write("f {}/{} {}/{} {}/{}\n".format(
                face[0]+1, face[0]+1,
                face[1]+1, face[1]+1,
                face[2]+1, face[2]+1
            ))
    
    print("Generated: {}".format(filename))

def generate_platform_texture(filename, size=1024):
    """Generate a detailed platform texture with grid and tech pattern"""
    
    img = Image.new('RGBA', (size, size), (30, 30, 40, 255))
    draw = ImageDraw.Draw(img)
    
    # Draw radial lines
    center = size // 2
    max_radius = size // 2
    
    for i in range(0, 360, 15):  # Every 15 degrees
        angle = math.radians(i)
        x2 = center + int(max_radius * math.cos(angle))
        y2 = center + int(max_radius * math.sin(angle))
        draw.line([(center, center), (x2, y2)], fill=(100, 100, 150, 255), width=2)
    
    # Draw concentric circles
    for r in range(0, max_radius, max_radius // 8):
        draw.ellipse([(center - r, center - r), (center + r, center + r)], 
                     outline=(80, 80, 120, 255), width=3)
    
    # Draw inner ring
    inner_radius = max_radius // 4
    draw.ellipse([(center - inner_radius, center - inner_radius),
                  (center + inner_radius, center + inner_radius)],
                 outline=(150, 150, 200, 255), width=4)
    
    # Draw center dot
    draw.ellipse([(center - 10, center - 10), (center + 10, center + 10)],
                 fill=(200, 200, 255, 255))
    
    # Add grid pattern overlay
    grid_spacing = size // 16
    for x in range(0, size, grid_spacing):
        draw.line([(x, 0), (x, size)], fill=(60, 60, 80, 100), width=1)
        draw.line([(0, x), (size, x)], fill=(60, 60, 80, 100), width=1)
    
    # Add some tech dots
    for i in range(200):
        import random
        x = random.randint(0, size)
        y = random.randint(0, size)
        dist = math.sqrt((x - center)**2 + (y - center)**2)
        if dist < max_radius * 0.9:
            draw.point((x, y), fill=(120, 120, 180, 200))
    
    img.save(filename)
    print("Generated: {}".format(filename))

if __name__ == "__main__":
    print("Generating platform assets...")
    generate_platform_obj("platform.obj", radius=12.0, segments=64)
    generate_platform_texture("platform.png", size=1024)
    print("\nDone! Files created: platform.obj, platform.png")
