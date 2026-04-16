// Compile the program
// javac -cp "../../../lwjgl-release-3.3.6-custom/*" Simulation.java
// Run the program
// java -cp ".;../../../lwjgl-release-3.3.6-custom/*" Simulation

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.assimp.*;

import java.nio.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.assimp.Assimp.*;

public class Simulation {
    private static final int   WIDTH  = 1280;
    private static final int   HEIGHT = 720;
    private static final float FOV    = 0.4663f;

    private long window;
    private int prog, vao, vbo;
    private int texMustafar, texKashyyyk;
    
    // Planet meshes
    private int mustafarVAO, mustafarVBO, mustafarEBO, mustafarIndexCount;
    private int kashyyykVAO, kashyyykVBO, kashyyykEBO, kashyyykIndexCount;
    
    // Planet positions
    private float musX = 9.0f, musZ = 0.0f;
    private float kasX = 16.5f, kasZ = 0.0f;
    private double orbitAngleMus = 0.0;
    private double orbitAngleKas = 2.1;
    
    private double startTime;

    private float camX = 0, camY = 2, camZ = 30;
    private float yaw = 0, pitch = 0;

    private boolean wDown, aDown, sDown, dDown, spDown, shDown;
    private boolean locked = false;
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    public void run() { init(); loop(); cleanup(); }

    // Vertex shader for rendering OBJ meshes
    private static final String VERT = """
            #version 410 core
            layout(location=0) in vec3 aPos;
            layout(location=1) in vec2 aTexCoord;
            
            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;
            
            out vec2 TexCoord;
            out vec3 FragPos;
            out vec3 Normal;
            
            void main() {
                TexCoord = aTexCoord;
                FragPos = vec3(uModel * vec4(aPos, 1.0));
                gl_Position = uProjection * uView * vec4(FragPos, 1.0);
            }
            """;

    // Fragment shader for planets
    private static final String FRAG = """
            #version 410 core
            out vec4 frag;
            
            in vec2 TexCoord;
            in vec3 FragPos;
            
            uniform sampler2D uTexture;
            uniform vec3 uLightPos;
            uniform vec3 uViewPos;
            uniform float uTime;
            
            void main() {
                vec4 texColor = texture(uTexture, TexCoord);
                
                // Simple lighting
                vec3 lightPos = uLightPos;
                vec3 norm = normalize(FragPos); // Approximate normal from sphere position
                vec3 lightDir = normalize(lightPos - FragPos);
                float diff = max(dot(norm, lightDir), 0.2);
                
                vec3 result = texColor.rgb * diff;
                frag = vec4(result, 1.0);
            }
            """;

    // Simple OBJ loader (handles vertices and texture coordinates)
    private static class MeshData {
        List<Float> vertices = new ArrayList<>();
        List<Float> texCoords = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
    }
    
    private MeshData loadOBJ(String path) {
        MeshData mesh = new MeshData();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            List<Float> tempVerts = new ArrayList<>();
            List<Float> tempTexCoords = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("v ")) {
                    // Vertex position
                    String[] parts = line.split("\\s+");
                    tempVerts.add(Float.parseFloat(parts[1]));
                    tempVerts.add(Float.parseFloat(parts[2]));
                    tempVerts.add(Float.parseFloat(parts[3]));
                } else if (line.startsWith("vt ")) {
                    // Texture coordinate
                    String[] parts = line.split("\\s+");
                    tempTexCoords.add(Float.parseFloat(parts[1]));
                    tempTexCoords.add(1.0f - Float.parseFloat(parts[2])); // Flip V for OpenGL
                } else if (line.startsWith("f ")) {
                    // Face (assuming triangles)
                    String[] parts = line.split("\\s+");
                    for (int i = 1; i <= 3; i++) {
                        String[] vertData = parts[i].split("/");
                        int vertIdx = Integer.parseInt(vertData[0]) - 1;
                        int texIdx = Integer.parseInt(vertData[1]) - 1;
                        
                        mesh.vertices.add(tempVerts.get(vertIdx * 3));
                        mesh.vertices.add(tempVerts.get(vertIdx * 3 + 1));
                        mesh.vertices.add(tempVerts.get(vertIdx * 3 + 2));
                        
                        mesh.texCoords.add(tempTexCoords.get(texIdx * 2));
                        mesh.texCoords.add(tempTexCoords.get(texIdx * 2 + 1));
                        
                        mesh.indices.add(mesh.indices.size());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load OBJ: " + path);
            e.printStackTrace();
        }
        
        System.out.println("Loaded " + path + " - Vertices: " + mesh.vertices.size()/3 + 
                          ", Indices: " + mesh.indices.size());
        return mesh;
    }
    
    private int createMesh(MeshData mesh) {
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = glGenBuffers();
        
        glBindVertexArray(vao);
        
        // Upload vertices (positions and texcoords interleaved)
        float[] interleaved = new float[mesh.vertices.size() / 3 * 5];
        for (int i = 0; i < mesh.vertices.size() / 3; i++) {
            interleaved[i * 5] = mesh.vertices.get(i * 3);
            interleaved[i * 5 + 1] = mesh.vertices.get(i * 3 + 1);
            interleaved[i * 5 + 2] = mesh.vertices.get(i * 3 + 2);
            interleaved[i * 5 + 3] = mesh.texCoords.get(i * 2);
            interleaved[i * 5 + 4] = mesh.texCoords.get(i * 2 + 1);
        }
        
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, interleaved, GL_STATIC_DRAW);
        
        // Upload indices
        int[] indices = mesh.indices.stream().mapToInt(i -> i).toArray();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        
        // Position attribute
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        // TexCoord attribute
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
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

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW failed");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(WIDTH, HEIGHT,
                "Star Wars System - Mustafar & Kashyyyk", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Window failed");

        try (MemoryStack st = stackPush()) {
            IntBuffer pw = st.mallocInt(1), ph = st.mallocInt(1);
            glfwGetWindowSize(window, pw, ph);
            GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window,
                    (vm.width() - pw.get(0)) / 2,
                    (vm.height() - ph.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();
        
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Load textures
        texMustafar = loadTexture("mustafar.png");
        texKashyyyk = loadTexture("kashyyyk.png");
        
        // Load OBJ meshes
        MeshData mustafarMesh = loadOBJ("mustafar.obj");
        MeshData kashyyykMesh = loadOBJ("kashyyyk.obj");
        
        mustafarVAO = createMesh(mustafarMesh);
        mustafarIndexCount = mustafarMesh.indices.size();
        
        kashyyykVAO = createMesh(kashyyykMesh);
        kashyyykIndexCount = kashyyykMesh.indices.size();

        // Compile shaders
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, VERT); glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Vert: " + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, FRAG); glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException("Frag: " + glGetShaderInfoLog(fs));

        prog = glCreateProgram();
        glAttachShader(prog, vs); glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Link: " + glGetProgramInfoLog(prog));
        glDeleteShader(vs); glDeleteShader(fs);

        startTime = glfwGetTime();

        // Input callbacks
        glfwSetKeyCallback(window, (win, key, sc, action, mods) -> {
            boolean dn = action != GLFW_RELEASE;
            if (key == GLFW_KEY_W) wDown = dn;
            if (key == GLFW_KEY_S) sDown = dn;
            if (key == GLFW_KEY_A) aDown = dn;
            if (key == GLFW_KEY_D) dDown = dn;
            if (key == GLFW_KEY_SPACE) spDown = dn;
            if (key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT) shDown = dn;
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
            pitch  = Math.max(-(float)Math.PI/2+0.01f,
                     Math.min((float)Math.PI/2-0.01f, pitch));
        });
    }

    private void loop() {
        glClearColor(0, 0, 0, 1);
        
        glUseProgram(prog);
        
        int locModel = glGetUniformLocation(prog, "uModel");
        int locView = glGetUniformLocation(prog, "uView");
        int locProj = glGetUniformLocation(prog, "uProjection");
        int locTexture = glGetUniformLocation(prog, "uTexture");
        int locLightPos = glGetUniformLocation(prog, "uLightPos");
        int locViewPos = glGetUniformLocation(prog, "uViewPos");
        int locTime = glGetUniformLocation(prog, "uTime");
        
        double prev = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float)(now - prev); prev = now;
            
            // Update orbit positions
            orbitAngleMus += dt * 0.5;  // Speed of orbit
            orbitAngleKas += dt * 0.3;
            
            musX = (float)(Math.cos(orbitAngleMus) * 9.0);
            musZ = (float)(Math.sin(orbitAngleMus) * 9.0);
            kasX = (float)(Math.cos(orbitAngleKas) * 16.5);
            kasZ = (float)(Math.sin(orbitAngleKas) * 16.5);

            // Camera movement
            float speed = 6.0f * dt;
            float fwdX = (float)Math.sin(yaw), fwdZ = -(float)Math.cos(yaw);
            float rtX  = (float)Math.cos(yaw), rtZ  =  (float)Math.sin(yaw);

            if (wDown)  { camX += fwdX*speed; camZ += fwdZ*speed; }
            if (sDown)  { camX -= fwdX*speed; camZ -= fwdZ*speed; }
            if (aDown)  { camX -= rtX*speed;  camZ -= rtZ*speed;  }
            if (dDown)  { camX += rtX*speed;  camZ += rtZ*speed;  }
            if (spDown)  camY += speed;
            if (shDown)  camY -= speed;

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glUseProgram(prog);
            
            // Setup view and projection matrices
            float[] view = createViewMatrix();
            float[] proj = createProjectionMatrix();
            
            glUniformMatrix4fv(locView, false, view);
            glUniformMatrix4fv(locProj, false, proj);
            
            // Light at center (sun)
            glUniform3f(locLightPos, 0, 0, 0);
            glUniform3f(locViewPos, camX, camY, camZ);
            glUniform1f(locTime, (float)(now - startTime));
            
            // Draw Mustafar
            float[] mustafarModel = createModelMatrix(musX, 0, musZ, 1.55f);
            glUniformMatrix4fv(locModel, false, mustafarModel);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texMustafar);
            glUniform1i(locTexture, 0);
            glBindVertexArray(mustafarVAO);
            glDrawElements(GL_TRIANGLES, mustafarIndexCount, GL_UNSIGNED_INT, 0);
            
            // Draw Kashyyyk
            float[] kashyyykModel = createModelMatrix(kasX, 0, kasZ, 1.75f);
            glUniformMatrix4fv(locModel, false, kashyyykModel);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texKashyyyk);
            glUniform1i(locTexture, 0);
            glBindVertexArray(kashyyykVAO);
            glDrawElements(GL_TRIANGLES, kashyyykIndexCount, GL_UNSIGNED_INT, 0);
            
            glBindVertexArray(0);
            
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }
    
    private float[] createViewMatrix() {
        float[] forward = new float[3];
        forward[0] = (float)(Math.sin(yaw) * Math.cos(pitch));
        forward[1] = (float)(Math.sin(pitch));
        forward[2] = (float)(-Math.cos(yaw) * Math.cos(pitch));
        
        float[] right = new float[3];
        right[0] = (float)Math.cos(yaw);
        right[1] = 0;
        right[2] = (float)Math.sin(yaw);
        
        float[] up = new float[3];
        up[0] = -right[0] * forward[1];
        up[1] = right[0] * forward[2] - right[2] * forward[0];
        up[2] = -right[2] * forward[1];
        
        float[] view = new float[16];
        view[0] = right[0];
        view[1] = up[0];
        view[2] = -forward[0];
        view[3] = 0;
        view[4] = right[1];
        view[5] = up[1];
        view[6] = -forward[1];
        view[7] = 0;
        view[8] = right[2];
        view[9] = up[2];
        view[10] = -forward[2];
        view[11] = 0;
        view[12] = -right[0]*camX - right[1]*camY - right[2]*camZ;
        view[13] = -up[0]*camX - up[1]*camY - up[2]*camZ;
        view[14] = forward[0]*camX + forward[1]*camY + forward[2]*camZ;
        view[15] = 1;
        
        return view;
    }
    
    private float[] createProjectionMatrix() {
        float aspect = (float)WIDTH / HEIGHT;
        float near = 0.1f;
        float far = 100.0f;
        float fovRad = FOV;
        
        float tanHalfFOV = (float)Math.tan(fovRad / 2.0);
        float[] proj = new float[16];
        proj[0] = 1.0f / (aspect * tanHalfFOV);
        proj[5] = 1.0f / tanHalfFOV;
        proj[10] = -(far + near) / (far - near);
        proj[11] = -1;
        proj[14] = -(2.0f * far * near) / (far - near);
        proj[15] = 0;
        
        return proj;
    }
    
    private float[] createModelMatrix(float x, float y, float z, float scale) {
        float[] model = new float[16];
        model[0] = scale;
        model[5] = scale;
        model[10] = scale;
        model[15] = 1;
        model[12] = x;
        model[13] = y;
        model[14] = z;
        return model;
    }

    private void cleanup() {
        glDeleteTextures(texMustafar);
        glDeleteTextures(texKashyyyk);
        glDeleteProgram(prog);
        glDeleteVertexArrays(mustafarVAO);
        glDeleteVertexArrays(kashyyykVAO);
        glDeleteBuffers(mustafarVBO);
        glDeleteBuffers(kashyyykVBO);
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    public static void main(String[] args) { new Simulation().run(); }
}
