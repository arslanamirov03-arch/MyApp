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

# --- step state ---
var foot: Vector3              # current world foot position
var step_from: Vector3
var step_to: Vector3
var step_t: float = 1.0        # >= 1.0 means planted
var step_time: float = 0.18
var step_height: float = 0.28
var phase_jitter: float = 0.0
var plant_normal: Vector3 = Vector3.UP
var settled: float = 0.0       # small vertical settle after touchdown

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
	return step_t < 1.0


func reach() -> float:
	return femur_len + tibia_len + tarsus_len


## Begin a swing towards `target`.
func begin_step(target: Vector3, speed01: float, up: float) -> void:
	step_from = foot
	step_to = target
	step_t = 0.0
	# fast legs when running, lazy legs when creeping
	step_time = lerpf(0.17, 0.075, clampf(speed01, 0.0, 1.0)) + phase_jitter * 0.6
	step_height = lerpf(0.24, 0.55, clampf(speed01, 0.0, 1.0)) * up


## Advance the swing. Returns true on the frame the foot touches down.
func advance(delta: float, up_vec: Vector3) -> bool:
	if step_t >= 1.0:
		if settled > 0.0:
			settled = maxf(settled - delta * 5.0, 0.0)
		return false
	step_t = minf(step_t + delta / maxf(step_time, 0.01), 1.0)
	# ease-out: the leg snaps out quickly and settles softly, like a real one
	var e := 1.0 - pow(1.0 - step_t, 2.9)
	var arc := sin(step_t * PI)
	foot = step_from.lerp(step_to, e) + up_vec * arc * step_height
	if step_t >= 1.0:
		foot = step_to
		settled = 1.0
		return true
	return false


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
