class_name SpiderLeg
extends RefCounted
## One spider leg: analytic two-bone IK plus its own step state machine.
##
## The leg never plays a canned animation. Every frame it is told where its
## foot should be standing; it decides on its own when that is far enough away
## to be worth taking a step, and then swings the foot along an arc. The joint
## angles fall out of the IK solve, so the leg bends correctly on stairs,
## slopes, walls and ceilings without any extra work.

var index: int = 0
var side: float = 1.0          # +1 right, -1 left
var group: int = 0             # alternating tetrapod gait group
var rest_local: Vector3        # ideal foot position in body space
var hip_local: Vector3         # where the leg meets the body

var femur_len: float = 0.62
var tibia_len: float = 0.72
var tarsus_len: float = 0.42

# --- swing state ---
## Where in the gait cycle this leg swings. Shifting it is how a leg that has
## been left behind asks to move now: it re-enters the swing window at once and
## the whole gait re-staggers around it.
var phase_offset: float = 0.0
var swinging: bool = false
var foot: Vector3              # current world foot position
var step_from: Vector3
var step_to: Vector3
var plant_normal: Vector3 = Vector3.UP

# --- visuals ---
var femur: MeshInstance3D
var tibia: MeshInstance3D
var tarsus: MeshInstance3D
var knee_ball: MeshInstance3D
var ankle_ball: MeshInstance3D
var claw: MeshInstance3D

var knee_pos: Vector3
var ankle_pos: Vector3


func is_stepping() -> bool:
	return swinging


func reach() -> float:
	return femur_len + tibia_len + tarsus_len


## Lift off from wherever the foot is standing.
func begin_swing() -> void:
	step_from = foot
	step_to = foot
	swinging = true


## Drive one frame of a swing.
##
## `t` runs 0 -> 1 across the swing window. Two things make this read as a leg
## rather than a hop:
##
##  - the horizontal travel is eased in AND out (smoothstep), so the foot
##    leaves the ground and arrives at the next one with zero speed. The old
##    curve was ease-out only, which covered most of the distance in the first
##    few frames — that is exactly what "the legs jump" was;
##  - `target` is re-read every frame instead of being fixed at lift-off, so
##    the foot tracks where the ground actually is by the time it lands, and
##    the landing is exact rather than approximate.
func advance_swing(t: float, target: Vector3, up: Vector3, height: float) -> void:
	step_to = target
	var k := clampf(t, 0.0, 1.0)
	var e := smoothstep(0.0, 1.0, k)
	# a slightly front-loaded arc: lifts briskly, comes down softly
	var lift := sin(pow(k, 0.88) * PI) * height
	foot = step_from.lerp(step_to, e) + up * lift


## Put the foot down. Returns true on the frame it actually lands.
func plant() -> bool:
	if not swinging:
		return false
	swinging = false
	foot = step_to
	return true


## Solve the chain and move the meshes. `body_up` biases the knee upwards so the
## leg arches over the body the way a spider's does.
func solve(hip_world: Vector3, body_up: Vector3, outward: Vector3) -> void:
	# Hard limit: a foot can never be further from its hip than the leg is long.
	# The gait normally keeps well inside this, but when the body accelerates
	# away — rolling onto a wall, say — the foot would otherwise be left behind
	# and the leg would render as a straight, obviously broken stick. Dragging
	# the foot in is what a real leg does when it runs out of reach.
	var to_foot := foot - hip_world
	var span := to_foot.length()
	var limit := reach() * 0.94
	if span > limit and span > 0.0001:
		foot = hip_world + to_foot * (limit / span)

	var ankle := foot + body_up * tarsus_len * 0.82 + outward * tarsus_len * 0.12
	var to_ankle := ankle - hip_world
	var d := to_ankle.length()
	var min_d := absf(femur_len - tibia_len) + 0.02
	var max_d := femur_len + tibia_len - 0.02
	if d < 0.0001:
		to_ankle = outward * 0.01
		d = 0.01
	var axis := to_ankle / d
	d = clampf(d, min_d, max_d)
	ankle = hip_world + axis * d

	# pole vector: knee rides high and slightly outboard
	# knees ride high above the back — what makes a spider look like a spider
	var pole := (body_up * 1.45 + outward * 0.28).normalized()
	var perp := pole - axis * pole.dot(axis)
	if perp.length_squared() < 0.0001:
		perp = outward - axis * outward.dot(axis)
	if perp.length_squared() < 0.0001:
		perp = Vector3.UP - axis * Vector3.UP.dot(axis)
	perp = perp.normalized()

	var a := (femur_len * femur_len - tibia_len * tibia_len + d * d) / (2.0 * d)
	var h := sqrt(maxf(femur_len * femur_len - a * a, 0.0))
	knee_pos = hip_world + axis * a + perp * h
	ankle_pos = ankle

	_place(femur, hip_world, knee_pos)
	_place(tibia, knee_pos, ankle_pos)
	_place(tarsus, ankle_pos, foot)
	if knee_ball:
		knee_ball.global_position = knee_pos
	if ankle_ball:
		ankle_ball.global_position = ankle_pos
	if claw:
		claw.global_position = foot


## Stretch a 1 m tall cylinder mesh so that it spans exactly from -> to.
static func _place(seg: MeshInstance3D, from: Vector3, to: Vector3) -> void:
	if seg == null:
		return
	var d := to - from
	var length := d.length()
	if length < 0.0001:
		return
	var y_axis := d / length
	var ref := Vector3.UP if absf(y_axis.dot(Vector3.UP)) < 0.95 else Vector3.RIGHT
	var x_axis := y_axis.cross(ref).normalized()
	var z_axis := x_axis.cross(y_axis)
	var thick: float = seg.get_meta("thick", 1.0)
	# scale is baked straight into the basis so the result is independent of
	# whatever transform the parent node happens to have this frame
	var b := Basis(x_axis * thick, y_axis * length, z_axis * thick)
	seg.global_transform = Transform3D(b, (from + to) * 0.5)
