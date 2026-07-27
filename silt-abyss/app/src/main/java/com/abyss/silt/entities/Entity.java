package com.abyss.silt.entities;

import android.graphics.Canvas;

import com.abyss.silt.World;
import com.abyss.silt.core.Art;
import com.abyss.silt.core.Cam;

/** Everything that lives in the water. */
public abstract class Entity {
    public World world;

    public float x, y;            // world units
    public float vx, vy;
    public float r = 10f;         // collision radius
    public float angle = 0f;      // facing, radians
    public float t = 0f;          // personal clock, seconds

    public boolean dead = false;
    public boolean removed = false;

    /** Collision flags written by World.moveAndCollide each step. */
    public boolean onGround = false;
    public boolean hitWall = false;

    /** Set by the world each frame; how much light falls on this entity (0..1). */
    public float lit = 0f;

    public Entity(World w, float x, float y) {
        this.world = w;
        this.x = x;
        this.y = y;
    }

    public abstract void update(float dt);

    public abstract void draw(Canvas c, Cam cam, Art art);

    /** Larger of the two radii used for "is the player touching me" tests. */
    public float hitRadius() {
        return r;
    }

    /** Light this entity emits, in world units. 0 for most things. */
    public float lightRadius() {
        return 0f;
    }

    public float lightIntensity() {
        return 0f;
    }

    public boolean overlaps(Entity o) {
        float rr = hitRadius() + o.hitRadius();
        float dx = o.x - x, dy = o.y - y;
        return dx * dx + dy * dy < rr * rr;
    }

    protected void faceVelocity(float dt, float rate) {
        float sp = vx * vx + vy * vy;
        if (sp > 4f) {
            float target = (float) Math.atan2(vy, vx);
            angle = com.abyss.silt.core.V.angApproach(angle, target, rate * dt);
        }
    }
}
