import math
import argparse

def write_sphere(filename, radius, lat_segments, lon_segments):
    vertices = []
    tex_coords = []
    faces = []
    face_normals = []  # for smooth shading

    # generate vertices and texture coordinates
    for i in range(lat_segments + 1):
        theta = i * math.pi / lat_segments  # from 0 to pi
        sin_t = math.sin(theta)
        cos_t = math.cos(theta)
        
        # V texture coordinate (latitude)
        v = 1.0 - (i / lat_segments)  # flip so north pole is top

        for j in range(lon_segments + 1):
            phi = j * 2 * math.pi / lon_segments  # from 0 to 2pi
            sin_p = math.sin(phi)
            cos_p = math.cos(phi)

            # vertex position
            x = radius * sin_t * cos_p
            y = radius * cos_t
            z = radius * sin_t * sin_p
            vertices.append((x, y, z))
            
            # U texture coordinate (longitude)
            u = 1.0 - (j / lon_segments)  # flip so texture wraps correctly
            
            tex_coords.append((u, v))

    # generate faces (triangles instead of quads)
    for i in range(lat_segments):
        for j in range(lon_segments):
            first = i * (lon_segments + 1) + j
            second = first + lon_segments + 1

            # Triangle 1 (bottom-left, top-left, top-right)
            faces.append((
                first + 1,      # vertex index
                second + 1,     # vertex index
                second + 2,     # vertex index
                first + 1,      # texcoord index
                second + 1,     # texcoord index
                second + 2      # texcoord index
            ))
            
            # Triangle 2 (bottom-left, top-right, bottom-right)
            faces.append((
                first + 1,      # vertex index
                second + 2,     # vertex index
                first + 2,      # vertex index
                first + 1,      # texcoord index
                second + 2,     # texcoord index
                first + 2       # texcoord index
            ))

    # write OBJ with vertex positions, texture coordinates, and faces
    with open(filename, "w") as f:
        f.write("# Low poly sphere with texture coordinates\n")
        f.write("# Generated for Star Wars planet simulation\n\n")

        # Write vertices (positions)
        for v in vertices:
            f.write(f"v {v[0]} {v[1]} {v[2]}\n")

        # Write texture coordinates
        for vt in tex_coords:
            f.write(f"vt {vt[0]} {vt[1]}\n")

        # Write faces (with vertex and texture coordinate indices)
        for face in faces:
            # Format: f v1/vt1 v2/vt2 v3/vt3
            f.write(f"f {face[0]}/{face[3]} {face[1]}/{face[4]} {face[2]}/{face[5]}\n")

    print(f"Saved: {filename}")
    print(f"  Vertices: {len(vertices)}")
    print(f"  Texture coordinates: {len(tex_coords)}")
    print(f"  Triangles: {len(faces)}")

def write_sphere_with_normals(filename, radius, lat_segments, lon_segments):
    """Alternative: Write sphere with normals for better lighting"""
    vertices = []
    normals = []
    tex_coords = []
    faces = []

    for i in range(lat_segments + 1):
        theta = i * math.pi / lat_segments
        sin_t = math.sin(theta)
        cos_t = math.cos(theta)
        v = 1.0 - (i / lat_segments)

        for j in range(lon_segments + 1):
            phi = j * 2 * math.pi / lon_segments
            sin_p = math.sin(phi)
            cos_p = math.cos(phi)

            # Position
            x = radius * sin_t * cos_p
            y = radius * cos_t
            z = radius * sin_t * sin_p
            vertices.append((x, y, z))
            
            # Normal (normalized position for sphere)
            norm = (x / radius, y / radius, z / radius)
            normals.append(norm)
            
            # Texture coordinate
            u = 1.0 - (j / lon_segments)
            tex_coords.append((u, v))

    # Generate triangles
    for i in range(lat_segments):
        for j in range(lon_segments):
            first = i * (lon_segments + 1) + j
            second = first + lon_segments + 1

            # Triangle 1
            faces.append((first + 1, second + 1, second + 2))
            # Triangle 2
            faces.append((first + 1, second + 2, first + 2))

    with open(filename, "w") as f:
        f.write("# Low poly sphere with normals\n\n")
        
        for v in vertices:
            f.write(f"v {v[0]} {v[1]} {v[2]}\n")
        
        for vn in normals:
            f.write(f"vn {vn[0]} {vn[1]} {vn[2]}\n")
        
        for vt in tex_coords:
            f.write(f"vt {vt[0]} {vt[1]}\n")
        
        # Write faces with vertex/texture/normal indices
        for i in range(0, len(faces), 2):
            t1 = faces[i]
            t2 = faces[i+1]
            # Format: f v1/vt1/vn1 v2/vt2/vn2 v3/vt3/vn3
            f.write(f"f {t1[0]}/{t1[0]}/{t1[0]} {t1[1]}/{t1[1]}/{t1[1]} {t1[2]}/{t1[2]}/{t1[2]}\n")
            f.write(f"f {t2[0]}/{t2[0]}/{t2[0]} {t2[1]}/{t2[1]}/{t2[1]} {t2[2]}/{t2[2]}/{t2[2]}\n")

    print(f"Saved (with normals): {filename}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate sphere OBJ files for planet rendering")
    parser.add_argument("--out", default="sphere.obj", help="Output filename")
    parser.add_argument("--radius", type=float, default=1.0, help="Sphere radius")
    parser.add_argument("--lat", type=int, default=32, help="Latitude segments (higher = smoother)")
    parser.add_argument("--lon", type=int, default=32, help="Longitude segments (higher = smoother)")
    parser.add_argument("--normals", action="store_true", help="Include normals in OBJ file")
    
    args = parser.parse_args()
    
    if args.normals:
        write_sphere_with_normals(args.out, args.radius, args.lat, args.lon)
    else:
        write_sphere(args.out, args.radius, args.lat, args.lon)
    
    print("\nTo generate the planets:")
    print(f"  python sphere_gen.py --out mustafar.obj --radius 1.55 --lat 64 --lon 64")
    print(f"  python sphere_gen.py --out kashyyyk.obj --radius 1.75 --lat 64 --lon 64")
