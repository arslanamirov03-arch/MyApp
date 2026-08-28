class_name CameraRig
extends Node3D
## Third-person camera that survives the spider walking up walls and across
## ceilings: its up vector blends part-way towards the surface normal instead of
## snapping to it, which keeps the horizon readable without making the player
## seasick.

const MIN_PITCH := -1.15
const MAX_PITCH := 0.85

var spider: Spider
var distance := 4.4
var height := 0.75
var look_delta := Vector2.ZERO      # consumed every frame
var pitch := -0.20

var arm: SpringArm3D
var cam: Camera3D

var _fwd := Vector3.FORWARD
var _up := Vector3.UP
var _pos := Vector3.ZERO
var _shake := 0.0


func _ready() -> void:
	arm = SpringArm3D.new()
	arm.spring_length = distance
	arm.margin = 0.25
	arm.collision_mask = 1
	add_child(arm)

	cam = Camera3D.new()
	cam.fov = 74.0
	cam.near = 0.05
	cam.far = 140.0
	cam.current = true
	arm.add_child(cam)

	_pos = global_position


func _process(delta: float) -> void:
	if spider == null:
		return

	var sens := 0.0042 * Settings.look_sensitivity
	var inv := -1.0 if Settings.invert_y else 1.0

	# Blend the camera's up towards the surface the spider is on. Every vector
	# below is re-checked before it is used as a rotation axis: on the
	# ceiling-to-floor transition the up vector passes through the spider's
	# own facing direction, and an axis that degenerates there would otherwise
	# throw for as long as the camera stayed in that state.
	# Follow the surface normal all the way, not part of the way. A partial blend
	# leaves the camera's frame and the spider's frame disagreeing, and the
	# movement direction derived from the camera then drifts and eventually
	# reverses part-way up a wall.
	var want_up := spider.surface_normal
	var prev_up := _up
	_up = Vec.slerp_dir(_up, want_up, 1.0 - exp(-9.0 * delta))
	# rotate the heading with the frame so it never collapses against the wall
	_fwd = Vec.transport(_fwd, prev_up, _up)

	# keep the forward vector continuous as the up vector rotates
	_fwd = Vec.unit(_fwd - _up * _fwd.dot(_up), Vector3.ZERO)
	if _fwd == Vector3.ZERO:
		var alt := spider.body_forward()
		_fwd = Vec.unit(alt - _up * alt.dot(_up), _up.cross(Vector3.RIGHT))
		_fwd = Vec.unit(_fwd, Vector3.FORWARD)
	_fwd = _fwd.rotated(_up, -look_delta.x * sens)
	pitch = clampf(pitch - look_delta.y * sens * inv, MIN_PITCH, MAX_PITCH)
	look_delta = Vector2.ZERO

	var right := Vec.unit(_fwd.cross(_up), Vector3.RIGHT)
	var dir := Vec.unit(_fwd.rotated(right, pitch), _fwd)

	# follow the spider, lagging slightly so fast turns read as motion
	var target := spider.global_position + _up * height * spider.body_size
	_pos = _pos.lerp(target, 1.0 - exp(-13.0 * delta))

	arm.spring_length = distance * Settings.camera_distance * spider.body_size

	var shake_v := Vector3.ZERO
	_shake = maxf(_shake - delta * 2.0, 0.0)
	if spider.last_speed01 > 0.55:
		_shake = maxf(_shake, (spider.last_speed01 - 0.55) * 0.6)
	if _shake > 0.0:
		shake_v = Vector3(
			sin(Time.get_ticks_msec() * 0.041) * _shake,
			cos(Time.get_ticks_msec() * 0.053) * _shake, 0.0) * 0.035

	var xf := Transform3D(Basis(), _pos + shake_v)
	global_transform = xf.looking_at(_pos + dir, _up)

	cam.fov = lerpf(cam.fov, lerpf(72.0, 88.0, spider.last_speed01), 1.0 - exp(-4.0 * delta))
