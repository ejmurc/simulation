import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Simulation {

    private static final int WIDTH  = 1280;
    private static final int HEIGHT = 720;
    private static final float FOV  = 0.4663f;

    private long   window;
    private int    prog, vao, vbo;
    private double startTime;

    private float  camX = 0, camY = 2, camZ = 30;
    private float  yaw  = 0, pitch = 0;

    private boolean wDown, aDown, sDown, dDown, spDown, shDown;
    private boolean locked = false;
    private double  lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    public void run() { init(); loop(); cleanup(); }

    private static final String VERT = """
#version 410 core
        layout(location=0) in vec2 p;
    void main(){ gl_Position=vec4(p,0,1); }
    """;

    private static final String FRAG = """
#version 410 core
        out vec4 frag;
    uniform vec2  uRes;
    uniform float uTime;
    uniform vec3  uCamPos;
    uniform float uYaw;
    uniform float uPitch;
    uniform float uFOV;

    float hash(float n){ return fract(sin(n)*43758.5453); }
    float hash2(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
    float hash3(vec3 p){ return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453); }

    float vnoise(vec3 p){
        vec3 i=floor(p), f=fract(p);
        f=f*f*(3.-2.*f);
        float n=i.x+i.y*57.+113.*i.z;
        return mix(
                mix(mix(hash(n),      hash(n+1.),  f.x), mix(hash(n+57.), hash(n+58.), f.x), f.y),
                mix(mix(hash(n+113.), hash(n+114.),f.x), mix(hash(n+170.),hash(n+171.),f.x), f.y), f.z);
    }
    float fbm(vec3 p, int o){
        float v=0.,a=.5,q=1.;
        for(int i=0;i<o;i++){v+=a*vnoise(p*q);a*=.5;q*=2.;}
        return v;
    }

    // ── Starfield ──────────────────────────────────────────────────────────────
    vec3 starfield(vec3 rd, float time) {
        vec3 col = vec3(0.);

        for (int layer = 0; layer < 3; layer++) {
            float scale  = 200.0 + float(layer) * 180.0;
            float bright = 1.0   - float(layer) * 0.28;

            vec3 ar = abs(rd);
            vec2 uv;
            float face;
            if (ar.x >= ar.y && ar.x >= ar.z) {
                uv   = rd.yz / ar.x;
                face = rd.x > 0. ? 0. : 1.;
            } else if (ar.y >= ar.x && ar.y >= ar.z) {
                uv   = rd.xz / ar.y;
                face = rd.y > 0. ? 2. : 3.;
            } else {
                uv   = rd.xy / ar.z;
                face = rd.z > 0. ? 4. : 5.;
            }

            vec2 cell = floor(uv * scale + face * 1000.0);
            vec2 offs = fract(uv * scale + face * 1000.0);

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    vec2  nc   = cell + vec2(float(dx), float(dy));
                    float h1   = hash2(nc);
                    float h2   = hash2(nc + 37.3);
                    float h3   = hash2(nc + 91.7);
                    float h4   = hash2(nc + 153.1);

                    vec2 starPos = vec2(h1, h2);
                    float exist = step(0.78, h3);
                    if (exist < 0.5) continue;

                    vec2  delta = offs - starPos - vec2(float(dx), float(dy));
                    float dist  = length(delta);
                    float mag   = pow(h4, 2.2);
                    float radius = mix(0.003, 0.0004, mag);
                    float glow  = exp(-dist * dist / (radius * radius * 8.0));
                    float core  = exp(-dist * dist / (radius * radius * 0.8));

                    float colorH = hash2(nc + 200.5);
                    vec3 starCol;
                    if      (colorH < 0.04) starCol = vec3(0.65, 0.75, 1.00);
                    else if (colorH < 0.12) starCol = vec3(0.80, 0.88, 1.00);
                    else if (colorH < 0.30) starCol = vec3(1.00, 1.00, 1.00);
                    else if (colorH < 0.55) starCol = vec3(1.00, 0.97, 0.82);
                    else if (colorH < 0.78) starCol = vec3(1.00, 0.85, 0.55);
                    else                    starCol = vec3(1.00, 0.55, 0.35);

                    float twinkleRate = mix(1.8, 0.6, mag);
                    float twinkle     = 0.85 + 0.15 * sin(time * twinkleRate + h1 * 62.8 + h2 * 31.4);
                    float luminance   = (1.0 - mag * 0.85) * bright * twinkle;
                    col += starCol * (glow * 0.6 + core * 1.4) * luminance * exist;
                }
            }
        }

        // Milky Way
        float ga  = 0.52;
        float cga = cos(ga), sga = sin(ga);
        vec3  gr  = vec3(rd.x, cga*rd.y - sga*rd.z, sga*rd.y + cga*rd.z);
        float latB    = abs(gr.y);
        float bandFall= exp(-latB * latB / 0.025);
        float mwNoise = fbm(gr * vec3(3.0, 12.0, 3.0), 5) * 0.6
            + fbm(gr * vec3(6.0, 20.0, 6.0) + 7.3, 4) * 0.4;
        mwNoise = clamp(mwNoise, 0., 1.);
        vec3 mwCore = mix(vec3(0.55, 0.50, 0.75), vec3(0.75, 0.70, 0.55), mwNoise);
        col += mwCore * bandFall * mwNoise * 0.22;
        float hazeFbm = fbm(rd * 1.4 + 5.7, 4);
        col += vec3(0.010, 0.008, 0.018) * hazeFbm;

        return col;
    }
    // ──────────────────────────────────────────────────────────────────────────

    const vec3  SUN_C     = vec3(0.);
    const float SUN_R     = 2.4;

    // Mustafar
    const float MUS_ORBIT = 9.0;
    const float MUS_R     = 1.55;

    // Kashyyyk — further out, slightly larger
    const float KAS_ORBIT = 16.5;
    const float KAS_R     = 1.75;

    vec3 musPlanetCenter(){
        float a = uTime * 0.18;
        return vec3(cos(a)*MUS_ORBIT, sin(a)*0.4, sin(a)*MUS_ORBIT);
    }

    vec3 kasPlanetCenter(){
        // Kashyyyk orbits slower (Kepler-ish), slight inclination, offset phase
        float a = uTime * 0.10 + 2.1;
        return vec3(cos(a)*KAS_ORBIT, sin(a)*0.25, sin(a)*KAS_ORBIT);
    }

    // ── Signed-distance functions ──────────────────────────────────────────────

    float sdMustafar(vec3 p, vec3 c){
        vec3 n   = normalize(p - c);
        float spin = uTime * 0.25, cs = cos(spin), sn = sin(spin);
        vec3 rot = vec3(cs*n.x + sn*n.z, n.y, -sn*n.x + cs*n.z);
        float disp = fbm(rot*3.0, 6)*0.18 + fbm(rot*7.0, 3)*0.05;
        return length(p - c) - MUS_R - disp;
    }

    float sdKashyyyk(vec3 p, vec3 c){
        vec3 n   = normalize(p - c);
        float spin = uTime * 0.20, cs = cos(spin), sn = sin(spin);
        vec3 rot = vec3(cs*n.x + sn*n.z, n.y, -sn*n.x + cs*n.z);
        // Dense jungle canopy gives soft, organic displacement
        float canopy = fbm(rot*2.5,  7)*0.20 + fbm(rot*6.0,  4)*0.07;
        float ocean  = fbm(rot*12.0, 3)*0.01;   // subtle ocean wavelet
        return length(p - c) - KAS_R - canopy - ocean;
    }

    float sdSun(vec3 p){
        vec3 n=normalize(p-SUN_C);
        float disp=fbm(n*4.+uTime*.08,4)*0.12+fbm(n*9.+uTime*.05,3)*0.04;
        return length(p-SUN_C)-SUN_R-disp;
    }

    // id: 1=Mustafar, 2=Sun, 3=Kashyyyk
    vec2 sceneAll(vec3 p){
        vec3 mc = musPlanetCenter();
        vec3 kc = kasPlanetCenter();
        float dm = sdMustafar(p, mc);
        float ds = sdSun(p);
        float dk = sdKashyyyk(p, kc);

        float best = dm; float id = 1.;
        if (ds < best) { best = ds; id = 2.; }
        if (dk < best) { best = dk; id = 3.; }
        return vec2(best, id);
    }

    // Shadow-caster: only solid planets cast shadows on each other
    float scenePlanetsOnly(vec3 p){
        float dm = sdMustafar(p, musPlanetCenter());
        float dk = sdKashyyyk(p, kasPlanetCenter());
        return min(dm, dk);
    }

    vec3 calcNormal(vec3 p){
        const float e=0.002;
        const vec2 k=vec2(1.,-1.);
        return normalize(
                k.xyy*sceneAll(p+k.xyy*e).x +
                k.yyx*sceneAll(p+k.yyx*e).x +
                k.yxy*sceneAll(p+k.yxy*e).x +
                k.xxx*sceneAll(p+k.xxx*e).x
                );
    }

    vec2 raymarch(vec3 ro, vec3 rd){
        float t=0.1;
        for(int i=0;i<148;i++){
            vec2 res=sceneAll(ro+rd*t);
            if(res.x<0.002) return vec2(t,res.y);
            t+=res.x;
            if(t>200.) break;
        }
        return vec2(-1.,0.);
    }

    float softShadow(vec3 ro, vec3 rd, float mint, float maxt, float k){
        float res=1., t=mint, ph=1e10;
        for(int i=0;i<64;i++){
            float h=scenePlanetsOnly(ro+rd*t);
            if(h<0.001) return 0.;
            float y=h*h/(2.*ph);
            float d=sqrt(max(0.,h*h-y*y));
            res=min(res, k*d/max(0.001,t-y));
            ph=h; t+=h;
            if(t>maxt) break;
        }
        return clamp(res,0.,1.);
    }

    float ambientOcc(vec3 p, vec3 n){
        float occ=0., sca=1.;
        for(int i=0;i<5;i++){
            float h=0.02+0.12*float(i)/4.;
            float d=scenePlanetsOnly(p+n*h);
            occ+=(h-d)*sca;
            sca*=0.85;
        }
        return clamp(1.-2.*occ,0.,1.);
    }

    // ── Mustafar surface ───────────────────────────────────────────────────────
    vec3 musColor(vec3 n){
        float spin=uTime*0.18, cs=cos(spin), sn=sin(spin);
        vec3 rot=vec3(cs*n.x+sn*n.z, n.y, -sn*n.x+cs*n.z);

        float plate  = fbm(rot*1.8,          6);
        float crack  = fbm(rot*6.5  + 2.3,   5);
        float micro  = fbm(rot*18.0 + 7.1,   3);

        float crackMask = smoothstep(0.52, 0.46, crack + plate*0.3);
        float plateMask = smoothstep(0.40, 0.60, plate);

        float rockVar = micro * 0.5 + plate * 0.5;
        vec3 rockDark  = vec3(0.022, 0.018, 0.020);
        vec3 rockMid   = vec3(0.055, 0.042, 0.038);
        vec3 rockHot   = vec3(0.110, 0.060, 0.040);
        vec3 rockCol   = mix(rockDark, rockMid,  smoothstep(0.3, 0.7, rockVar));
        rockCol        = mix(rockCol,  rockHot,  crackMask * 0.55);
        float sheen    = smoothstep(0.60, 0.80, micro) * (1.0 - crackMask);
        rockCol       += vec3(0.018, 0.012, 0.035) * sheen;

        float lavaFlow = fbm(rot*4.2 + uTime*0.12, 4);
        float lavaTemp = clamp(lavaFlow * 1.3 - crackMask * 0.2, 0., 1.);

        vec3 lavaWhite  = vec3(1.00, 0.96, 0.78);
        vec3 lavaOrange = vec3(1.00, 0.52, 0.04);
        vec3 lavaRed    = vec3(0.72, 0.08, 0.01);
        vec3 lavaCrust  = vec3(0.28, 0.03, 0.01);

        vec3 lavaCol = mix(lavaCrust,  lavaRed,    smoothstep(0.10, 0.35, lavaTemp));
        lavaCol      = mix(lavaCol,    lavaOrange, smoothstep(0.35, 0.65, lavaTemp));
        lavaCol      = mix(lavaCol,    lavaWhite,  smoothstep(0.72, 0.95, lavaTemp));

        vec3 surface = mix(rockCol, lavaCol, crackMask);
        float emGlow = crackMask * (0.5 + 0.5 * lavaTemp);
        vec3  emCol  = lavaCol * emGlow * 3.2;

        return surface + emCol;
    }

    // ── Kashyyyk surface ───────────────────────────────────────────────────────
    vec3 kasColor(vec3 n){
        float spin = uTime * 0.20, cs = cos(spin), sn = sin(spin);
        vec3 rot = vec3(cs*n.x + sn*n.z, n.y, -sn*n.x + cs*n.z);

        // Large-scale continental structure
        float continent = fbm(rot * 1.4,          6);
        // Mid-scale jungle canopy texture
        float canopy    = fbm(rot * 4.5 + 3.1,    6);
        // Fine forest-floor detail
        float detail    = fbm(rot * 12.0 + 7.9,   4);
        // Ocean / inland-sea mask (low continent = ocean basin)
        float oceanMask = smoothstep(0.38, 0.50, continent);   // 0=ocean, 1=land
                                                               // Polar icecap: fade to white near poles (|n.y| close to 1)
        float polar     = smoothstep(0.72, 0.92, abs(rot.y));

        // ── Land colours ─────────────────────────────────────────────────────────
        // Kashyyyk is DENSE jungle — wroshyr trees 500m tall, layered canopy.
        // Palette: near-black shadow green → vivid mid green → yellow-green tips
        vec3 jungleDark  = vec3(0.018, 0.055, 0.020);   // deep shadow under canopy
        vec3 jungleMid   = vec3(0.055, 0.160, 0.048);   // main canopy
        vec3 jungleBright= vec3(0.120, 0.290, 0.060);   // sunlit treetop highlights
        vec3 jungleYellow= vec3(0.200, 0.350, 0.040);   // new growth / cleared areas

        float canopyBlend = smoothstep(0.30, 0.70, canopy + detail * 0.25);
        vec3 jungleCol = mix(jungleDark, jungleMid,   smoothstep(0.20, 0.55, canopyBlend));
        jungleCol      = mix(jungleCol,  jungleBright, smoothstep(0.55, 0.80, canopyBlend));
        jungleCol      = mix(jungleCol,  jungleYellow, smoothstep(0.82, 0.96, canopyBlend) * 0.4);

        // Rocky highland / mountain ridge — darker brownish-green
        float mountain  = smoothstep(0.64, 0.82, continent + canopy * 0.2);
        vec3  ridgeCol  = mix(jungleDark, vec3(0.08, 0.07, 0.05), mountain);
        jungleCol       = mix(jungleCol, ridgeCol, mountain * 0.6);

        // ── Ocean / Shadowsea ─────────────────────────────────────────────────────
        // Deep teal-blue ocean (orbital views show vast inland seas)
        float waveNoise  = fbm(rot * 9.0 + uTime * 0.04, 3);
        vec3 oceanDeep   = vec3(0.012, 0.060, 0.120);   // abyssal dark blue
        vec3 oceanShallow= vec3(0.045, 0.170, 0.240);   // shallow shelf cyan
        vec3 oceanCol    = mix(oceanDeep, oceanShallow, smoothstep(0.35, 0.65, waveNoise));
        // Specular glint on ocean surface
        float oceanSpec  = pow(waveNoise, 3.0) * 0.6;
        oceanCol        += vec3(0.3, 0.6, 0.8) * oceanSpec * (1.0 - oceanMask);

        // ── Polar icecaps ─────────────────────────────────────────────────────────
        vec3 iceCol = vec3(0.80, 0.90, 0.95);
        float iceNoise = fbm(rot * 8.0 + 11.3, 3) * 0.15;
        vec3 polarCol  = mix(jungleCol, iceCol, polar + iceNoise * polar);

        // ── Compose land → ocean → polar ─────────────────────────────────────────
        vec3 surface = mix(oceanCol, polarCol, oceanMask);

        // ── Atmospheric cloud wisps ────────────────────────────────────────────────
        // Kashyyyk has a thick humid atmosphere — partial cloud cover visible from orbit
        float cloud = fbm(rot * 3.5 + uTime * 0.025, 5);
        float cloudMask = smoothstep(0.56, 0.68, cloud);
        vec3  cloudCol  = vec3(0.70, 0.78, 0.85);   // slightly blue-tinted clouds
        surface = mix(surface, cloudCol, cloudMask * 0.50);

        return surface;
    }

    // ── Sun surface ───────────────────────────────────────────────────────────
    vec3 sunSurfColor(vec3 n){
        float t=uTime;
        vec3 p=n*4.+vec3(t*.10,t*.07,t*.13);
        float g=fbm(p,5), sp=fbm(p*2.5+9.3,4), fl=fbm(n*6.+t*.05,3);
        float bl=smoothstep(.3,.7,g);
        vec3 col=mix(vec3(.7,.3,.05),mix(vec3(1.,.6,.1),vec3(1.,.95,.7),bl),bl);
        col+=vec3(1.,.8,.3)*pow(max(0.,fl-.4),2.)*2.;
        col=mix(col,vec3(.7,.3,.05)*.6,smoothstep(.65,.5,sp)*.5);
        return col*vec3(2.5,1.6,0.4)*1.4;
    }

    // ── Nebulae ────────────────────────────────────────────────────────────────
    vec3 nebula(vec3 rd) {
        vec3 col = vec3(0.);

        // Lobe A: large H-alpha emission cloud, red/crimson
        {
            vec3  axis  = normalize(vec3(-0.6, 0.5, -0.8));
            float align = dot(rd, axis);
            float fall  = exp(-max(0., 1.0 - align) * 3.5);
            vec3  p     = rd * 2.3 + vec3(1.7, 0.4, 2.1);
            float n1    = fbm(p,         6);
            float n2    = fbm(p * 2.1 + 3.9, 4);
            float shape = smoothstep(0.38, 0.72, n1 + n2 * 0.35) * fall;
            float dust  = smoothstep(0.55, 0.45, fbm(p * 1.7 + 9.1, 4));
            vec3  hue   = mix(vec3(0.55, 0.02, 0.04), vec3(0.90, 0.12, 0.08),
                    smoothstep(0.4, 0.7, n1));
            hue += vec3(0.04, 0.12, 0.18) * smoothstep(0.6, 0.8, n2);
            col += hue * shape * dust * 0.55;
        }

        // Lobe B: orange/amber shocked gas
        {
            vec3  axis  = normalize(vec3(0.7, -0.3, 0.6));
            float align = dot(rd, axis);
            float fall  = exp(-max(0., 1.0 - align) * 5.0);
            vec3  p     = rd * 3.1 + vec3(5.2, 1.1, 3.8);
            float n1    = fbm(p,         5);
            float n2    = fbm(p * 2.4 + 7.3, 3);
            float shape = smoothstep(0.42, 0.68, n1 + n2 * 0.3) * fall;
            vec3  hue   = mix(vec3(0.45, 0.10, 0.01), vec3(0.95, 0.45, 0.05),
                    smoothstep(0.35, 0.65, n1));
            col += hue * shape * 0.40;
        }

        // Lobe C: faint purple/blue O-III outer shell
        {
            vec3  axis  = normalize(vec3(0.1, 0.8, 0.5));
            float align = dot(rd, axis);
            float fall  = exp(-max(0., 1.0 - align) * 2.0);
            vec3  p     = rd * 1.6 + vec3(8.3, 4.1, 1.2);
            float n1    = fbm(p,         5);
            float shape = smoothstep(0.44, 0.65, n1) * fall;
            vec3  hue   = mix(vec3(0.08, 0.05, 0.20), vec3(0.18, 0.10, 0.45),
                    smoothstep(0.4, 0.7, n1));
            col += hue * shape * 0.30;
        }

        return col;
    }
    // ──────────────────────────────────────────────────────────────────────────

    // ── Mustafar lava atmosphere (orange-red) ─────────────────────────────────
    vec3 lavaAtm(vec3 rd, vec3 pc, float pr, vec3 ld){
        float ar=pr*1.22; vec3 oc=-pc;
        float b=dot(oc,rd), c=dot(oc,oc)-ar*ar, disc=b*b-c;
        if(disc<0.) return vec3(0.);
        float sq=sqrt(disc), t2=-b+sq;
        if(t2<0.) return vec3(0.);
        float thickness=clamp((t2-max(-b-sq,0.))/(2.*ar),0.,1.);

        float inner = pow(thickness, 0.6);
        float outer = pow(thickness, 0.25);
        float sunFacing = clamp(dot(normalize(pc), ld) * 0.5 + 0.5, 0., 1.);

        vec3 innerCol = mix(vec3(0.60, 0.10, 0.01), vec3(1.00, 0.45, 0.05), inner);
        vec3 outerCol = mix(vec3(0.20, 0.02, 0.00), vec3(0.55, 0.08, 0.01), outer);
        vec3 atm = innerCol * inner * 0.55 + outerCol * outer * 0.30;

        float lavaLit = (1.0 - sunFacing) * thickness * 0.35;
        atm += vec3(0.80, 0.18, 0.02) * lavaLit;

        return atm;
    }

    // ── Kashyyyk forest atmosphere (blue-teal) ────────────────────────────────
    // Thick, humid atmosphere — Rayleigh scattering → vivid blue limb glow.
    // Inner layer: teal-blue (oxygen + water vapour scatter).
    // Outer limb: deep cobalt-indigo fringe (high-altitude haze).
    // Night side: faint bio-luminescent blue-green from the wroshyr forest floor.
    vec3 kasAtm(vec3 rd, vec3 pc, float pr, vec3 ld){
        float ar = pr * 1.30;   // notably thicker than Mustafar (humid world)
        vec3  oc = -pc;
        float b  = dot(oc, rd), c = dot(oc, oc) - ar*ar, disc = b*b - c;
        if (disc < 0.) return vec3(0.);
        float sq = sqrt(disc), t2 = -b + sq;
        if (t2 < 0.) return vec3(0.);
        float thickness = clamp((t2 - max(-b-sq, 0.)) / (2.*ar), 0., 1.);

        float inner = pow(thickness, 0.55);   // inner dense blue layer
        float outer = pow(thickness, 0.20);   // diffuse outer limb

        float sunFacing = clamp(dot(normalize(pc), ld) * 0.5 + 0.5, 0., 1.);

        // Inner atmosphere: teal→cyan gradient
        vec3 innerDark  = vec3(0.00, 0.08, 0.22);   // dark teal in shadow
        vec3 innerBright= vec3(0.05, 0.35, 0.65);   // lit teal-blue
        vec3 innerCol   = mix(innerDark, innerBright, sunFacing * inner);

        // Outer limb haze: deep indigo-blue Rayleigh fringe
        vec3 outerDark  = vec3(0.00, 0.04, 0.18);
        vec3 outerBright= vec3(0.02, 0.15, 0.48);
        vec3 outerCol   = mix(outerDark, outerBright, sunFacing);

        vec3 atm = innerCol * inner * 0.60 + outerCol * outer * 0.35;

        // Bioluminescent night-side glow: blue-green from below the canopy
        float bioGlow = (1.0 - sunFacing) * thickness * 0.28;
        atm += vec3(0.04, 0.28, 0.35) * bioGlow;

        return atm;
    }
    // ──────────────────────────────────────────────────────────────────────────

    void main(){
        vec2 ndc = (gl_FragCoord.xy / uRes) * 2.0 - 1.0;
        vec2 uv  = vec2(ndc.x * (uRes.x / uRes.y), ndc.y);

        float cy=cos(uYaw),   sy=sin(uYaw);
        float cp=cos(uPitch), sp=sin(uPitch);

        vec3 right    = vec3(cy, 0., sy);
        vec3 up       = vec3(0., 1., 0.);
        vec3 viewFwd  = vec3(sy*cp, sp, -cy*cp);
        vec3 viewRight= right;
        vec3 viewUp   = cross(viewRight, viewFwd);

        vec3 ro = uCamPos;
        vec3 rd  = normalize(viewFwd + uv.x*uFOV*viewRight + uv.y*uFOV*viewUp);

        vec3 musPc = musPlanetCenter();
        vec3 kasPc = kasPlanetCenter();
        vec3 ld    = normalize(SUN_C - musPc);   // light direction from sun

        vec2 hit = raymarch(ro, rd);
        vec3 color = vec3(0.);

        if(hit.x > 0.){
            vec3 pos = ro + rd * hit.x;
            vec3 N   = calcNormal(pos);

            if(hit.y < 1.5){
                // ── Mustafar ─────────────────────────────────────────────────────────
                vec3 alb = musColor(N);
                float ao = ambientOcc(pos, N);
                float diff = max(dot(N, ld), 0.);
                float dist2sun = length(SUN_C - pos);
                float shad = softShadow(pos + N*0.015, ld, 0.05, dist2sun, 14.);
                vec3 sunL = vec3(2.2, 1.4, 0.35)*2.5*diff*shad;
                vec3 amb  = vec3(0.18, 0.04, 0.005)*ao;
                vec3 H    = normalize(ld - rd);
                float spec = pow(max(dot(N, H), 0.), 120.)*0.6*shad;
                float fres = pow(clamp(1.-dot(N,-rd), 0., 1.), 2.5)*0.8;
                vec3 rim   = vec3(1., .30, .02)*fres*(0.4+0.6*(1.-shad));
                color = alb*(sunL+amb) + spec*vec3(1.8,1.2,0.4) + rim;
                vec3 atm = lavaAtm(rd, musPc, MUS_R, ld);
                color = color*(1.-atm.r*0.35) + atm;

            } else if(hit.y > 2.5){
                // ── Kashyyyk ─────────────────────────────────────────────────────────
                vec3 kasLd = normalize(SUN_C - kasPc);   // light toward Kashyyyk
                vec3 alb = kasColor(N);
                float ao = ambientOcc(pos, N);
                float diff = max(dot(N, kasLd), 0.);
                float dist2sun = length(SUN_C - pos);
                float shad = softShadow(pos + N*0.015, kasLd, 0.05, dist2sun, 14.);
                vec3 sunL = vec3(2.0, 1.6, 0.9) * 2.2 * diff * shad;
                // Ambient: faint warm fill + bioluminescent blue-green underlight
                vec3 amb  = vec3(0.05, 0.10, 0.08) * ao;
                vec3 H    = normalize(kasLd - rd);
                // Jungle/ocean has a softer, broader specular (wet leaves + ocean glint)
                float spec = pow(max(dot(N, H), 0.), 55.) * 0.45 * shad;
                // Rim: blue atmospheric edge backlit by sun — Kashyyyk's signature look
                float fres = pow(clamp(1.-dot(N,-rd), 0., 1.), 2.2) * 0.9;
                vec3 rim   = vec3(0.10, 0.50, 0.90) * fres * (0.5 + 0.5*(1.-shad));
                color = alb*(sunL + amb) + spec*vec3(0.7, 0.9, 1.0) + rim;
                vec3 atm = kasAtm(rd, kasPc, KAS_R, kasLd);
                color = color*(1. - atm.b*0.25) + atm;

            } else {
                // ── Sun ───────────────────────────────────────────────────────────────
                color = sunSurfColor(N);
            }
        } else {
            // ── Background: nebulae + stars + both planet atmospheres ──────────────
            color  = nebula(rd);
            color += starfield(rd, uTime);
            // Mustafar lava atmosphere halo visible from outside
            vec3 musLd = normalize(SUN_C - musPc);
            color += lavaAtm(rd, musPc - ro, MUS_R, musLd) * 0.6;
            // Kashyyyk blue atmosphere limb glow visible from outside
            vec3 kasLd = normalize(SUN_C - kasPc);
            color += kasAtm(rd, kasPc - ro, KAS_R, kasLd) * 0.65;
        }

        color = color / (color + vec3(1.));
        color = pow(color, vec3(1./2.2));
        frag  = vec4(color, 1.);
    }
    """;

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW failed");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Star Wars System — Mustafar & Kashyyyk", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Window failed");

        try (MemoryStack st = stackPush()) {
            IntBuffer pw = st.mallocInt(1), ph = st.mallocInt(1);
            glfwGetWindowSize(window, pw, ph);
            GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window, (vm.width()-pw.get(0))/2, (vm.height()-ph.get(0))/2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, VERT); glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS)==GL_FALSE)
            throw new RuntimeException("Vert: "+glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, FRAG); glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS)==GL_FALSE)
            throw new RuntimeException("Frag: "+glGetShaderInfoLog(fs));

        prog = glCreateProgram();
        glAttachShader(prog, vs); glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS)==GL_FALSE)
            throw new RuntimeException("Link: "+glGetProgramInfoLog(prog));
        glDeleteShader(vs); glDeleteShader(fs);

        float[] verts = {-1,-1, 1,-1, -1,1, 1,1};
        vao = glGenVertexArrays(); vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        try (MemoryStack st = stackPush()) {
            FloatBuffer fb = st.mallocFloat(8);
            fb.put(verts).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);
        glBindVertexArray(0);

        startTime = glfwGetTime();

        glfwSetKeyCallback(window, (win, key, sc, action, mods) -> {
            boolean dn = action != GLFW_RELEASE;
            if (key==GLFW_KEY_W) wDown=dn;
            if (key==GLFW_KEY_S) sDown=dn;
            if (key==GLFW_KEY_A) aDown=dn;
            if (key==GLFW_KEY_D) dDown=dn;
            if (key==GLFW_KEY_SPACE) spDown=dn;
            if (key==GLFW_KEY_LEFT_SHIFT||key==GLFW_KEY_RIGHT_SHIFT) shDown=dn;
            if (key==GLFW_KEY_ESCAPE && action==GLFW_RELEASE) {
                if (locked) { glfwSetInputMode(win,GLFW_CURSOR,GLFW_CURSOR_NORMAL); locked=false; }
                else glfwSetWindowShouldClose(win,true);
            }
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button==GLFW_MOUSE_BUTTON_LEFT && action==GLFW_PRESS && !locked) {
                glfwSetInputMode(win, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                locked=true; firstMouse=true;
            }
        });

        glfwSetCursorPosCallback(window, (win, mx, my) -> {
            if (!locked) return;
            if (firstMouse) { lastMouseX=mx; lastMouseY=my; firstMouse=false; return; }
            double dx=mx-lastMouseX, dy=my-lastMouseY;
            lastMouseX=mx; lastMouseY=my;
            float sens=0.0015f;
            yaw   += (float)(dx*sens);
            pitch -= (float)(dy*sens);
            pitch  = Math.max(-(float)Math.PI/2+0.01f, Math.min((float)Math.PI/2-0.01f, pitch));
        });
    }

    private void loop() {
        glClearColor(0,0,0,1);
        int locRes    = glGetUniformLocation(prog,"uRes");
        int locTime   = glGetUniformLocation(prog,"uTime");
        int locCamPos = glGetUniformLocation(prog,"uCamPos");
        int locYaw    = glGetUniformLocation(prog,"uYaw");
        int locPitch  = glGetUniformLocation(prog,"uPitch");
        int locFOV    = glGetUniformLocation(prog,"uFOV");

        double prev = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt=(float)(now-prev); prev=now;

            float speed=6.0f*dt;
            float fwdX=(float)Math.sin(yaw),  fwdZ=-(float)Math.cos(yaw);
            float rtX =(float)Math.cos(yaw),   rtZ = (float)Math.sin(yaw);

            if (wDown)  { camX+=fwdX*speed; camZ+=fwdZ*speed; }
            if (sDown)  { camX-=fwdX*speed; camZ-=fwdZ*speed; }
            if (aDown)  { camX-=rtX*speed;  camZ-=rtZ*speed;  }
            if (dDown)  { camX+=rtX*speed;  camZ+=rtZ*speed;  }
            if (spDown) camY+=speed;
            if (shDown) camY-=speed;

            glClear(GL_COLOR_BUFFER_BIT);
            glUseProgram(prog);
            glUniform2f(locRes, WIDTH, HEIGHT);
            glUniform1f(locTime, (float)(now-startTime));
            glUniform3f(locCamPos, camX, camY, camZ);
            glUniform1f(locYaw,   yaw);
            glUniform1f(locPitch, pitch);
            glUniform1f(locFOV,   FOV);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            glBindVertexArray(0);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        glDeleteProgram(prog);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    public static void main(String[] args) { new Simulation().run(); }
}
