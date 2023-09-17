package cfbastian.renderer3d;

import cfbastian.renderer3d.util.ObjFileManager;
import com.aparapi.Kernel;
import com.aparapi.Range;

import java.io.IOException;

public class Renderer {
    private Scene mainScene;
    double elapsedTime;

    float[] rays, vertices;
    int[] faces;
    int[] kIdxs;
    float[] shearFactors;
    float[] cameraPos;

    RenderKernel renderKernel = new RenderKernel();
    Range range;

    public void init()
    {
        range = Range.create(Application.width * Application.height, 32);

        mainScene = new Scene();
        try {
//            mainScene.addMesh(ObjFileManager.generateMeshFromFile("src/main/resources/cfbastian/renderer3d/meshes/StanfordBunny.obj", new float[]{6f, 0f, -2f}, 5f, 2, "StaffordBunny"));
//            mainScene.addMesh(ObjFileManager.generateMeshFromFile("src/main/resources/cfbastian/renderer3d/meshes/UtahTeapot.obj", new float[]{6f, 0f, 0f}, 5f, 2, "Teapot"));
//            mainScene.addMesh(ObjFileManager.generateMeshFromFile("src/main/resources/cfbastian/renderer3d/meshes/Sphere.obj", new float[]{4f, 0f, 0f}, 0.5f, 2, "Sphere"));
            mainScene.addMesh(ObjFileManager.generateMeshFromFile("src/main/resources/cfbastian/renderer3d/meshes/Cube.obj", new float[]{4f, 0f, 0f}, 0.5f, 2, "Cube"));
            mainScene.addMesh(ObjFileManager.generateMeshFromFile("src/main/resources/cfbastian/renderer3d/meshes/Cube.obj", new float[]{4f, 1.5f, 0f}, 0.5f, 2, "Cube1"));
            mainScene.addMesh(ObjFileManager.generateMeshFromFile("src/main/resources/cfbastian/renderer3d/meshes/Cube.obj", new float[]{4f, -1.5f, 0f}, 0.5f, 2, "Cube2"));
//            mainScene.addMesh(ObjFileManager.generateMeshFromFile("src/main/resources/cfbastian/renderer3d/meshes/Quad.obj", new float[]{5f, 0f, 0f}, 1f, 2, "Quad"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        vertices = mainScene.getAllVertices();
        faces = mainScene.getAllFaces();
    }

    public int[] render(double elapsedTime, float[] rays, int[] kIdxs, float[] shearFactors, float[] cameraPos)
    {
        this.elapsedTime = elapsedTime;
        this.rays = rays;
        this.kIdxs = kIdxs;
        this.shearFactors = shearFactors;
        this.cameraPos = cameraPos;

        renderKernel.update(cameraPos, rays, kIdxs, shearFactors, vertices, faces);
        renderKernel.execute(range);

        return renderKernel.get();
    }

    public boolean hitAABB(float[] cameraPos, float[] ray, float[] a, float[] b, float padding)
    {
        float[] a1 = new float[3];
        float[] b1 = new float[3];
        a1[0] = Math.min(a[0], b[0]) - padding;
        a1[1] = Math.min(a[1], b[1]) - padding;
        a1[2] = Math.min(a[2], b[2]) - padding;
        b1[0] = Math.max(a[0], b[0]) + padding;
        b1[1] = Math.max(a[1], b[1]) + padding;
        b1[2] = Math.max(a[2], b[2]) + padding;

        float tMin = Float.MIN_VALUE, tMax = Float.MAX_VALUE;

        for (int i = 0; i < 3; i++) {
            float invD = 1f/ray[i];
            float t0 = (a1[i] - cameraPos[i]) * invD;
            float t1 = (b1[i] - cameraPos[i]) * invD;

            if(invD < 0f)
            {
                float v = t0;
                t0 = t1;
                t1 = v;
            }

            if(t0 > tMin) tMin = t0;
            if(t1 < tMax) tMax = t1;

            if(tMax < tMin) return false;
        }

        return true;
    }

    private class RenderKernel extends Kernel
    {
        @Local float[] cameraPos;
        @Local float[] rays;
        @Local int[] kIdxs;
        @Local float[] shearFactors;
        @Local float[] vertices;
        @Local int[] faces;
        @Local int[] pixels = new int[Application.width * Application.height];

        public synchronized void update(float[] cameraPos, float[] rays, int[] kIdxs, float[] shearFactors, float[] vertices, int[] faces) {
            this.cameraPos = cameraPos;
            this.rays = rays;
            this.kIdxs = kIdxs;
            this.shearFactors = shearFactors;
            this.vertices = vertices;
            this.faces = faces;
        }

        public int[] get()
        {
            return pixels;
        }

        /**
         * Gets the color for the pixel at the given index. Ray-Triangle intersection algorithm from
         * Watertight Ray/Triangle Intersection by Woop et al. TODO fix citation
         * */
        @Override
        public void run()
        {
            int i = getGlobalId();

            float rayX = rays[i*3];
            float rayY = rays[i*3+1];
            float rayZ = rays[i*3+2];

            float r, g, b;

            int kx = kIdxs[i*3], ky = kIdxs[i*3+1], kz = kIdxs[i*3+2];
            float Sx = shearFactors[i*3], Sy = shearFactors[i*3+1], Sz = shearFactors[i*3+2];

            float t = 3.4028235E38f, u = 0f, v = 0f, w = 0f; // 3.4028235E38f is Float.MAX_VALUE

            for (int j = 0; j < faces.length/3; j++) {
                float Akx = vertices[faces[j*3]*3+kx] - cameraPos[kx];
                float Aky = vertices[faces[j*3]*3+ky] - cameraPos[ky];
                float Akz = vertices[faces[j*3]*3+kz] - cameraPos[kz];
                float Bkx = vertices[faces[j*3+1]*3+kx] - cameraPos[kx];
                float Bky = vertices[faces[j*3+1]*3+ky] - cameraPos[ky];
                float Bkz = vertices[faces[j*3+1]*3+kz] - cameraPos[kz];
                float Ckx = vertices[faces[j*3+2]*3+kx] - cameraPos[kx];
                float Cky = vertices[faces[j*3+2]*3+ky] - cameraPos[ky];
                float Ckz = vertices[faces[j*3+2]*3+kz] - cameraPos[kz];

                float Ax = Akx - Sx * Akz;
                float Ay = Aky - Sy * Akz;
                float Bx = Bkx - Sx * Bkz;
                float By = Bky - Sy * Bkz;
                float Cx = Ckx - Sx * Ckz;
                float Cy = Cky - Sy * Ckz;

                float U = Cx*By - Cy*Bx;
                float V = Ax*Cy - Ay*Cx;
                float W = Bx*Ay - By*Ax;

                if(U < 0 || V < 0 || W < 0) continue;

                float det = U + V + W;
                if(det == 0D) continue;

                float Az = Sz*Akz;
                float Bz = Sz*Bkz;
                float Cz = Sz*Ckz;
                float T = U*Az + V*Bz + W*Cz;

                if(T <= 0 || T >= t * det) continue;

                float rcpDet = 1f/det;
                u = U*rcpDet;
                v = V*rcpDet;
                w = W*rcpDet;
                t = T*rcpDet;
            }

            if(t != 3.4028235E38f)
            {
                r = u;
                g = v;
                b = w;
            }
            else
            {
                // Background
                float rayDirLength = sqrt(rayX*rayX + rayY*rayY + rayZ*rayZ);
                float[] unitDir = new float[]{rayX/rayDirLength, rayY/rayDirLength, rayZ/rayDirLength};
                float a = 0.5f * (unitDir[2] + 1f);

                r = (1f - a) * 1 + a * 0.5f;
                g = (1f - a) * 1 + a * 0.7f;
                b = (1f - a) * 1 + a;

                if(hitAABB(cameraPos, new float[]{rayX, rayY, rayZ}, new float[]{-1f, 1f, -1f}, new float[]{-2f, -1f, 0.5f}, 0.01f))
                {
                    r = 0;
                    g = 0;
                    b = 0;
                }
            }

            r = min(r, 1f);
            g = min(g, 1f);
            b = min(b, 1f);
            r = max(r, 0f);
            g = max(g, 0f);
            b = max(b, 0f);
            pixels[i] = 0xFF000000 + ((int) (r * 255)<<16) + ((int) (g * 255)<<8) + (int) (b * 255);
        }
    }
}
