import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.lwjgl.stb.STBImage;
import java.nio.*;
import java.io.*;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import org.lwjgl.openal.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import org.lwjgl.stb.STBVorbis;
import javax.sound.sampled.*;
import java.io.File;
import org.joml.Matrix4f;

public class Simulation {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final float FOV = 0.4663f;

    private long window;
    private int planetProg, atmosphereProg, sunProg, gridProg, platformProg, starProg;
    private int texMustafar, texKashyyyk, texPlatform;
    private int sunVAO, sunVBO, sunEBO, sunIndexCount;

    private int starVAO, starVBO, starCount;

    private long audioDevice;
    private long audioContext;
    private int musicSource;
    private IntBuffer musicBuffer;
    private boolean audioInitialized = false;
    private Clip backgroundMusic;

    private int mustafarVAO, mustafarIndexCount;
    private int kashyyykVAO, kashyyykIndexCount;

    private int platformVAO, platformIndexCount;
    private float platformY = -12.0f;

    private int gridVAO, gridIndexCount;

    private float musX = 12.0f, musZ = 0.0f;
    private float kasX = 22.0f, kasZ = 0.0f;
    private double orbitAngleMus = 0.0;
    private double orbitAngleKas = 2.1;
    private float musRotation = 0.0f;
    private float kasRotation = 0.0f;

    private double startTime;

    private float camX = 0, camY = 5, camZ = 25;
    private float yaw = 0, pitch = -0.2f;

    private boolean wDown, aDown, sDown, dDown, spDown, shDown;
    private boolean locked = false;
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    private int uModelPlanet, uViewPlanet, uProjPlanet, uTexPlanet, uSunPosPlanet, uViewPosPlanet, uTimePlanet;
    private int uModelAtmos, uViewAtmos, uProjAtmos, uSunPosAtmos, uViewPosAtmos, uColorAtmos, uTimeAtmos, uPlanetCenterAtmos, uPlanetRadiusAtmos, uAtmosRadiusAtmos;
    private int uModelPlatform, uViewPlatform, uProjPlatform, uTexPlatform, uSunPosPlatform, uViewPosPlatform, uTimePlatform;
    private int uModelSun, uViewSun, uProjSun, uTimeSun;
    private int uViewGrid, uProjGrid, uCamPosGrid;
    private int uViewStar, uProjStar, uTimeStar;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private static final String VERT_PLANET = """
    #version 410 core
    layout(location=0) in vec3 aPos;
    layout(location=1) in vec2 aTexCoord;
    layout(location=2) in vec3 aNormal;

    uniform mat4 uModel;
    uniform mat4 uView;
    uniform mat4 uProjection;

    out vec2 TexCoord;
    out vec3 FragPos;
    out vec3 Normal;

    void main() {
        TexCoord = aTexCoord;
        FragPos = vec3(uModel * vec4(aPos, 1.0));
        mat3 normalMatrix = transpose(inverse(mat3(uModel)));
        Normal = normalMatrix * aNormal;
        gl_Position = uProjection * uView * vec4(FragPos, 1.0);
    }
    """;

    private static final String FRAG_PLANET = """
    #version 410 core
    out vec4 frag;

    in vec2 TexCoord;
    in vec3 FragPos;
    in vec3 Normal;

    uniform sampler2D uTexture;
    uniform vec3 uSunPos;
    uniform vec3 uViewPos;
    uniform float uTime;

    float hash(vec2 p) {
        return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
    }

    float noise(vec2 p) {
        vec2 i = floor(p);
        vec2 f = fract(p);
        float a = hash(i);
        float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0));
        float d = hash(i + vec2(1.0, 1.0));
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    float fbm(vec2 p) {
        float v = 0.0;
        float a = 0.5;
        for (int i = 0; i < 4; ++i) {
            v += a * noise(p);
            p *= 2.0;
            a *= 0.5;
        }
        return v;
    }

    vec3 reinhard(vec3 x) {
        return x / (1.0 + x);
    }

    void main() {
        vec2 uv = TexCoord;
        vec2 flow = vec2(uTime * 0.01, uTime * 0.006);

        float n1 = fbm(uv * 5.0 + flow);
        float n2 = fbm(uv * 14.0 - flow * 1.6);
        float n3 = noise(uv * 32.0 + flow * 3.0);

        vec4 texColor = texture(uTexture, uv + vec2(uTime * 0.015, 0.0));
        vec3 albedo = texColor.rgb;
        albedo *= 0.80 + 0.30 * n1;
        albedo += (n2 - 0.5) * 0.10;
        albedo *= 0.92 + 0.08 * smoothstep(0.2, 0.8, n3);

        vec3 N = normalize(Normal);
        vec3 L = normalize(uSunPos - FragPos);
        vec3 V = normalize(uViewPos - FragPos);
        vec3 H = normalize(L + V);

        vec3 lightColor = vec3(1.0, 0.98, 0.92);
        float lightIntensity = 1.1;

        float NdotL = max(dot(N, L), 0.0);
        float NdotV = max(dot(N, V), 0.0);
        float NdotH = max(dot(N, H), 0.0);

        float ambient = 0.05;
        float diffuse = NdotL;

        float specPower = mix(32.0, 96.0, n1);
        float spec = pow(NdotH, specPower) * (specPower + 2.0) / 8.0;
        spec *= smoothstep(0.0, 0.1, NdotL) * 0.3;

        float rim = pow(1.0 - NdotV, 3.0) * 0.08 * NdotL;

        vec3 lit = albedo * (ambient + diffuse * lightIntensity) * lightColor;
        lit += lightColor * spec;
        lit += albedo * rim * lightColor;

        lit *= (0.97 + 0.03 * NdotL);

        lit = reinhard(lit);

        frag = vec4(lit, 1.0);
    }
    """;

    private static final String FRAG_ATMOSPHERE = """
    #version 410 core
    out vec4 frag;

    in vec2 TexCoord;
    in vec3 FragPos;
    in vec3 Normal;

    uniform vec3 uSunPos;
    uniform vec3 uViewPos;
    uniform vec3 uAtmosphereColor;
    uniform float uTime;
    uniform vec3 uPlanetCenter;
    uniform float uPlanetRadius;
    uniform float uAtmosphereRadius;

    const float PI = 3.14159265359;

    float rayleighPhase(float cosTheta) {
        float cosThetaSq = cosTheta * cosTheta;
        return (3.0 / (16.0 * PI)) * (1.0 + cosThetaSq);
    }

    float miePhase(float cosTheta, float g) {
        float g2 = g * g;
        float denom = 1.0 + g2 - 2.0 * g * cosTheta;
        denom = max(denom, 0.001);
        return (3.0 * (1.0 - g2)) / (2.0 * (2.0 + g2) * pow(denom, 1.5));
    }

    void main() {
        vec3 viewDir = normalize(uViewPos - FragPos);
        vec3 lightDir = normalize(uSunPos - FragPos);
        vec3 planetSurfaceNormal = normalize(FragPos - uPlanetCenter);

        float cosViewSurface = dot(viewDir, planetSurfaceNormal);
        float cosLightSurface = dot(lightDir, planetSurfaceNormal);
        float cosViewLight = dot(viewDir, lightDir);

        cosViewLight = clamp(cosViewLight, -0.999, 0.999);

        float altitude = length(FragPos - uPlanetCenter) - uPlanetRadius;
        float atmosphereThickness = uAtmosphereRadius - uPlanetRadius;
        float normalizedAltitude = clamp(altitude / atmosphereThickness, 0.0, 1.0);

        float opticalDepthRayleigh = (1.0 - exp(-normalizedAltitude * 1.8)) * 0.65;
        float opticalDepthMie = (1.0 - exp(-normalizedAltitude * 3.5)) * 0.45;

        float rayleighAmount = opticalDepthRayleigh * rayleighPhase(cosViewLight);
        float mieAmount = opticalDepthMie * miePhase(cosViewLight, 0.76);

        float rimIntensity = pow(1.0 - abs(cosViewSurface), 2.2);
        float glow = rimIntensity * 0.9 + rayleighAmount * 0.8 + mieAmount * 0.6;

        vec3 rayleighColor = vec3(0.25, 0.5, 1.0);
        vec3 mieColor = vec3(1.0, 0.9, 0.7);
        vec3 baseColor = uAtmosphereColor;

        vec3 scattered = mix(rayleighColor, baseColor, 0.6) * rayleighAmount;
        scattered += mieColor * mieAmount;
        scattered += baseColor * rimIntensity * 0.7;

        float alpha = clamp(glow * 0.75, 0.0, 0.9);
        frag = vec4(scattered * (0.95 + 0.05 * sin(uTime * 0.8)), alpha);
    }
    """;

    private static final String VERT_PLATFORM = """
    #version 410 core
    layout(location=0) in vec3 aPos;
    layout(location=1) in vec2 aTexCoord;
    layout(location=2) in vec3 aNormal;

    uniform mat4 uModel;
    uniform mat4 uView;
    uniform mat4 uProjection;

    out vec2 TexCoord;
    out vec3 FragPos;
    out vec3 Normal;

    void main() {
        TexCoord = aTexCoord;
        FragPos = vec3(uModel * vec4(aPos, 1.0));
        mat3 normalMatrix = transpose(inverse(mat3(uModel)));
        Normal = normalMatrix * aNormal;
        gl_Position = uProjection * uView * vec4(FragPos, 1.0);
    }
    """;

    private static final String FRAG_PLATFORM = """
    #version 410 core
    out vec4 frag;

    in vec2 TexCoord;
    in vec3 FragPos;
    in vec3 Normal;

    uniform sampler2D uTexture;
    uniform vec3 uSunPos;
    uniform vec3 uViewPos;
    uniform float uTime;

    void main() {
        vec2 animatedUV = TexCoord;
        animatedUV.x += uTime * 0.02;
        animatedUV.y += uTime * 0.01;
        vec4 texColor = texture(uTexture, animatedUV);

        vec3 N = normalize(Normal);
        vec3 L = normalize(uSunPos - FragPos);
        vec3 V = normalize(uViewPos - FragPos);
        vec3 H = normalize(L + V);

        float ambient = 0.24;
        float diff = max(dot(N, L), 0.08);
        float spec = pow(max(dot(N, H), 0.0), 96.0) * 0.22;

        vec3 lit = texColor.rgb * (ambient + diff) + vec3(0.6, 0.65, 0.8) * spec;
        frag = vec4(lit, 1.0);
    }
    """;

    private static final String VERT_SUN = """
    #version 410 core
    layout(location=0) in vec3 aPos;

    uniform mat4 uModel;
    uniform mat4 uView;
    uniform mat4 uProjection;
    uniform float uTime;

    out vec3 FragPos;

    void main() {
        FragPos = vec3(uModel * vec4(aPos, 1.0));
        gl_Position = uProjection * uView * vec4(FragPos, 1.0);
    }
    """;

    private static final String FRAG_SUN = """
    #version 410 core
    out vec4 frag;

    in vec3 FragPos;
    uniform float uTime;

    void main() {
        float pulse = 0.85 + 0.15 * sin(uTime * 2.0);
        vec3 sunColor = vec3(1.0, 0.72, 0.32) * pulse;
        sunColor += vec3(0.3, 0.12, 0.02) * sin(FragPos.xyx * 12.0 + uTime * 4.0) * 0.15;
        frag = vec4(sunColor, 1.0);
    }
    """;

    private static final String VERT_GRID = """
    #version 410 core
    layout(location=0) in vec3 aPos;

    uniform mat4 uView;
    uniform mat4 uProjection;
    uniform vec3 uCamPos;

    out float Alpha;

    void main() {
        vec4 worldPos = vec4(aPos, 1.0);
        vec4 viewPos = uView * worldPos;
        gl_Position = uProjection * viewPos;
        Alpha = 1.0;
    }
    """;

    private static final String FRAG_GRID = """
    #version 410 core
    out vec4 frag;

    in float Alpha;

    void main() {
        frag = vec4(0.25, 0.30, 0.45, 0.28);
    }
    """;

    private static final String VERT_STAR = """
    #version 410 core
    layout(location=0) in vec3 aPos;
    layout(location=1) in float aSeed;

    uniform mat4 uView;
    uniform mat4 uProjection;
    uniform float uTime;

    out float Seed;
    out float Twinkle;

    void main() {
        Seed = aSeed;
        float phase = aSeed * 12.0 + dot(aPos, vec3(0.013, 0.017, 0.019));
        Twinkle = 0.55 + 0.45 * sin(uTime * (1.5 + aSeed * 4.0) + phase);
        gl_Position = uProjection * uView * vec4(aPos, 1.0);
        gl_PointSize = 1.4 + aSeed * 2.4;
    }
    """;

    private static final String FRAG_STAR = """
    #version 410 core
    out vec4 frag;

    in float Seed;
    in float Twinkle;

    void main() {
        vec2 p = gl_PointCoord * 2.0 - 1.0;
        float r = dot(p, p);
        float core = smoothstep(1.0, 0.0, r);
        float halo = smoothstep(1.0, 0.15, r);
        vec3 tint = mix(vec3(0.72, 0.82, 1.0), vec3(1.0, 0.95, 0.84), Seed);
        float glow = core * Twinkle;
        frag = vec4(tint * glow, glow * halo);
    }
    """;

    private void generateSun() {
        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int latSeg = 32, lonSeg = 32;
        float radius = 2.0f;

        for (int i = 0; i <= latSeg; i++) {
            float theta = (float) i * (float) Math.PI / latSeg;
            float sinT = (float) Math.sin(theta);
            float cosT = (float) Math.cos(theta);
            for (int j = 0; j <= lonSeg; j++) {
                float phi = (float) j * 2.0f * (float) Math.PI / lonSeg;
                vertices.add(radius * sinT * (float) Math.cos(phi));
                vertices.add(radius * cosT);
                vertices.add(radius * sinT * (float) Math.sin(phi));
            }
        }

        for (int i = 0; i < latSeg; i++) {
            for (int j = 0; j < lonSeg; j++) {
                int first = i * (lonSeg + 1) + j;
                int second = first + lonSeg + 1;
                indices.add(first);
                indices.add(second);
                indices.add(first + 1);
                indices.add(second);
                indices.add(second + 1);
                indices.add(first + 1);
            }
        }

        float[] va = new float[vertices.size()];
        for (int i = 0; i < va.length; i++) va[i] = vertices.get(i);
        int[] ia = new int[indices.size()];
        for (int i = 0; i < ia.length; i++) ia[i] = indices.get(i);

        sunVAO = glGenVertexArrays();
        sunVBO = glGenBuffers();
        sunEBO = glGenBuffers();
        glBindVertexArray(sunVAO);
        glBindBuffer(GL_ARRAY_BUFFER, sunVBO);
        glBufferData(GL_ARRAY_BUFFER, va, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sunEBO);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ia, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        sunIndexCount = ia.length;
    }

    private void generateGrid() {
        List<Float> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        float gridSize = 60.0f, step = 2.0f, y = -10.0f;
        int vc = 0;

        for (float z = -gridSize; z <= gridSize; z += step) {
            vertices.add(-gridSize);
            vertices.add(y);
            vertices.add(z);
            vertices.add(gridSize);
            vertices.add(y);
            vertices.add(z);
            indices.add(vc);
            indices.add(vc + 1);
            vc += 2;
        }

        for (float x = -gridSize; x <= gridSize; x += step) {
            vertices.add(x);
            vertices.add(y);
            vertices.add(-gridSize);
            vertices.add(x);
            vertices.add(y);
            vertices.add(gridSize);
            indices.add(vc);
            indices.add(vc + 1);
            vc += 2;
        }

        float[] va = new float[vertices.size()];
        for (int i = 0; i < va.length; i++) va[i] = vertices.get(i);
        int[] ia = new int[indices.size()];
        for (int i = 0; i < ia.length; i++) ia[i] = indices.get(i);

        gridVAO = glGenVertexArrays();
        int gridVBO = glGenBuffers();
        int gridEBO = glGenBuffers();
        glBindVertexArray(gridVAO);
        glBindBuffer(GL_ARRAY_BUFFER, gridVBO);
        glBufferData(GL_ARRAY_BUFFER, va, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gridEBO);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ia, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        gridIndexCount = ia.length;
    }

    private static class MeshData {
        List<Float> vertices = new ArrayList<>();
        List<Float> texCoords = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
    }

    private MeshData loadOBJ(String path) {
        MeshData mesh = new MeshData();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            List<Float> tv = new ArrayList<>();
            List<Float> tt = new ArrayList<>();
            List<Float> tn = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("v ")) {
                    String[] p = line.split("\\s+");
                    tv.add(Float.parseFloat(p[1]));
                    tv.add(Float.parseFloat(p[2]));
                    tv.add(Float.parseFloat(p[3]));
                } else if (line.startsWith("vt ")) {
                    String[] p = line.split("\\s+");
                    tt.add(Float.parseFloat(p[1]));
                    tt.add(1.0f - Float.parseFloat(p[2]));
                } else if (line.startsWith("vn ")) {
                    String[] p = line.split("\\s+");
                    tn.add(Float.parseFloat(p[1]));
                    tn.add(Float.parseFloat(p[2]));
                    tn.add(Float.parseFloat(p[3]));
                } else if (line.startsWith("f ")) {
                    String[] p = line.split("\\s+");
                    for (int i = 1; i <= 3; i++) {
                        String[] vd = p[i].split("/");
                        int vi = Integer.parseInt(vd[0]) - 1;
                        mesh.vertices.add(tv.get(vi * 3));
                        mesh.vertices.add(tv.get(vi * 3 + 1));
                        mesh.vertices.add(tv.get(vi * 3 + 2));

                        if (vd.length > 1 && !vd[1].isEmpty()) {
                            int ti = Integer.parseInt(vd[1]) - 1;
                            mesh.texCoords.add(tt.get(ti * 2));
                            mesh.texCoords.add(tt.get(ti * 2 + 1));
                        } else {
                            mesh.texCoords.add(0.0f);
                            mesh.texCoords.add(0.0f);
                        }

                        if (vd.length > 2 && !vd[2].isEmpty()) {
                            int ni = Integer.parseInt(vd[2]) - 1;
                            mesh.normals.add(tn.get(ni * 3));
                            mesh.normals.add(tn.get(ni * 3 + 1));
                            mesh.normals.add(tn.get(ni * 3 + 2));
                        } else {
                            mesh.normals.add(0.0f);
                            mesh.normals.add(1.0f);
                            mesh.normals.add(0.0f);
                        }

                        mesh.indices.add(mesh.indices.size());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return mesh;
    }

    private int createMesh(MeshData mesh) {
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = glGenBuffers();
        glBindVertexArray(vao);

        int n = mesh.vertices.size() / 3;
        float[] interleaved = new float[n * 8];
        for (int i = 0; i < n; i++) {
            interleaved[i * 8] = mesh.vertices.get(i * 3);
            interleaved[i * 8 + 1] = mesh.vertices.get(i * 3 + 1);
            interleaved[i * 8 + 2] = mesh.vertices.get(i * 3 + 2);
            interleaved[i * 8 + 3] = mesh.texCoords.get(i * 2);
            interleaved[i * 8 + 4] = mesh.texCoords.get(i * 2 + 1);
            interleaved[i * 8 + 5] = mesh.normals.get(i * 3);
            interleaved[i * 8 + 6] = mesh.normals.get(i * 3 + 1);
            interleaved[i * 8 + 7] = mesh.normals.get(i * 3 + 2);
        }

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, interleaved, GL_STATIC_DRAW);

        int[] idx = new int[mesh.indices.size()];
        for (int i = 0; i < mesh.indices.size(); i++) {
            idx[i] = mesh.indices.get(i);
        }

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 8 * Float.BYTES, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
        return vao;
    }

    private int loadTexture(String path) {
        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        try (MemoryStack st = stackPush()) {
            IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), ch = st.mallocInt(1);
            ByteBuffer data = stbi_load(path, w, h, ch, 4);
            if (data == null) {
                throw new RuntimeException(stbi_failure_reason());
            }
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            glGenerateMipmap(GL_TEXTURE_2D);
            stbi_image_free(data);
        }
        glBindTexture(GL_TEXTURE_2D, 0);
        return texID;
    }

    private int compileProgram(String vertSrc, String fragSrc, String name) {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertSrc);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException(glGetShaderInfoLog(vs));
        }

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragSrc);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException(glGetShaderInfoLog(fs));
        }

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException(glGetProgramInfoLog(prog));
        }

        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private void loadPlatform() {
        MeshData platformMesh = loadOBJ("platform.obj");
        platformVAO = createMesh(platformMesh);
        platformIndexCount = platformMesh.indices.size();
        texPlatform = loadTexture("platform.png");
    }

    private void renderPlatform(float[] view, float[] proj, float time) {
        glUseProgram(platformProg);

        glUniformMatrix4fv(uViewPlatform, false, view);
        glUniformMatrix4fv(uProjPlatform, false, proj);
        glUniform3f(uSunPosPlatform, 0.0f, 0.0f, 0.0f);
        glUniform3f(uViewPosPlatform, camX, camY, camZ);
        glUniform1f(uTimePlatform, time);

        float[] model = createModelMatrix(0, platformY, 0, 1.0f);
        glUniformMatrix4fv(uModelPlatform, false, model);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texPlatform);
        glUniform1i(uTexPlatform, 0);

        glBindVertexArray(platformVAO);
        glDrawElements(GL_TRIANGLES, platformIndexCount, GL_UNSIGNED_INT, 0);
    }

    private void renderPlanet(float[] view, float[] proj, float time, int vao, int indexCount, int texture, float x, float z, float rotation, float scale) {
        glUseProgram(planetProg);

        glUniformMatrix4fv(uViewPlanet, false, view);
        glUniformMatrix4fv(uProjPlanet, false, proj);
        glUniform3f(uSunPosPlanet, 0.0f, 0.0f, 0.0f);
        glUniform3f(uViewPosPlanet, camX, camY, camZ);
        glUniform1f(uTimePlanet, time);
        glUniformMatrix4fv(uModelPlanet, false, buildPlanetModel(x, z, rotation, scale));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(uTexPlanet, 0);

        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
    }

    private void renderAtmosphere(float[] view, float[] proj, float time, int vao, int indexCount, float x, float z, float rotation, float planetScale, float atmosphereScale, float r, float g, float b) {
        glUseProgram(atmosphereProg);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        glDepthMask(false);

        glUniformMatrix4fv(uViewAtmos, false, view);
        glUniformMatrix4fv(uProjAtmos, false, proj);
        glUniform3f(uSunPosAtmos, 0.0f, 0.0f, 0.0f);
        glUniform3f(uViewPosAtmos, camX, camY, camZ);
        glUniform3f(uColorAtmos, r, g, b);
        glUniform1f(uTimeAtmos, time);
        glUniform3f(uPlanetCenterAtmos, x, 0.0f, z);
        glUniform1f(uPlanetRadiusAtmos, planetScale);
        glUniform1f(uAtmosRadiusAtmos, atmosphereScale);
        glUniformMatrix4fv(uModelAtmos, false, buildPlanetModel(x, z, rotation, atmosphereScale));

        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);

        glDepthMask(true);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void generateStars() {
        Random rng = new Random(7L);
        int count = 1200;
        starCount = count;
        float[] data = new float[count * 4];

        for (int i = 0; i < count; i++) {
            float theta = (float) (rng.nextFloat() * Math.PI * 2.0);
            float u = rng.nextFloat() * 2.0f - 1.0f;
            float phi = (float) Math.acos(u);
            float radius = 180.0f + rng.nextFloat() * 120.0f;

            float sinPhi = (float) Math.sin(phi);
            float x = (float) (Math.cos(theta) * sinPhi) * radius;
            float y = (float) Math.cos(phi) * radius;
            float z = (float) (Math.sin(theta) * sinPhi) * radius;
            float seed = rng.nextFloat();

            int base = i * 4;
            data[base] = x;
            data[base + 1] = y;
            data[base + 2] = z;
            data[base + 3] = seed;
        }

        starVAO = glGenVertexArrays();
        starVBO = glGenBuffers();
        glBindVertexArray(starVAO);
        glBindBuffer(GL_ARRAY_BUFFER, starVBO);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 1, GL_FLOAT, false, 4 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException();

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Simulation", NULL, NULL);
        if (window == NULL) throw new RuntimeException();

        try (MemoryStack st = stackPush()) {
            IntBuffer pw = st.mallocInt(1), ph = st.mallocInt(1);
            glfwGetWindowSize(window, pw, ph);
            GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window, (vm.width() - pw.get(0)) / 2, (vm.height() - ph.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_LINE_SMOOTH);
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_PROGRAM_POINT_SIZE);

        initAudio();

        texMustafar = loadTexture("mustafar.png");
        texKashyyyk = loadTexture("kashyyyk.png");

        MeshData mustafarMesh = loadOBJ("mustafar.obj");
        mustafarVAO = createMesh(mustafarMesh);
        mustafarIndexCount = mustafarMesh.indices.size();

        MeshData kashyyykMesh = loadOBJ("kashyyyk.obj");
        kashyyykVAO = createMesh(kashyyykMesh);
        kashyyykIndexCount = kashyyykMesh.indices.size();

        loadPlatform();
        generateSun();
        generateGrid();
        generateStars();

        planetProg = compileProgram(VERT_PLANET, FRAG_PLANET, "Planet");
        atmosphereProg = compileProgram(VERT_PLANET, FRAG_ATMOSPHERE, "Atmosphere");
        platformProg = compileProgram(VERT_PLATFORM, FRAG_PLATFORM, "Platform");
        sunProg = compileProgram(VERT_SUN, FRAG_SUN, "Sun");
        gridProg = compileProgram(VERT_GRID, FRAG_GRID, "Grid");
        starProg = compileProgram(VERT_STAR, FRAG_STAR, "Stars");

        glUseProgram(planetProg);
        uModelPlanet = glGetUniformLocation(planetProg, "uModel");
        uViewPlanet = glGetUniformLocation(planetProg, "uView");
        uProjPlanet = glGetUniformLocation(planetProg, "uProjection");
        uTexPlanet = glGetUniformLocation(planetProg, "uTexture");
        uSunPosPlanet = glGetUniformLocation(planetProg, "uSunPos");
        uViewPosPlanet = glGetUniformLocation(planetProg, "uViewPos");
        uTimePlanet = glGetUniformLocation(planetProg, "uTime");

        glUseProgram(atmosphereProg);
        uModelAtmos = glGetUniformLocation(atmosphereProg, "uModel");
        uViewAtmos = glGetUniformLocation(atmosphereProg, "uView");
        uProjAtmos = glGetUniformLocation(atmosphereProg, "uProjection");
        uSunPosAtmos = glGetUniformLocation(atmosphereProg, "uSunPos");
        uViewPosAtmos = glGetUniformLocation(atmosphereProg, "uViewPos");
        uColorAtmos = glGetUniformLocation(atmosphereProg, "uAtmosphereColor");
        uTimeAtmos = glGetUniformLocation(atmosphereProg, "uTime");
        uPlanetCenterAtmos = glGetUniformLocation(atmosphereProg, "uPlanetCenter");
        uPlanetRadiusAtmos = glGetUniformLocation(atmosphereProg, "uPlanetRadius");
        uAtmosRadiusAtmos = glGetUniformLocation(atmosphereProg, "uAtmosphereRadius");

        glUseProgram(platformProg);
        uModelPlatform = glGetUniformLocation(platformProg, "uModel");
        uViewPlatform = glGetUniformLocation(platformProg, "uView");
        uProjPlatform = glGetUniformLocation(platformProg, "uProjection");
        uTexPlatform = glGetUniformLocation(platformProg, "uTexture");
        uSunPosPlatform = glGetUniformLocation(platformProg, "uSunPos");
        uViewPosPlatform = glGetUniformLocation(platformProg, "uViewPos");
        uTimePlatform = glGetUniformLocation(platformProg, "uTime");

        glUseProgram(sunProg);
        uModelSun = glGetUniformLocation(sunProg, "uModel");
        uViewSun = glGetUniformLocation(sunProg, "uView");
        uProjSun = glGetUniformLocation(sunProg, "uProjection");
        uTimeSun = glGetUniformLocation(sunProg, "uTime");

        glUseProgram(gridProg);
        uViewGrid = glGetUniformLocation(gridProg, "uView");
        uProjGrid = glGetUniformLocation(gridProg, "uProjection");
        uCamPosGrid = glGetUniformLocation(gridProg, "uCamPos");

        glUseProgram(starProg);
        uViewStar = glGetUniformLocation(starProg, "uView");
        uProjStar = glGetUniformLocation(starProg, "uProjection");
        uTimeStar = glGetUniformLocation(starProg, "uTime");

        glUseProgram(0);

        startTime = glfwGetTime();

        glfwSetKeyCallback(window, (win, key, sc, action, mods) -> {
            boolean dn = action != GLFW_RELEASE;
            if (key == GLFW_KEY_W) wDown = dn;
            if (key == GLFW_KEY_S) sDown = dn;
            if (key == GLFW_KEY_A) aDown = dn;
            if (key == GLFW_KEY_D) dDown = dn;
            if (key == GLFW_KEY_SPACE) spDown = dn;
            if (key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT) shDown = dn;
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                if (locked) {
                    glfwSetInputMode(win, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                    locked = false;
                } else {
                    glfwSetWindowShouldClose(win, true);
                }
            }
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS && !locked) {
                glfwSetInputMode(win, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                locked = true;
                firstMouse = true;
            }
        });

        glfwSetCursorPosCallback(window, (win, mx, my) -> {
            if (!locked) return;
            if (firstMouse) {
                lastMouseX = mx;
                lastMouseY = my;
                firstMouse = false;
                return;
            }
            double dx = mx - lastMouseX, dy = my - lastMouseY;
            lastMouseX = mx;
            lastMouseY = my;
            float sens = 0.0015f;
            yaw += (float) (dx * sens);
            pitch -= (float) (dy * sens);
            pitch = Math.max(-(float) Math.PI / 2 + 0.01f, Math.min((float) Math.PI / 2 - 0.01f, pitch));
        });
    }

    private void loop() {
        glClearColor(0.0f, 0.0f, 0.02f, 1.0f);

        double prev = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) (now - prev);
            prev = now;
            float t = (float) (now - startTime);

            orbitAngleMus += dt * 0.5;
            orbitAngleKas += dt * 0.3;
            musRotation += dt * 0.8;
            kasRotation += dt * 0.5;

            float orbitRadiusMus = 12.0f;
            float orbitRadiusKas = 22.0f;
            musX = (float) (Math.cos(orbitAngleMus) * orbitRadiusMus);
            musZ = (float) (Math.sin(orbitAngleMus) * orbitRadiusMus);
            kasX = (float) (Math.cos(orbitAngleKas) * orbitRadiusKas);
            kasZ = (float) (Math.sin(orbitAngleKas) * orbitRadiusKas);

            float speed = 10.0f * dt;
            float fwdX = (float) Math.sin(yaw), fwdZ = -(float) Math.cos(yaw);
            float rtX = (float) Math.cos(yaw), rtZ = (float) Math.sin(yaw);

            if (wDown) {
                camX += fwdX * speed;
                camZ += fwdZ * speed;
            }
            if (sDown) {
                camX -= fwdX * speed;
                camZ -= fwdZ * speed;
            }
            if (aDown) {
                camX -= rtX * speed;
                camZ -= rtZ * speed;
            }
            if (dDown) {
                camX += rtX * speed;
                camZ += rtZ * speed;
            }
            if (spDown) camY += speed;
            if (shDown) camY -= speed;

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            float[] view = createViewMatrix();
            float[] proj = createProjectionMatrix();
            float[] starView = createStarViewMatrix();

            renderStars(starView, proj, t);

            glUseProgram(gridProg);
            glUniformMatrix4fv(uViewGrid, false, view);
            glUniformMatrix4fv(uProjGrid, false, proj);
            glUniform3f(uCamPosGrid, camX, camY, camZ);
            glBindVertexArray(gridVAO);
            glDrawElements(GL_LINES, gridIndexCount, GL_UNSIGNED_INT, 0);

            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS);
            glDepthMask(true);

            glUseProgram(sunProg);
            glUniformMatrix4fv(uViewSun, false, view);
            glUniformMatrix4fv(uProjSun, false, proj);
            glUniform1f(uTimeSun, t);
            glUniformMatrix4fv(uModelSun, false, createModelMatrix(0, 0, 0, 2.0f));
            glBindVertexArray(sunVAO);
            glDrawElements(GL_TRIANGLES, sunIndexCount, GL_UNSIGNED_INT, 0);

            renderPlatform(view, proj, t);

            renderPlanet(view, proj, t, mustafarVAO, mustafarIndexCount, texMustafar, musX, musZ, musRotation, 1.55f);
            renderAtmosphere(view, proj, t, mustafarVAO, mustafarIndexCount, musX, musZ, musRotation, 1.55f, 1.68f, 1.0f, 0.12f, 0.04f);

            renderPlanet(view, proj, t, kashyyykVAO, kashyyykIndexCount, texKashyyyk, kasX, kasZ, kasRotation, 1.75f);
            renderAtmosphere(view, proj, t, kashyyykVAO, kashyyykIndexCount, kasX, kasZ, kasRotation, 1.75f, 1.90f, 0.18f, 0.48f, 1.0f);

            glBindVertexArray(0);
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void renderStars(float[] view, float[] proj, float time) {
        glUseProgram(starProg);

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);

        glUniformMatrix4fv(uViewStar, false, view);
        glUniformMatrix4fv(uProjStar, false, proj);
        glUniform1f(uTimeStar, time);

        glBindVertexArray(starVAO);
        glDrawArrays(GL_POINTS, 0, starCount);

        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private float[] buildPlanetModel(float x, float z, float rotation, float scale) {
    float[] m = new float[16];
    new Matrix4f().translate(x, 0.0f, z).rotateY(rotation).scale(scale).get(m);
    return m;
}

    private float[] createViewMatrix() {
    float[] v = new float[16];
    float fwdX = (float) (Math.sin(yaw) * Math.cos(pitch));
    float fwdY = (float) Math.sin(pitch);
    float fwdZ = (float) (-Math.cos(yaw) * Math.cos(pitch));
    new Matrix4f().lookAt(
        camX, camY, camZ,
        camX + fwdX, camY + fwdY, camZ + fwdZ,
        0.0f, 1.0f, 0.0f
    ).get(v);
    return v;
}

private float[] createStarViewMatrix() {
    float[] v = new float[16];
    float fwdX = (float) (Math.sin(yaw) * Math.cos(pitch));
    float fwdY = (float) Math.sin(pitch);
    float fwdZ = (float) (-Math.cos(yaw) * Math.cos(pitch));
    new Matrix4f().lookAt(
        0.0f, 0.0f, 0.0f,
        fwdX, fwdY, fwdZ,
        0.0f, 1.0f, 0.0f
    ).get(v);
    return v;
}

private float[] createProjectionMatrix() {
    float[] p = new float[16];
    new Matrix4f().perspective(FOV, (float) WIDTH / HEIGHT, 0.1f, 200.0f).get(p);
    return p;
}

private float[] createModelMatrix(float x, float y, float z, float scale) {
    float[] m = new float[16];
    new Matrix4f().translate(x, y, z).scale(scale).get(m);
    return m;
}

    private void initAudio() {
        try {
            File audioFile = new File("space_audio.wav");
            if (!audioFile.exists()) {
                audioFile = new File("space_audio.mp3");
            }
            if (!audioFile.exists()) {
                return;
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = audioStream.getFormat();

            if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                AudioFormat pcmFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        format.getSampleRate(),
                        16,
                        format.getChannels(),
                        format.getChannels() * 2,
                        format.getSampleRate(),
                        false
                );
                audioStream = AudioSystem.getAudioInputStream(pcmFormat, audioStream);
            }

            backgroundMusic = AudioSystem.getClip();
            backgroundMusic.open(audioStream);
            backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);

            FloatControl volume = (FloatControl) backgroundMusic.getControl(FloatControl.Type.MASTER_GAIN);
            volume.setValue(-10.0f);
            backgroundMusic.start();

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private void cleanupAudio() {
        if (backgroundMusic != null) {
            backgroundMusic.stop();
            backgroundMusic.close();
        }
    }

    private void cleanup() {
        glDeleteTextures(texMustafar);
        glDeleteTextures(texKashyyyk);
        glDeleteTextures(texPlatform);
        glDeleteProgram(planetProg);
        glDeleteProgram(atmosphereProg);
        glDeleteProgram(platformProg);
        glDeleteProgram(sunProg);
        glDeleteProgram(gridProg);
        glDeleteProgram(starProg);
        glDeleteVertexArrays(mustafarVAO);
        glDeleteVertexArrays(kashyyykVAO);
        glDeleteVertexArrays(platformVAO);
        glDeleteVertexArrays(sunVAO);
        glDeleteVertexArrays(gridVAO);
        glDeleteVertexArrays(starVAO);
        glDeleteBuffers(sunVBO);
        glDeleteBuffers(sunEBO);
        glDeleteBuffers(starVBO);
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
        cleanupAudio();
    }

    public static void main(String[] args) {
        new Simulation().run();
    }
}
