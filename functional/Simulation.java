// Compile the program
// javac -cp "../../../lwjgl-release-3.3.6-custom/*" Simulation.java
// Run the program
// java -cp ".;../../../lwjgl-release-3.3.6-custom/*" Simulation

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

public class Simulation {
    private static final int   WIDTH  = 1280;
    private static final int   HEIGHT = 720;
    private static final float FOV    = 0.4663f;

    private long window;
    private int planetProg, sunProg, gridProg;
    private int texMustafar, texKashyyyk;
    private int sunVAO, sunVBO, sunEBO, sunIndexCount;

    // Planet meshes
    private int mustafarVAO, mustafarIndexCount;
    private int kashyyykVAO, kashyyykIndexCount;

    // Grid mesh
    private int gridVAO, gridIndexCount;

    // Planet positions and rotations
    private float musX = 9.0f,  musZ = 0.0f;
    private float kasX = 16.5f, kasZ = 0.0f;
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

    public void run() { init(); loop(); cleanup(); }

    // -------------------------------------------------------------------------
    // Shaders
    // -------------------------------------------------------------------------

    private static final String VERT_PLANET = """
            #version 410 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aTexCoord;

            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;

            out vec2 TexCoord;
            out vec3 FragPos;

            void main() {
                TexCoord    = aTexCoord;
                FragPos     = vec3(uModel * vec4(aPos, 1.0));
                gl_Position = uProjection * uView * vec4(FragPos, 1.0);
            }
            """;

    private static final String FRAG_PLANET = """
            #version 410 core
            out vec4 frag;

            in vec2 TexCoord;
            in vec3 FragPos;

            uniform sampler2D uTexture;
            uniform vec3  uSunPos;        // sun world-space position (0,0,0)
            uniform vec3  uPlanetCenter;  // this planet's world-space centre
            uniform vec3  uViewPos;
            uniform float uTime;

            void main() {
                vec4 texColor = texture(uTexture, TexCoord);

                // Surface normal = direction from planet centre to fragment
                // This is independent of axial spin, so the shadow terminator
                // always tracks the sun correctly.
                vec3 N = normalize(FragPos - uPlanetCenter);

                vec3 L = normalize(uSunPos - FragPos);   // frag -> sun
                vec3 V = normalize(uViewPos - FragPos);  // frag -> camera
                vec3 R = reflect(-L, N);

                float ambient = 0.05;
                float diff    = max(dot(N, L), 0.0);
                float spec    = pow(max(dot(V, R), 0.0), 32.0) * 0.4;
                // Rim only on the lit side
                float rim     = pow(1.0 - max(dot(V, N), 0.0), 2.0) * 0.15 * diff;

                vec3 lit = texColor.rgb * (ambient + diff)
                         + vec3(0.8, 0.7, 0.5) * spec
                         + vec3(0.3, 0.2, 0.1) * rim;

                frag = vec4(lit * 1.1, 1.0);
            }
            """;

    private static final String VERT_SUN = """
            #version 410 core
            layout(location=0) in vec3 aPos;

            uniform mat4  uModel;
            uniform mat4  uView;
            uniform mat4  uProjection;
            uniform float uTime;

            out vec3 FragPos;

            void main() {
                FragPos     = vec3(uModel * vec4(aPos, 1.0));
                gl_Position = uProjection * uView * vec4(FragPos, 1.0);
            }
            """;

    private static final String FRAG_SUN = """
            #version 410 core
            out vec4 frag;

            in vec3  FragPos;
            uniform float uTime;

            void main() {
                float pulse  = 0.8 + 0.2 * sin(uTime * 2.0);
                vec3 sunColor = vec3(1.0, 0.7, 0.3) * pulse;
                sunColor += vec3(0.2, 0.1, 0.0) * sin(FragPos.xyx * 10.0 + uTime * 5.0);
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
            
            // Always full opacity
            Alpha = 1.0;
        }
        """;

private static final String FRAG_GRID = """
        #version 410 core
        out vec4 frag;
        
        in float Alpha;
        
        void main() {
            frag = vec4(0.9, 0.9, 1.0, 1.0);
        }
        """;
    // -------------------------------------------------------------------------
    // Geometry generation
    // -------------------------------------------------------------------------

    private void generateSun() {
        List<Float>   vertices = new ArrayList<>();
        List<Integer> indices  = new ArrayList<>();

        int   latSeg = 32, lonSeg = 32;
        float radius = 2.0f;

        for (int i = 0; i <= latSeg; i++) {
            float theta = (float) i * (float) Math.PI / latSeg;
            float sinT  = (float) Math.sin(theta);
            float cosT  = (float) Math.cos(theta);
            for (int j = 0; j <= lonSeg; j++) {
                float phi  = (float) j * 2.0f * (float) Math.PI / lonSeg;
                vertices.add(radius * sinT * (float) Math.cos(phi));
                vertices.add(radius * cosT);
                vertices.add(radius * sinT * (float) Math.sin(phi));
            }
        }
        for (int i = 0; i < latSeg; i++) {
            for (int j = 0; j < lonSeg; j++) {
                int first  = i * (lonSeg + 1) + j;
                int second = first + lonSeg + 1;
                indices.add(first);  indices.add(second);    indices.add(first + 1);
                indices.add(second); indices.add(second + 1); indices.add(first + 1);
            }
        }

        float[] va = new float[vertices.size()];
        for (int i = 0; i < va.length; i++) va[i] = vertices.get(i);
        int[]   ia = new int[indices.size()];
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
        List<Float>   vertices = new ArrayList<>();
        List<Integer> indices  = new ArrayList<>();

        float gridSize = 60.0f, step = 2.0f, y = -10.0f;
        int   vc = 0;

        for (float z = -gridSize; z <= gridSize; z += step) {
            vertices.add(-gridSize); vertices.add(y); vertices.add(z);
            vertices.add( gridSize); vertices.add(y); vertices.add(z);
            indices.add(vc); indices.add(vc + 1); vc += 2;
        }
        for (float x = -gridSize; x <= gridSize; x += step) {
            vertices.add(x); vertices.add(y); vertices.add(-gridSize);
            vertices.add(x); vertices.add(y); vertices.add( gridSize);
            indices.add(vc); indices.add(vc + 1); vc += 2;
        }

        float[] va = new float[vertices.size()];
        for (int i = 0; i < va.length; i++) va[i] = vertices.get(i);
        int[]   ia = new int[indices.size()];
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

    // -------------------------------------------------------------------------
    // OBJ loader
    // -------------------------------------------------------------------------

    private static class MeshData {
        List<Float>   vertices  = new ArrayList<>();
        List<Float>   texCoords = new ArrayList<>();
        List<Integer> indices   = new ArrayList<>();
    }

    private MeshData loadOBJ(String path) {
        MeshData mesh = new MeshData();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            List<Float> tv = new ArrayList<>(), tt = new ArrayList<>();
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
                } else if (line.startsWith("f ")) {
                    String[] p = line.split("\\s+");
                    for (int i = 1; i <= 3; i++) {
                        String[] vd  = p[i].split("/");
                        int      vi  = Integer.parseInt(vd[0]) - 1;
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
                        mesh.indices.add(mesh.indices.size());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not load OBJ: " + path, e);
        }
        return mesh;
    }

    private int createMesh(MeshData mesh) {
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = glGenBuffers();
        glBindVertexArray(vao);

        int    n          = mesh.vertices.size() / 3;
        float[] interleaved = new float[n * 5];
        for (int i = 0; i < n; i++) {
            interleaved[i * 5]     = mesh.vertices.get(i * 3);
            interleaved[i * 5 + 1] = mesh.vertices.get(i * 3 + 1);
            interleaved[i * 5 + 2] = mesh.vertices.get(i * 3 + 2);
            interleaved[i * 5 + 3] = mesh.texCoords.get(i * 2);
            interleaved[i * 5 + 4] = mesh.texCoords.get(i * 2 + 1);
        }
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, interleaved, GL_STATIC_DRAW);

        int[] idx = mesh.indices.stream().mapToInt(i -> i).toArray();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
        return vao;
    }

    // -------------------------------------------------------------------------
    // Texture loader
    // -------------------------------------------------------------------------

    private int loadTexture(String path) {
        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S,       GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T,       GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER,   GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER,   GL_LINEAR);
        try (MemoryStack st = stackPush()) {
            IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), ch = st.mallocInt(1);
            ByteBuffer data = stbi_load(path, w, h, ch, 4);
            if (data == null)
                throw new RuntimeException("Failed to load texture: " + path
                        + "\n" + stbi_failure_reason());
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0),
                    0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            glGenerateMipmap(GL_TEXTURE_2D);
            stbi_image_free(data);
        }
        glBindTexture(GL_TEXTURE_2D, 0);
        System.out.println("Loaded texture: " + path);
        return texID;
    }

    // -------------------------------------------------------------------------
    // Shader compiler helper
    // -------------------------------------------------------------------------

    private int compileProgram(String vertSrc, String fragSrc, String name) {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertSrc); glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException(name + " vert: " + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragSrc); glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException(name + " frag: " + glGetShaderInfoLog(fs));

        int prog = glCreateProgram();
        glAttachShader(prog, vs); glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException(name + " link: " + glGetProgramInfoLog(prog));

        glDeleteShader(vs); glDeleteShader(fs);
        return prog;
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW failed");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE,        GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE,             GLFW_FALSE);

        window = glfwCreateWindow(WIDTH, HEIGHT,
                "Star Wars System - Mustafar & Kashyyyk", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Window failed");

        try (MemoryStack st = stackPush()) {
            IntBuffer pw = st.mallocInt(1), ph = st.mallocInt(1);
            glfwGetWindowSize(window, pw, ph);
            GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window,
                    (vm.width()  - pw.get(0)) / 2,
                    (vm.height() - ph.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);   glCullFace(GL_BACK);
        glEnable(GL_LINE_SMOOTH); glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);
        glEnable(GL_BLEND);       glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        texMustafar = loadTexture("mustafar.png");
        texKashyyyk = loadTexture("kashyyyk.png");

        System.out.println("Loading mustafar.obj...");
        MeshData mustafarMesh = loadOBJ("mustafar.obj");
        mustafarVAO        = createMesh(mustafarMesh);
        mustafarIndexCount = mustafarMesh.indices.size();

        System.out.println("Loading kashyyyk.obj...");
        MeshData kashyyykMesh = loadOBJ("kashyyyk.obj");
        kashyyykVAO        = createMesh(kashyyykMesh);
        kashyyykIndexCount = kashyyykMesh.indices.size();

        generateSun();
        generateGrid();

        planetProg = compileProgram(VERT_PLANET, FRAG_PLANET, "Planet");
        sunProg    = compileProgram(VERT_SUN,    FRAG_SUN,    "Sun");
        gridProg   = compileProgram(VERT_GRID,   FRAG_GRID,   "Grid");

        startTime = glfwGetTime();

        // --- Input callbacks ---
        glfwSetKeyCallback(window, (win, key, sc, action, mods) -> {
            boolean dn = action != GLFW_RELEASE;
            if (key == GLFW_KEY_W) wDown = dn;
            if (key == GLFW_KEY_S) sDown = dn;
            if (key == GLFW_KEY_A) aDown = dn;
            if (key == GLFW_KEY_D) dDown = dn;
            if (key == GLFW_KEY_SPACE)      spDown = dn;
            if (key == GLFW_KEY_LEFT_SHIFT  || key == GLFW_KEY_RIGHT_SHIFT) shDown = dn;
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                if (locked) { glfwSetInputMode(win, GLFW_CURSOR, GLFW_CURSOR_NORMAL); locked = false; }
                else glfwSetWindowShouldClose(win, true);
            }
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS && !locked) {
                glfwSetInputMode(win, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                locked = true; firstMouse = true;
            }
        });

        glfwSetCursorPosCallback(window, (win, mx, my) -> {
            if (!locked) return;
            if (firstMouse) { lastMouseX = mx; lastMouseY = my; firstMouse = false; return; }
            double dx = mx - lastMouseX, dy = my - lastMouseY;
            lastMouseX = mx; lastMouseY = my;
            float sens = 0.0015f;
            yaw   += (float)(dx * sens);
            pitch -= (float)(dy * sens);
            pitch  = Math.max(-(float)Math.PI / 2 + 0.01f,
                     Math.min( (float)Math.PI / 2 - 0.01f, pitch));
        });
    }

    // -------------------------------------------------------------------------
    // Main loop
    // -------------------------------------------------------------------------

    private void loop() {
        glClearColor(0.0f, 0.0f, 0.02f, 1.0f);

        // --- Uniform locations ---
        int pModel   = glGetUniformLocation(planetProg, "uModel");
        int pView    = glGetUniformLocation(planetProg, "uView");
        int pProj    = glGetUniformLocation(planetProg, "uProjection");
        int pTex     = glGetUniformLocation(planetProg, "uTexture");
        int pSunPos  = glGetUniformLocation(planetProg, "uSunPos");
        int pCenter  = glGetUniformLocation(planetProg, "uPlanetCenter");
        int pViewPos = glGetUniformLocation(planetProg, "uViewPos");
        int pTime    = glGetUniformLocation(planetProg, "uTime");

        int sModel = glGetUniformLocation(sunProg, "uModel");
        int sView  = glGetUniformLocation(sunProg, "uView");
        int sProj  = glGetUniformLocation(sunProg, "uProjection");
        int sTime  = glGetUniformLocation(sunProg, "uTime");

        int gView   = glGetUniformLocation(gridProg, "uView");
        int gProj   = glGetUniformLocation(gridProg, "uProjection");
        int gCamPos = glGetUniformLocation(gridProg, "uCamPos");

        double prev = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float  dt  = (float)(now - prev); prev = now;
            float  t   = (float)(now - startTime);

            // --- Update orbits & axial rotations ---
            orbitAngleMus += dt * 0.5;
            orbitAngleKas += dt * 0.3;
            musRotation   += dt * 0.8;
            kasRotation   += dt * 0.5;

            musX = (float)(Math.cos(orbitAngleMus) * 9.0);
            musZ = (float)(Math.sin(orbitAngleMus) * 9.0);
            kasX = (float)(Math.cos(orbitAngleKas) * 16.5);
            kasZ = (float)(Math.sin(orbitAngleKas) * 16.5);

            // --- Camera movement ---
            float speed = 10.0f * dt;
            float fwdX  = (float) Math.sin(yaw),  fwdZ = -(float) Math.cos(yaw);
            float rtX   = (float) Math.cos(yaw),  rtZ  =  (float) Math.sin(yaw);
            if (wDown)  { camX += fwdX * speed; camZ += fwdZ * speed; }
            if (sDown)  { camX -= fwdX * speed; camZ -= fwdZ * speed; }
            if (aDown)  { camX -= rtX  * speed; camZ -= rtZ  * speed; }
            if (dDown)  { camX += rtX  * speed; camZ += rtZ  * speed; }
            if (spDown)  camY += speed;
            if (shDown)  camY -= speed;

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            float[] view = createViewMatrix();
            float[] proj = createProjectionMatrix();

            // --- Grid ---
            glUseProgram(gridProg);
            glUniformMatrix4fv(gView, false, view);
            glUniformMatrix4fv(gProj, false, proj);
            glUniform3f(gCamPos, camX, camY, camZ);
            glBindVertexArray(gridVAO);
            glDrawElements(GL_LINES, gridIndexCount, GL_UNSIGNED_INT, 0);

            // --- Sun ---
            glUseProgram(sunProg);
            glUniformMatrix4fv(sView, false, view);
            glUniformMatrix4fv(sProj, false, proj);
            glUniform1f(sTime, t);
            glUniformMatrix4fv(sModel, false, createModelMatrix(0, 0, 0, 2.0f));
            glBindVertexArray(sunVAO);
            glDrawElements(GL_TRIANGLES, sunIndexCount, GL_UNSIGNED_INT, 0);

            // --- Planets (shared uniforms) ---
            glUseProgram(planetProg);
            glUniformMatrix4fv(pView,    false, view);
            glUniformMatrix4fv(pProj,    false, proj);
            glUniform3f(pSunPos,  0.0f, 0.0f, 0.0f);   // sun is always at origin
            glUniform3f(pViewPos, camX, camY, camZ);
            glUniform1f(pTime,    t);

            // --- Mustafar ---
            // uPlanetCenter must match the translation in uModel exactly
            glUniform3f(pCenter, musX, 0.0f, musZ);
            glUniformMatrix4fv(pModel, false, buildPlanetModel(musX, musZ, musRotation, 1.55f));
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texMustafar);
            glUniform1i(pTex, 0);
            glBindVertexArray(mustafarVAO);
            glDrawElements(GL_TRIANGLES, mustafarIndexCount, GL_UNSIGNED_INT, 0);

            // --- Kashyyyk ---
            // uPlanetCenter updated here with Kashyyyk's position
            glUniform3f(pCenter, kasX, 0.0f, kasZ);
            glUniformMatrix4fv(pModel, false, buildPlanetModel(kasX, kasZ, kasRotation, 1.75f));
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texKashyyyk);
            glUniform1i(pTex, 0);
            glBindVertexArray(kashyyykVAO);
            glDrawElements(GL_TRIANGLES, kashyyykIndexCount, GL_UNSIGNED_INT, 0);

            glBindVertexArray(0);
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    // -------------------------------------------------------------------------
    // Matrix helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a column-major model matrix for a planet:
     *   - Y-axis rotation by `rotation`
     *   - Uniform scale
     *   - Translation to (x, 0, z)
     *
     * Column-major layout (OpenGL convention):
     *   col0 = [m0,  m1,  m2,  m3 ]
     *   col1 = [m4,  m5,  m6,  m7 ]
     *   col2 = [m8,  m9,  m10, m11]
     *   col3 = [m12, m13, m14, m15]
     */
    private float[] buildPlanetModel(float x, float z, float rotation, float scale) {
        float c = (float) Math.cos(rotation) * scale;
        float s = (float) Math.sin(rotation) * scale;

        float[] m = new float[16];
        // col 0  (X basis scaled)
        m[0]  =  c;     // right-X
        m[1]  =  0;
        m[2]  = -s;     // right-Z  (note: row 2 of col 0 = -sin for Y-rot)
        m[3]  =  0;
        m[4]  =  0;
        m[5]  =  scale;
        m[6]  =  0;
        m[7]  =  0;
        m[8]  =  s;     // forward-X
        m[9]  =  0;
        m[10] =  c;     // forward-Z
        m[11] =  0;
        m[12] =  x;
        m[13] =  0;
        m[14] =  z;
        m[15] =  1;
        return m;
    }

    private float[] createViewMatrix() {
        float[] forward = {
            (float)(Math.sin(yaw) * Math.cos(pitch)),
            (float)(Math.sin(pitch)),
            (float)(-Math.cos(yaw) * Math.cos(pitch))
        };

        // right = normalize(cross(forward, worldUp))
        float[] right = {
             forward[1] * 0 - forward[2] * 1,   // cross with (0,1,0)
             forward[2] * 0 - forward[0] * 0,
             forward[0] * 1 - forward[1] * 0
        };
        float rLen = (float) Math.sqrt(right[0]*right[0] + right[1]*right[1] + right[2]*right[2]);
        right[0] /= rLen; right[1] /= rLen; right[2] /= rLen;

        // up = cross(right, forward)
        float[] up = {
            right[1]*forward[2] - right[2]*forward[1],
            right[2]*forward[0] - right[0]*forward[2],
            right[0]*forward[1] - right[1]*forward[0]
        };

        // Column-major lookAt matrix
        float[] v = new float[16];
        v[0] =  right[0];    v[4] =  right[1];    v[8]  =  right[2];
        v[1] =  up[0];       v[5] =  up[1];       v[9]  =  up[2];
        v[2] = -forward[0];  v[6] = -forward[1];  v[10] = -forward[2];
        v[3] = 0;            v[7] = 0;            v[11] = 0;
        v[12] = -(right[0]*camX   + right[1]*camY   + right[2]*camZ);
        v[13] = -(up[0]*camX      + up[1]*camY      + up[2]*camZ);
        v[14] =  (forward[0]*camX + forward[1]*camY + forward[2]*camZ);
        v[15] = 1;
        return v;
    }

    private float[] createProjectionMatrix() {
        float aspect      = (float) WIDTH / HEIGHT;
        float near        = 0.1f, far = 200.0f;
        float tanHalfFOV  = (float) Math.tan(FOV / 2.0);
        float[] p = new float[16];
        p[0]  = 1.0f / (aspect * tanHalfFOV);
        p[5]  = 1.0f / tanHalfFOV;
        p[10] = -(far + near) / (far - near);
        p[11] = -1;
        p[14] = -(2.0f * far * near) / (far - near);
        return p;
    }

    private float[] createModelMatrix(float x, float y, float z, float scale) {
        float[] m = new float[16];
        m[0] = scale; m[5] = scale; m[10] = scale; m[15] = 1;
        m[12] = x; m[13] = y; m[14] = z;
        return m;
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    private void cleanup() {
        glDeleteTextures(texMustafar);
        glDeleteTextures(texKashyyyk);
        glDeleteProgram(planetProg);
        glDeleteProgram(sunProg);
        glDeleteProgram(gridProg);
        glDeleteVertexArrays(mustafarVAO);
        glDeleteVertexArrays(kashyyykVAO);
        glDeleteVertexArrays(sunVAO);
        glDeleteVertexArrays(gridVAO);
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    public static void main(String[] args) { new Simulation().run(); }
}
