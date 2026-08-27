class_name Vec
extends RefCounted
## Small vector helpers that stay well behaved in the degenerate cases this game
## hits constantly.
##
## Vector3.slerp() builds its rotation axis from the cross product of the two
## vectors. When they are nearly collinear — which is every single frame that
## the spider walks on flat ground and the surface normal barely changes — that
## axis is a denormal, and normalising it produces something that is not quite
## unit length, which Godot then rejects. Interpolating linearly and
## renormalising is exact in that case and indistinguishable in every other.

const COLLINEAR := 0.9995


## Normalise, with a fallback for degenerate or non-finite input.
static func unit(v: Vector3, fallback: Vector3) -> Vector3:
	if not (is_finite(v.x) and is_finite(v.y) and is_finite(v.z)):
		return fallback
	if v.length_squared() < 1e-6:
		return fallback
	return v.normalized()


## Interpolate between two directions. Both are assumed to be unit length; the
## result always is.
static func slerp_dir(from: Vector3, to: Vector3, weight: float) -> Vector3:
	var d := clampf(from.dot(to), -1.0, 1.0)
	if absf(d) > COLLINEAR:
		# collinear (or opposed): lerp and renormalise
		var mixed := from.lerp(to, weight)
		if mixed.length_squared() < 1e-6:
			# exactly opposed and half way: pick any perpendicular direction
			var axis := from.cross(Vector3.UP)
			if axis.length_squared() < 1e-6:
				axis = from.cross(Vector3.RIGHT)
			return unit(axis, to)
		return mixed.normalized()
	return unit(from.slerp(to, weight), to)


## Carry a direction along with a rotating frame.
##
## When the spider rolls from the floor onto a wall, its up vector sweeps 90
## degrees. Simply re-projecting "forward" onto the new tangent plane collapses
## to zero at the moment forward and up line up — which is exactly the moment
## the camera is pointing at the wall being climbed. Rotating the vector by the
## same rotation that moved the up vector keeps the frame continuous instead.
static func transport(v: Vector3, from_up: Vector3, to_up: Vector3) -> Vector3:
	var axis := from_up.cross(to_up)
	if axis.length_squared() < 1e-10:
		return v
	var angle := from_up.angle_to(to_up)
	if absf(angle) < 1e-5:
		return v
	return v.rotated(axis.normalized(), angle)
