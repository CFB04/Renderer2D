package cfbastian.renderer3d.bodies;

import cfbastian.renderer3d.math.ArrayOperations;
import cfbastian.renderer3d.math.ScalarMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

public class BoundingVolumeHierarchy {
    private LinkedList<BoundingVolumeHierarchy> childNodes;
    private AxisAlignedBoundingBox aabb;
    private int[] faces;

    public BoundingVolumeHierarchy(int[] faces, float[] vertices, int splitAxis, int splitChecks, int maxDepth)
    {
        childNodes = new LinkedList<>();
        splitAxis = splitAxis > 2 ? 0 : splitAxis;

        faces = ArrayOperations.sortTris(faces, vertices, splitAxis);
        this.faces = faces;
        aabb = new AxisAlignedBoundingBox(faces, vertices);

        int numFaces = faces.length/3;

        if(0 < maxDepth) {
            float a = aabb.getA()[splitAxis];
            float b = aabb.getB()[splitAxis];

            int splitIdx = 0;
            float minCost = Float.MAX_VALUE;
            for (int i = 0; i < splitChecks; i++)
            {
                int splitPoint;
                for (splitPoint = 0; splitPoint < numFaces; splitPoint++) if(vertices[faces[splitPoint * 3]*3+splitAxis] > ScalarMath.lerp(a, b, (i + 1) * 1f / (float) (splitChecks + 1))) break;
                AxisAlignedBoundingBox aabbA = new AxisAlignedBoundingBox(Arrays.copyOfRange(faces, 0, splitPoint * 3), vertices);
                AxisAlignedBoundingBox aabbB = new AxisAlignedBoundingBox(Arrays.copyOfRange(faces, splitPoint * 3, faces.length), vertices);
                float cost = getSAHCost(1f, 2f, aabbA, aabbB);
                if(cost < minCost)
                {
                    splitIdx = splitPoint;
                    minCost = cost;
                }
            }

            if(minCost != Float.MAX_VALUE && 0 != splitIdx * 3 && splitIdx * 3 != faces.length)
            {
                childNodes.add(new BoundingVolumeHierarchy(Arrays.copyOfRange(faces, 0, splitIdx * 3), vertices, splitAxis + 1, splitChecks, maxDepth, 1));
                childNodes.add(new BoundingVolumeHierarchy(Arrays.copyOfRange(faces, splitIdx * 3, faces.length), vertices, splitAxis + 1, splitChecks, maxDepth, 1));
            }
        }
    }

    private BoundingVolumeHierarchy(int[] faces, float[] vertices, int splitAxis, int splitChecks, int maxDepth, int depth)
    {
        childNodes = new LinkedList<>();
        splitAxis = splitAxis > 2 ? 0 : splitAxis;

        faces = ArrayOperations.sortTris(faces, vertices, splitAxis);
        this.faces = faces;
        aabb = new AxisAlignedBoundingBox(faces, vertices);

        int numFaces = faces.length/3;

        if(depth < maxDepth) {
            float a = aabb.getA()[splitAxis];
            float b = aabb.getB()[splitAxis];

            int splitIdx = 0;
            float minCost = Float.MAX_VALUE;
            for (int i = 0; i < splitChecks; i++)
            {
                int splitPoint;
                for (splitPoint = 0; splitPoint < numFaces; splitPoint++) if(vertices[faces[splitPoint * 3]*3+splitAxis] > ScalarMath.lerp(a, b, (i + 1) * 1f / (float) (splitChecks + 1))) break;
                AxisAlignedBoundingBox aabbA = new AxisAlignedBoundingBox(Arrays.copyOfRange(faces, 0, splitPoint * 3), vertices);
                AxisAlignedBoundingBox aabbB = new AxisAlignedBoundingBox(Arrays.copyOfRange(faces, splitPoint * 3, faces.length), vertices);
                float cost = getSAHCost(1f, 2f, aabbA, aabbB);
                if(cost < minCost)
                {
                    splitIdx = splitPoint;
                    minCost = cost;
                }
            }

            if(minCost != Float.MAX_VALUE && 0 != splitIdx * 3 && splitIdx * 3 != faces.length)
            {
                childNodes.add(new BoundingVolumeHierarchy(Arrays.copyOfRange(faces, 0, splitIdx * 3), vertices, splitAxis + 1, splitChecks, maxDepth, depth + 1));
                childNodes.add(new BoundingVolumeHierarchy(Arrays.copyOfRange(faces, splitIdx * 3, faces.length), vertices, splitAxis + 1, splitChecks, maxDepth, depth + 1));
            }
        }
    }

    private float getSAHCost(float tTraversal, float tIntersection, AxisAlignedBoundingBox a, AxisAlignedBoundingBox b)
    {
        float sa = aabb.getHalfSurfaceArea();
        return tTraversal + a.getHalfSurfaceArea()/sa * a.getFaces().length * tIntersection + b.getHalfSurfaceArea()/sa * b.getFaces().length * tIntersection;
    }

    public AxisAlignedBoundingBox getAabb() {
        return aabb;
    }

    public BoundingVolumeHierarchy[] hitLeaf(float[] cameraPos, float[] ray, float padding)
    {
        ArrayList<BoundingVolumeHierarchy> leaves = new ArrayList<>();
        int[] depth = new int[1];

        if(aabb.hitAABB(cameraPos, ray, padding))
        {
            if(childNodes.isEmpty()) leaves.add(this);
            else for (BoundingVolumeHierarchy child : childNodes) child.hitLeaf(cameraPos, ray, padding, leaves, depth);
        }

        BoundingVolumeHierarchy[] leavesArr = new BoundingVolumeHierarchy[leaves.size()];
        for (int i = 0; i < leaves.size(); i++) leavesArr[i] = leaves.get(i);
        return leavesArr;
    }

    private void hitLeaf(float[] cameraPos, float[] ray, float padding, ArrayList<BoundingVolumeHierarchy> leaves, int[] depth)
    {
        depth[0]++;
        if(aabb.hitAABB(cameraPos, ray, padding))
        {
            if(childNodes.isEmpty()) leaves.add(this);
            else for (BoundingVolumeHierarchy child : childNodes) child.hitLeaf(cameraPos, ray, padding);
        }
    }

    public BoundingVolumeHierarchy[] getLeaves()
    {
        ArrayList<BoundingVolumeHierarchy> leaves = new ArrayList<>();
        if(childNodes.isEmpty()) leaves.add(this);
        else for (BoundingVolumeHierarchy child : childNodes) child.getLeaves(leaves);

        BoundingVolumeHierarchy[] leavesArr = new BoundingVolumeHierarchy[leaves.size()];
        for (int i = 0; i < leaves.size(); i++) leavesArr[i] = leaves.get(i);
        return leavesArr;
    }

    private void getLeaves(ArrayList<BoundingVolumeHierarchy> leaves)
    {
        if(childNodes.isEmpty()) leaves.add(this);
        else for (BoundingVolumeHierarchy child : childNodes) child.getLeaves(leaves);
    }

    public LinkedList<BoundingVolumeHierarchy> getChildNodes() {
        return childNodes;
    }

    public int[] getFaces() {
        return faces;
    }
}