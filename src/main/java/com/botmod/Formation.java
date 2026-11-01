package com.botmod;

import net.minecraft.world.phys.Vec3;

public enum Formation {
    LINE, COLUMN, WEDGE, CIRCLE, BOX, SPREAD;

    /** World position of slot i out of n, relative to an anchor facing yawDeg (Minecraft yaw). */
    public Vec3 slot(int i, int n, double spacing, Vec3 anchor, float yawDeg) {
        double fx, fz; // local: +x = right, +z = forward
        switch (this) {
            case LINE -> { fx = (i - (n - 1) / 2.0) * spacing; fz = 0; }
            case COLUMN -> { fx = 0; fz = -i * spacing; }
            case WEDGE -> {
                int row = (i + 1) / 2; boolean right = (i % 2 == 1);
                fx = (right ? 1 : -1) * row * spacing; fz = -row * spacing;
                if (i == 0) { fx = 0; fz = 0; }
            }
            case CIRCLE -> {
                double r = Math.max(spacing, n * spacing / (2 * Math.PI));
                double a = 2 * Math.PI * i / Math.max(1, n);
                fx = Math.cos(a) * r; fz = Math.sin(a) * r;
            }
            case BOX -> {
                int side = (int) Math.ceil(Math.sqrt(n));
                fx = (i % side - (side - 1) / 2.0) * spacing;
                fz = -(i / side) * spacing;
            }
            default -> { // SPREAD: golden-angle sunflower, good for huge counts
                double r = spacing * Math.sqrt(i + 1);
                double a = i * 2.399963;
                fx = Math.cos(a) * r; fz = Math.sin(a) * r;
            }
        }
        double yaw = Math.toRadians(yawDeg);
        // Minecraft: forward = (-sin(yaw), cos(yaw)), right = (-cos(yaw), -sin(yaw))
        double wx = fx * -Math.cos(yaw) + fz * -Math.sin(yaw);
        double wz = fx * -Math.sin(yaw) + fz * Math.cos(yaw);
        return anchor.add(wx, 0, wz);
    }
}
