import math
import argparse

def write_sphere(filename, radius, lat_segments, lon_segments):
    vertices = []
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

            x = radius * sin_t * cos_p
            y = radius * cos_t
            z = radius * sin_t * sin_p
            vertices.append((x, y, z))
            
            u = 1.0 - (j / lon_segments)
            tex_coords.append((u, v))

    for i in range(lat_segments):
        for j in range(lon_segments):
            first = i * (lon_segments + 1) + j
            second = first + lon_segments + 1

            faces.append((
                first + 1,
                first + 2,
                second + 2,
                first + 1,
                first + 2,
                second + 2
            ))
            faces.append((
                first + 1,
                second + 2,
                second + 1,
                first + 1,
                second + 2,
                second + 1
            ))

    with open(filename, "w") as f:
        f.write("# Sphere OBJ\n")
        for v in vertices:
            f.write(f"v {v[0]} {v[1]} {v[2]}\n")
        for vt in tex_coords:
            f.write(f"vt {vt[0]} {vt[1]}\n")
        for face in faces:
            f.write(f"f {face[0]}/{face[3]} {face[1]}/{face[4]} {face[2]}/{face[5]}\n")

    print(f"Saved: {filename}")
    print(f"  Vertices: {len(vertices)}")
    print(f"  Texture coordinates: {len(tex_coords)}")
    print(f"  Triangles: {len(faces)}")

def write_sphere_with_normals(filename, radius, lat_segments, lon_segments):
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

            x = radius * sin_t * cos_p
            y = radius * cos_t
            z = radius * sin_t * sin_p
            vertices.append((x, y, z))
            
            norm = (x / radius, y / radius, z / radius)
            normals.append(norm)
            
            u = 1.0 - (j / lon_segments)
            tex_coords.append((u, v))

    for i in range(lat_segments):
        for j in range(lon_segments):
            first = i * (lon_segments + 1) + j
            second = first + lon_segments + 1

            faces.append((first + 1, first + 2, second + 2))
            faces.append((first + 1, second + 2, second + 1))

    with open(filename, "w") as f:
        f.write("# Sphere OBJ with normals\n")
        for v in vertices:
            f.write(f"v {v[0]} {v[1]} {v[2]}\n")
        for vn in normals:
            f.write(f"vn {vn[0]} {vn[1]} {vn[2]}\n")
        for vt in tex_coords:
            f.write(f"vt {vt[0]} {vt[1]}\n")
        for i in range(0, len(faces), 2):
            t1 = faces[i]
            t2 = faces[i+1]
            f.write(f"f {t1[0]}/{t1[0]}/{t1[0]} {t1[1]}/{t1[1]}/{t1[1]} {t1[2]}/{t1[2]}/{t1[2]}\n")
            f.write(f"f {t2[0]}/{t2[0]}/{t2[0]} {t2[1]}/{t2[1]}/{t2[1]} {t2[2]}/{t2[2]}/{t2[2]}\n")

    print(f"Saved (with normals): {filename}")
    print(f"  Vertices: {len(vertices)}")
    print(f"  Normals: {len(normals)}")
    print(f"  Texture coordinates: {len(tex_coords)}")
    print(f"  Triangles: {len(faces)}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate sphere OBJ files")
    parser.add_argument("--out", default="sphere.obj", help="Output filename")
    parser.add_argument("--radius", type=float, default=1.0, help="Sphere radius")
    parser.add_argument("--lat", type=int, default=32, help="Latitude segments")
    parser.add_argument("--lon", type=int, default=32, help="Longitude segments")
    parser.add_argument("--normals", action="store_true", help="Include normals in OBJ")
    
    args = parser.parse_args()
    
    if args.normals:
        write_sphere_with_normals(args.out, args.radius, args.lat, args.lon)
    else:
        write_sphere(args.out, args.radius, args.lat, args.lon)
    
    print("\nExample planet generation:")
    print("  python sphere_gen.py --out mustafar.obj --radius 1.55 --lat 64 --lon 64 --normals")
    print("  python sphere_gen.py --out kashyyyk.obj --radius 1.75 --lat 64 --lon 64 --normals")
