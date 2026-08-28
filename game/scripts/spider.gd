class_name Spider
extends CharacterBody3D
## Player-controlled giant spider.
##
## Movement is a surface walker: the body keeps its own "up" vector, which is
## the normal of whatever it is currently standing on. Walking into a wall rolls
## that up vector onto the wall, so floors, walls and ceilings are all just
## surfaces — there is no separate "climb" mode to toggle.
##
## Nothing here is keyframed. The legs are eight independent IK chains that
## decide for themselves when to step, and the body rides on the plane through
## the eight feet, so slopes, stairs and door sills all produce motion for free.

signal footstep(world_pos: Vector3, speed01: float)
signal landed(impact: float)

const LEG_COUNT := 8
## Tallest thing the spider can simply walk up onto rather than climb. Palace
## steps, plinths, kerbs and furniture are all bigger than a house's were, and
## anything taller than this still gets climbed as a wall — so a generous value
## just means fewer places to snag.
const MAX_STEP := 1.45
## Below this a ledge is just surface noise, not something to climb onto.
const MIN_STEP := 0.12
## How far the spider can reach out and catch a surface while in mid-air.
const GRAB_RANGE := 1.5

## Three gaits. The slow one is the default: the palace and the garden are
## worth looking at, and a spider that crosses a ballroom in two seconds is not
## exploring it.
@export var walk_speed := 1.7
@export var fast_speed := 3.6
@export var run_speed := 6.6
@export var acceleration := 16.0
@export var air_acceleration := 3.0
@export var turn_rate := 9.0
@export var ride_height := 0.68
@export var jump_speed := 6.2
@export var gravity := 13.0
@export var stick_force := 9.0
@export var body_size := 1.0

# --- input, written by the HUD or the keyboard ---
var move_input := Vector2.ZERO
## 0 = slow walk, 1 = fast walk, 2 = run
var speed_mode := 0
var jump_queued := false
var attack_queued := false

# --- surface state ---
var surface_normal := Vector3.UP
var attached := true
var airborne := 0.0
var facing := Vector3.FORWARD
var last_speed01 := 0.0

var camera_basis := Basis()

# --- rig ---
var rig: Node3D
var carapace: MeshInstance3D
var abdomen_pivot: Node3D
var abdomen: MeshInstance3D
var sternum: MeshInstance3D
var pedicel: MeshInstance3D
var fang_l: Node3D
var fang_r: Node3D
var palp_l: MeshInstance3D
var palp_r: MeshInstance3D
var legs: Array[SpiderLeg] = []

var _rig_basis := Basis()
var _sway := Vector3.ZERO
var _sway_vel := Vector3.ZERO
var _abd_offset := Vector3.ZERO
var _abd_vel := Vector3.ZERO
var _prev_velocity := Vector3.ZERO
var _idle_timer := 0.0
var _attack_timer := 0.0
var _time := 0.0
var _rng := RandomNumberGenerator.new()
var _step_lift := 0.0
var _wish_dir := Vector3.ZERO
var _jump_lock := 0.0
var _righting := 0.0
var _stuck_time := 0.0
var _stuck_from := Vector3.ZERO
var _space: PhysicsDirectSpaceState3D
var _exclude: Array[RID] = []


func _ready() -> void:
	_rng.randomize()
	motion_mode = CharacterBody3D.MOTION_MODE_FLOATING
	var shape := SphereShape3D.new()
	shape.radius = 0.40 * body_size
	var cs := CollisionShape3D.new()
	cs.shape = shape
	add_child(cs)
	collision_layer = 2
	collision_mask = 1
	_exclude = [get_rid()]
	_build_rig()
	_rig_basis = global_transform.basis


# ===========================================================================
#  rig construction
# ===========================================================================

func _build_rig() -> void:
	# the "chitin" bark scan turned out to have moss in it, which made the
	# spider green; the leather scan reads as a waxy exoskeleton instead
	# Against the now brightly-lit rooms a light tint read as polished copper.
	# Dark keeps it a spider, and keeps it legible as a silhouette.
	var chitin := MaterialLib.object_surface("leather", 0.42, 1.8,
		Color(0.40, 0.34, 0.31))
	chitin.roughness = 0.36
	chitin.metallic = 0.16
	chitin.specular_mode = BaseMaterial3D.SPECULAR_SCHLICK_GGX
	# The house is dark and the spider is usually between the player and a lamp,
	# so without a rim it reads as a flat black cut-out. This catches light along
	# the silhouette and gives the carapace its waxy look.
	chitin.rim_enabled = true
	chitin.rim = 0.75
	chitin.rim_tint = 0.35
	var joint_mat := MaterialLib.object_surface("chitin", 0.22, 1.4,
		Color(0.30, 0.25, 0.22))
	joint_mat.roughness = 0.52
	joint_mat.rim_enabled = true
	joint_mat.rim = 0.6

	rig = Node3D.new()
	rig.name = "Rig"
	rig.top_level = true          # driven manually in world space
	add_child(rig)

	# --- cephalothorax ---
	carapace = MeshInstance3D.new()
	var head := SphereMesh.new()
	head.radius = 0.5
	head.height = 1.0
	head.radial_segments = 20
	head.rings = 12
	carapace.mesh = head
	carapace.scale = Vector3(0.50, 0.30, 0.68) * body_size
	carapace.position = Vector3(0.0, 0.0, -0.16) * body_size
	carapace.material_override = chitin
	rig.add_child(carapace)

	# --- abdomen on a spring pivot so it lags behind the body ---
	abdomen_pivot = Node3D.new()
	abdomen_pivot.position = Vector3(0.0, 0.05, 0.42) * body_size
	rig.add_child(abdomen_pivot)

	abdomen = MeshInstance3D.new()
	var abd := SphereMesh.new()
	abd.radius = 0.5
	abd.height = 1.0
	abd.radial_segments = 22
	abd.rings = 14
	abdomen.mesh = abd
	abdomen.scale = Vector3(0.58, 0.52, 0.94) * body_size
	abdomen.position = Vector3(0.0, 0.02, 0.42) * body_size
	abdomen.material_override = chitin
	abdomen_pivot.add_child(abdomen)

	# a few darker markings on the abdomen
	for i in range(3):
		var mark := MeshInstance3D.new()
		var ms := SphereMesh.new()
		ms.radius = 0.5
		ms.height = 1.0
		mark.mesh = ms
		mark.scale = Vector3(0.22 - i * 0.05, 0.05, 0.18) * body_size
		mark.position = Vector3(0.0, 0.30 - i * 0.02, 0.20 + i * 0.24) * body_size
		mark.material_override = MaterialLib.plain(Color(0.05, 0.04, 0.035), 0.75)
		abdomen_pivot.add_child(mark)

	# Underside plate. The legs spring from this rather than from thin air,
	# which is most of why the old rig looked like loose parts flying in
	# formation.
	sternum = MeshInstance3D.new()
	var st := SphereMesh.new()
	st.radius = 0.5
	st.height = 1.0
	st.radial_segments = 16
	st.rings = 10
	sternum.mesh = st
	sternum.scale = Vector3(0.46, 0.16, 0.72) * body_size
	sternum.position = Vector3(0.0, -0.10, -0.10) * body_size
	sternum.material_override = joint_mat
	rig.add_child(sternum)

	# Pedicel: the waist. The abdomen rides on a spring, so this is re-spanned
	# every frame between the back of the carapace and the front of the abdomen
	# and the two can never be seen to come apart.
	pedicel = MeshInstance3D.new()
	var pm2 := CylinderMesh.new()
	pm2.top_radius = 0.5
	pm2.bottom_radius = 0.5
	pm2.height = 1.0
	pm2.radial_segments = 10
	pedicel.mesh = pm2
	pedicel.set_meta("thick", 0.20 * body_size)
	pedicel.material_override = joint_mat
	pedicel.top_level = true
	add_child(pedicel)

	_build_eyes()
	_build_mouthparts(chitin)
	_build_legs(chitin, joint_mat)


func _build_eyes() -> void:
	# eight eyes: two big principal ones and six smaller, in two arcs
	var eye_mat := StandardMaterial3D.new()
	eye_mat.albedo_color = Color(0.02, 0.01, 0.015)
	eye_mat.roughness = 0.04
	eye_mat.metallic = 0.2
	eye_mat.emission_enabled = true
	eye_mat.emission = Color(0.32, 0.03, 0.02)
	eye_mat.emission_energy_multiplier = 0.9

	var layout := [
		Vector3(-0.10, 0.14, -0.50), Vector3(0.10, 0.14, -0.50),   # principal
		Vector3(-0.21, 0.10, -0.44), Vector3(0.21, 0.10, -0.44),
		Vector3(-0.16, 0.19, -0.41), Vector3(0.16, 0.19, -0.41),
		Vector3(-0.05, 0.20, -0.45), Vector3(0.05, 0.20, -0.45),
	]
	var sizes := [0.062, 0.062, 0.042, 0.042, 0.034, 0.034, 0.030, 0.030]
	for i in range(layout.size()):
		var e := MeshInstance3D.new()
		var sm := SphereMesh.new()
		sm.radius = sizes[i] * body_size
		sm.height = sizes[i] * 2.0 * body_size
		sm.radial_segments = 12
		sm.rings = 8
		e.mesh = sm
		e.position = layout[i] * body_size
		e.material_override = eye_mat
		rig.add_child(e)


func _build_mouthparts(chitin: Material) -> void:
	var fang_mat := MaterialLib.plain(Color(0.09, 0.07, 0.06), 0.25)
	for side_v in [-1.0, 1.0]:
		var side: float = side_v
		var pivot := Node3D.new()
		pivot.position = Vector3(0.13 * side, -0.02, -0.46) * body_size
		rig.add_child(pivot)

		var base := MeshInstance3D.new()
		var bm := CylinderMesh.new()
		bm.top_radius = 0.055
		bm.bottom_radius = 0.085
		bm.height = 0.26
		base.mesh = bm
		base.position = Vector3(0.0, -0.11, 0.0) * body_size
		base.scale = Vector3.ONE * body_size
		base.material_override = chitin
		pivot.add_child(base)

		var fang := MeshInstance3D.new()
		var fm := CylinderMesh.new()
		fm.top_radius = 0.004
		fm.bottom_radius = 0.05
		fm.height = 0.3
		fang.mesh = fm
		fang.position = Vector3(0.0, -0.36, -0.03) * body_size
		fang.rotation_degrees = Vector3(-16.0, 0.0, 0.0)
		fang.scale = Vector3.ONE * body_size
		fang.material_override = fang_mat
		pivot.add_child(fang)

		if side < 0.0:
			fang_l = pivot
		else:
			fang_r = pivot

		# pedipalp: short feeler in front of the fangs
		var palp := MeshInstance3D.new()
		var pm := CylinderMesh.new()
		pm.top_radius = 0.025
		pm.bottom_radius = 0.05
		pm.height = 0.44
		palp.mesh = pm
		palp.position = Vector3(0.26 * side, -0.16, -0.60) * body_size
		palp.rotation_degrees = Vector3(-62.0, 0.0, 22.0 * side)
		palp.scale = Vector3.ONE * body_size
		palp.material_override = chitin
		rig.add_child(palp)
		if side < 0.0:
			palp_l = palp
		else:
			palp_r = palp


func _build_legs(chitin: Material, joint_mat: Material) -> void:
	# hips run down each side of the cephalothorax, front to back
	# hips sit on the flank of the carapace, not inside it
	var hips := [
		Vector3(-0.23, 0.02, -0.30), Vector3(-0.25, 0.00, -0.10),
		Vector3(-0.25, -0.01, 0.08), Vector3(-0.23, -0.03, 0.26),
	]
	var rests := [
		Vector3(-1.20, 0.0, -1.46), Vector3(-1.54, 0.0, -0.52),
		Vector3(-1.54, 0.0, 0.48), Vector3(-1.26, 0.0, 1.34),
	]
	# front and rear legs are longer, like a real spider's leg I and IV
	var lengths := [
		[0.80, 1.00, 0.58], [0.74, 0.90, 0.50],
		[0.74, 0.90, 0.50], [0.82, 1.04, 0.60],
	]

	for i in range(LEG_COUNT):
		var slot := i % 4
		var side := -1.0 if i < 4 else 1.0
		var leg := SpiderLeg.new()
		leg.index = i
		leg.side = side
		leg.group = (slot % 2) if side < 0.0 else (1 - slot % 2)
		leg.hip_local = Vector3(absf(hips[slot].x) * side, hips[slot].y, hips[slot].z) * body_size
		leg.rest_local = Vector3(absf(rests[slot].x) * side, -ride_height, rests[slot].z) * body_size
		leg.femur_len = lengths[slot][0] * body_size
		leg.tibia_len = lengths[slot][1] * body_size
		leg.tarsus_len = lengths[slot][2] * body_size
		leg.phase_jitter = _rng.randf_range(-0.022, 0.022)
		leg.foot = global_position + leg.rest_local

		# thin: a spider's leg is a bristle, not a sausage
		leg.femur = _segment(chitin, 0.038 * body_size, 0)
		leg.tibia = _segment(chitin, 0.027 * body_size, 0)
		leg.tarsus = _segment(chitin, 0.017 * body_size, 0)
		leg.knee_ball = _ball(joint_mat, 0.044 * body_size)
		leg.ankle_ball = _ball(joint_mat, 0.029 * body_size)
		leg.claw = _ball(MaterialLib.plain(Color(0.06, 0.05, 0.05), 0.3), 0.016 * body_size)
		# coxa: a stub on the body at the hip, so the leg has something to
		# come out of instead of starting in mid-air
		var coxa := _ball(joint_mat, 0.060 * body_size)
		coxa.top_level = false
		remove_child(coxa)
		rig.add_child(coxa)
		coxa.position = leg.hip_local
		legs.append(leg)


## A 1 m tall cylinder that SpiderLeg stretches between two joints.
##
## No child bristles: a segment is scaled non-uniformly (thin on X/Z, stretched
## on Y to span its joints), and anything parented to it inherits that shear,
## which turned the bristles into flat black slabs. The leather normal map
## carries the surface detail instead.
func _segment(mat: Material, thickness: float, _bristles: int) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.38
	cm.bottom_radius = 0.5
	cm.height = 1.0
	cm.radial_segments = 8
	mi.mesh = cm
	mi.set_meta("thick", thickness * 2.0)
	mi.material_override = mat
	mi.top_level = true
	add_child(mi)
	return mi


func _ball(mat: Material, radius: float) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = radius
	sm.height = radius * 2.0
	sm.radial_segments = 8
	sm.rings = 5
	mi.mesh = sm
	mi.material_override = mat
	mi.top_level = true
	add_child(mi)
	return mi


# ===========================================================================
#  simulation
# ===========================================================================

func _physics_process(delta: float) -> void:
	_space = get_world_3d().direct_space_state
	_time += delta

	# The surface probes have to use the direction the player is ASKING for, not
	# the direction we are actually moving. Deriving it from velocity deadlocks:
	# pressed up against a stair tread the velocity is zero, so nothing probes
	# forward, so the step is never detected, so the velocity stays zero.
	_wish_dir = _compute_wish(surface_normal)
	_update_surface(delta)
	_apply_movement(delta)
	move_and_slide()
	_right_self(delta)
	_unstick(delta)
	_update_gait(delta)
	_update_pose(delta)
	_update_mouthparts(delta)

	_prev_velocity = velocity


## Flip back over.
##
## Being under a ceiling and lying on your back look identical to the surface
## walker — both are "attached to something whose normal points down". They are
## told apart by what is underneath in world terms: from a real ceiling the
## ground is a storey away, whereas on your back it is right there. In that case
## the legs push off and the body rolls upright.
func _right_self(delta: float) -> void:
	if _righting > 0.0:
		_righting = maxf(_righting - delta, 0.0)
		return
	if not attached or surface_normal.y > -0.25:
		return
	var below := _ray(global_position, global_position + Vector3.DOWN * 2.2 * body_size)
	if not below:
		return                      # genuinely hanging from a ceiling
	var n: Vector3 = below.normal
	if n.y < 0.3:
		return
	_righting = 0.55
	surface_normal = n
	facing = Vec.unit(facing - n * facing.dot(n), Vector3.FORWARD)
	velocity = n * 2.2
	# throw the legs out so the flip is something the legs visibly do
	for leg in legs:
		leg.step_t = 1.0
		leg.foot = global_position + leg.rest_local * 1.3 + n * 0.2 * body_size


## Safety net: a concave collision mesh can swallow the body, and once inside it
## the solver will not push it back out. If we are asking to move and have gone
## nowhere for a while, lift out along the surface normal.
func _unstick(delta: float) -> void:
	if _wish_dir == Vector3.ZERO:
		_stuck_time = 0.0
		_stuck_from = global_position
		return
	if global_position.distance_to(_stuck_from) > 0.25 * body_size:
		_stuck_time = 0.0
		_stuck_from = global_position
		return
	_stuck_time += delta
	if _stuck_time > 0.9:
		global_position += surface_normal * 0.22 * body_size - _wish_dir * 0.06 * body_size
		_stuck_time = 0.0
		_stuck_from = global_position


## World-space direction the player is asking for, flattened onto the surface.
func _compute_wish(up: Vector3) -> Vector3:
	if move_input.length_squared() < 0.0004:
		return Vector3.ZERO
	var cam_fwd := -camera_basis.z
	var cam_right := camera_basis.x
	var fwd := cam_fwd - up * cam_fwd.dot(up)
	if fwd.length_squared() < 0.001:
		var alt := -camera_basis.y
		fwd = alt - up * alt.dot(up)
	fwd = fwd.normalized()
	var right := cam_right - up * cam_right.dot(up)
	right = right.normalized() if right.length_squared() > 0.001 else fwd.cross(up)
	var wish := right * move_input.x + fwd * move_input.y
	return wish.normalized() if wish.length_squared() > 0.0001 else Vector3.ZERO


func _ray(from: Vector3, to: Vector3) -> Dictionary:
	var q := PhysicsRayQueryParameters3D.create(from, to)
	q.exclude = _exclude
	q.collision_mask = 1
	return _space.intersect_ray(q)


## Work out which surface we are on, and roll `surface_normal` onto walls and
## ceilings as the spider walks into them.
func _update_surface(delta: float) -> void:
	var up := surface_normal
	var pos := global_position
	var reach := ride_height * 2.3 * body_size

	var move_dir := _wish_dir
	if move_dir == Vector3.ZERO and velocity.length_squared() > 0.04:
		move_dir = (velocity - up * velocity.dot(up))
		move_dir = move_dir.normalized() if move_dir.length_squared() > 0.0001 \
			else Vector3.ZERO

	# just jumped: stay detached long enough to actually leave the surface,
	# otherwise the ride-height spring cancels the jump on the very next frame
	_jump_lock = maxf(_jump_lock - delta, 0.0)
	if _jump_lock > 0.0:
		attached = false
		airborne += delta
		_step_lift = 0.0
		surface_normal = Vec.slerp_dir(surface_normal, Vector3.UP, 1.0 - exp(-3.0 * delta))
		return

	var found := false
	var new_normal := up
	var dist := ride_height * body_size

	# 1. straight down onto the current surface
	var down := _ray(pos + up * 0.15 * body_size, pos - up * reach)
	if down:
		found = true
		new_normal = down.normal
		dist = pos.distance_to(down.position)

	# 2. Anything in the way is either something to step onto or something to
	#    climb. Ask the step probe first; if the obstacle is too tall to step
	#    onto, roll the up vector onto its face and walk up it instead. That is
	#    what makes lamp posts, plinths, columns, tree trunks, statues and the
	#    outside of the building all climbable without special cases.
	_step_lift = 0.0
	if move_dir != Vector3.ZERO:
		_step_lift = _step_assist(pos, up, move_dir)
		if _step_lift <= 0.0:
			var wall := _ray(pos, pos + move_dir * 0.95 * body_size)
			if wall and wall.normal.dot(up) < 0.86:
				var d: float = pos.distance_to(wall.position)
				var blend: float = clampf(1.0 - (d - 0.35) / 0.6, 0.0, 1.0)
				if blend > 0.0:
					new_normal = new_normal.lerp(wall.normal, blend).normalized()
					if not down:
						dist = d
					found = true

		# 3. convex edge: the floor runs out ahead, so wrap around it
		if not down:
			var probe_at := pos + move_dir * 0.55 * body_size - up * 0.5 * body_size
			var back := _ray(probe_at, probe_at - move_dir * 0.9 * body_size)
			if back:
				new_normal = back.normal
				dist = pos.distance_to(back.position)
				found = true

	# 4. Nothing underfoot: reach out. A real spider crossing a gap catches the
	#    far side with a leg rather than dropping, and without this the only way
	#    off a wall was down. This is what lets you cross from one wall to the
	#    one facing it, and go up through a window opening onto the outside.
	if not found:
		var grab := _grab(pos, up)
		if not grab.is_empty():
			found = true
			new_normal = grab.normal
			dist = pos.distance_to(grab.position)

	if found:
		attached = true
		airborne = 0.0
		# rolling onto a new surface is fast but not instant
		var rate: float = 1.0 - exp(-14.0 * delta)
		var prev_up := surface_normal
		surface_normal = Vec.slerp_dir(surface_normal, Vec.unit(new_normal, surface_normal), rate)
		# carry the heading around with the frame instead of re-projecting it
		facing = Vec.unit(Vec.transport(facing, prev_up, surface_normal), facing)
		set_meta("surface_dist", dist)
	else:
		airborne += delta
		if airborne > 0.16:
			if attached:
				attached = false
			var rate2: float = 1.0 - exp(-7.0 * delta)
			surface_normal = Vec.slerp_dir(surface_normal, Vector3.UP, rate2)


## Feel around for any surface within reach and return the nearest hit.
func _grab(pos: Vector3, up: Vector3) -> Dictionary:
	var reach := GRAB_RANGE * body_size
	var b := _rig_basis
	var dirs: Array[Vector3] = [
		-up, up, b.x, -b.x, -b.z, b.z, Vector3.DOWN,
		(-up + b.x).normalized(), (-up - b.x).normalized(),
		(-up - b.z).normalized(), (-up + b.z).normalized(),
	]
	var best := {}
	var best_d := reach + 1.0
	for d in dirs:
		var hit := _ray(pos, pos + d * reach)
		if hit:
			var dist: float = pos.distance_to(hit.position)
			if dist < best_d:
				best_d = dist
				best = hit
	return best


## How far the body needs to rise to get onto the low obstacle in front of it.
## Returns 0.0 when there is nothing to step onto.
func _step_assist(pos: Vector3, up: Vector3, move_dir: Vector3) -> float:
	# Look just past the obstacle, not far beyond it: probing a whole body-length
	# ahead on a staircase lands three treads up, reads as too tall to step onto
	# and gives up.
	var ahead := pos + move_dir * 0.52 * body_size + up * 0.9 * body_size
	var landing := _ray(ahead, ahead - up * 1.8 * body_size)
	if not landing:
		return 0.0
	var landing_pos: Vector3 = landing.position
	var rise: float = (landing_pos - pos).dot(up) + ride_height * body_size
	# `rise` near zero means the probe just found the surface we are already
	# standing on — which is what happens on a wall, where a lift would peel the
	# spider straight off it. Only a genuinely higher ledge counts as a step.
	if rise < MIN_STEP * body_size or rise > MAX_STEP * body_size:
		return 0.0
	return rise


func _apply_movement(delta: float) -> void:
	var up := surface_normal
	var wish := _wish_dir
	var input_len := clampf(move_input.length(), 0.0, 1.0)

	var top_speed := walk_speed
	if speed_mode >= 2:
		top_speed = run_speed
	elif speed_mode == 1:
		top_speed = fast_speed
	var target := wish * top_speed * input_len

	if attached:
		var tangent := velocity - up * velocity.dot(up)
		tangent = tangent.move_toward(target, acceleration * delta)
		if jump_queued:
			# push off the surface along its normal, plus a forward lunge
			var lunge := facing if facing.length_squared() > 0.1 else -_rig_basis.z
			velocity = tangent + lunge * 2.4 + up * jump_speed
			attached = false
			airborne = 0.3
			_jump_lock = 0.30
			_step_lift = 0.0
		else:
			# hold the ride height with a spring, and keep a little pressure
			# into the surface so walls and ceilings stay stuck
			var dist: float = get_meta("surface_dist", ride_height * body_size)
			var err: float = (ride_height * body_size) - dist
			var normal_v: float = clampf(err * 14.0, -6.0, 6.0) - stick_force * 0.08
			# Rise over stair treads and low furniture. A sustained climb rate
			# reads as walking up; a ballistic impulse made it hop each tread.
			if _step_lift > 0.0:
				normal_v = maxf(normal_v, clampf(_step_lift * 9.0, 0.0, 4.5))
			velocity = tangent + up * normal_v
	else:
		# free fall: world gravity, weak mid-air steering
		var horiz := Vector3(velocity.x, 0.0, velocity.z)
		var want_h := Vector3(target.x, 0.0, target.z)
		horiz = horiz.move_toward(want_h, air_acceleration * delta)
		velocity = Vector3(horiz.x, velocity.y - gravity * delta, horiz.z)

	jump_queued = false

	last_speed01 = clampf((velocity - up * velocity.dot(up)).length() / run_speed, 0.0, 1.0)

	# face the way we are going
	var flat := velocity - up * velocity.dot(up)
	if flat.length() > 0.4:
		var want := flat.normalized()
		var rate := 1.0 - exp(-turn_rate * delta)
		facing = Vec.slerp_dir(facing, want, rate)
	facing = (facing - up * facing.dot(up))
	facing = facing.normalized() if facing.length_squared() > 0.001 else -_rig_basis.z


# ---------------------------------------------------------------------------
#  gait
# ---------------------------------------------------------------------------

func _update_gait(delta: float) -> void:
	var up := surface_normal
	var speed01 := last_speed01

	if not attached:
		_flail(delta)
		return

	var stepping := 0
	for leg in legs:
		if leg.advance(delta, up):
			footstep.emit(leg.foot, speed01)
		if leg.is_stepping():
			stepping += 1

	var busy_groups := {}
	for leg in legs:
		if leg.is_stepping():
			busy_groups[leg.group] = true

	# tighter tolerance = the feet are re-planted more often and slide less
	var threshold := lerpf(0.15, 0.40, speed01) * body_size
	_idle_timer += delta
	var hip_xf := rig.global_transform

	for leg in legs:
		if leg.is_stepping():
			continue
		var target := _foot_target(leg, speed01)
		var err := leg.foot.distance_to(target)
		var wants := err > threshold
		# A leg that is nearly out of reach re-plants at once, whatever the gait
		# would prefer. This is what keeps the legs under the spider while it
		# rolls from the floor onto a wall at full speed.
		var hip: Vector3 = hip_xf * leg.hip_local
		var urgent := hip.distance_to(leg.foot) > leg.reach() * 0.80
		if urgent:
			leg.begin_step(target, maxf(speed01, 0.75), body_size)
			busy_groups[leg.group] = true
			stepping += 1
			continue
		# a settled spider still fidgets: every so often one leg resets itself
		if not wants and speed01 < 0.05 and _idle_timer > 2.2 and _rng.randf() < 0.004:
			wants = true
			target += Vector3(_rng.randf_range(-0.06, 0.06), 0.0,
				_rng.randf_range(-0.06, 0.06)) * body_size
			_idle_timer = 0.0
		if not wants:
			continue
		if stepping >= 4:
			continue
		# alternating tetrapod: the other group must have finished its swing
		var other := 1 - leg.group
		if busy_groups.has(other):
			continue
		leg.begin_step(target, speed01, body_size)
		busy_groups[leg.group] = true
		stepping += 1
		if speed01 > 0.05:
			_idle_timer = 0.0


## Where this leg would like to plant its foot right now.
func _foot_target(leg: SpiderLeg, speed01: float) -> Vector3:
	var up := surface_normal
	var xf := Transform3D(_rig_basis, global_position)
	var anchor := xf * leg.rest_local

	# lead the body: at speed, feet reach out ahead of where the body is now
	var flat := velocity - up * velocity.dot(up)
	anchor += flat * lerpf(0.05, 0.17, speed01)

	# tight spaces (doorways, corners): pull the leg in against the obstacle
	var outward := anchor - global_position
	var out_flat := outward - up * outward.dot(up)
	var out_len := out_flat.length()
	if out_len > 0.05:
		var hit := _ray(global_position, global_position + out_flat * 1.05)
		if hit:
			var d := global_position.distance_to(hit.position)
			if d < out_len:
				anchor = global_position + out_flat.normalized() * (d * 0.82) \
					+ up * outward.dot(up)

	# drop the foot onto whatever is underneath it
	var from := anchor + up * 0.9 * body_size
	var to := anchor - up * 1.4 * body_size
	var ground := _ray(from, to)
	if ground:
		leg.plant_normal = ground.normal
		return ground.position + ground.normal * 0.02 * body_size

	# nothing under the ideal spot: feel around closer to the body
	var pulled := global_position + (anchor - global_position) * 0.55
	var g2 := _ray(pulled + up * 0.9 * body_size, pulled - up * 1.6 * body_size)
	if g2:
		leg.plant_normal = g2.normal
		return g2.position + g2.normal * 0.02 * body_size

	return anchor


## In mid-air the legs stop stepping and splay out, feeling for a surface.
func _flail(delta: float) -> void:
	var xf := Transform3D(_rig_basis, global_position)
	for i in range(legs.size()):
		var leg := legs[i]
		leg.step_t = 1.0
		var wob := sin(_time * 7.5 + i * 1.3) * 0.16 * body_size
		var splay := leg.rest_local * 1.16 + Vector3(0.0, 0.42 * body_size + wob, 0.0)
		var target := xf * splay
		leg.foot = leg.foot.lerp(target, 1.0 - exp(-9.0 * delta))


# ---------------------------------------------------------------------------
#  body pose
# ---------------------------------------------------------------------------

func _update_pose(delta: float) -> void:
	var up := surface_normal

	# orientation: yaw from where we are heading, pitch/roll from the feet
	var foot_up := up
	if attached and legs.size() == LEG_COUNT:
		var front := (legs[0].foot + legs[4].foot) * 0.5
		var back := (legs[3].foot + legs[7].foot) * 0.5
		var left := (legs[1].foot + legs[2].foot) * 0.5
		var right := (legs[5].foot + legs[6].foot) * 0.5
		var f := front - back
		var r := right - left
		if f.length_squared() > 0.01 and r.length_squared() > 0.01:
			var n := r.cross(f).normalized()
			if n.dot(up) > 0.1:
				foot_up = Vec.slerp_dir(up, n, 0.55)

	var fwd := facing - foot_up * facing.dot(foot_up)
	if fwd.length_squared() < 0.001:
		fwd = -_rig_basis.z
	fwd = fwd.normalized()
	var side := fwd.cross(foot_up)
	if side.length_squared() < 1e-6:
		side = _rig_basis.x
	side = side.normalized()
	var want_basis := Basis(side, foot_up, -fwd).orthonormalized()

	# lean into acceleration and bank into turns
	var accel_v := (velocity - _prev_velocity) / maxf(delta, 0.0001)
	var local_a := want_basis.inverse() * accel_v
	var pitch := clampf(local_a.z * 0.010, -0.16, 0.16)
	var roll := clampf(-local_a.x * 0.012, -0.20, 0.20)
	want_basis = want_basis * Basis(Vector3.RIGHT, pitch) * Basis(Vector3.FORWARD, roll)

	var rate := 1.0 - exp(-11.0 * delta)
	_rig_basis = _rig_basis.slerp(want_basis, rate).orthonormalized()

	# body height: ride above the average of the planted feet
	var avg := Vector3.ZERO
	for leg in legs:
		avg += leg.foot
	avg /= float(legs.size())
	var target_pos := avg + foot_up * ride_height * body_size
	if not attached:
		target_pos = global_position

	# spring sway so the body keeps moving after the legs stop
	var spring := (target_pos - (global_position + _sway)) * 90.0
	_sway_vel = (_sway_vel + spring * delta) * exp(-9.0 * delta)
	_sway += _sway_vel * delta
	_sway = _sway.limit_length(0.32 * body_size)

	# gait bob
	var bob := sin(_time * lerpf(5.0, 15.0, last_speed01)) * 0.018 * last_speed01 * body_size

	rig.global_transform = Transform3D(_rig_basis.scaled(Vector3.ONE),
		global_position + _sway + foot_up * bob)

	# abdomen lags behind, driven by the body's own acceleration
	var accel_local := _rig_basis.inverse() * ((velocity - _prev_velocity) / maxf(delta, 0.0001))
	var abd_target := -accel_local * 0.006
	abd_target.y = clampf(abd_target.y, -0.06, 0.06)
	abd_target = abd_target.limit_length(0.14) * body_size
	var abd_spring := (abd_target - _abd_offset) * 120.0
	_abd_vel = (_abd_vel + abd_spring * delta) * exp(-11.0 * delta)
	_abd_offset += _abd_vel * delta
	abdomen_pivot.position = Vector3(0.0, 0.05, 0.42) * body_size + _abd_offset
	# slow breathing
	var breath := 1.0 + sin(_time * 1.35) * 0.018
	abdomen.scale = Vector3(0.58, 0.52, 0.94) * body_size * breath

	# waist, re-spanned between carapace and abdomen wherever the spring put them
	if pedicel and abdomen_pivot:
		var back := rig.global_transform * (Vector3(0.0, 0.0, 0.18) * body_size)
		var front := abdomen_pivot.global_transform * (Vector3(0.0, 0.02, -0.06) * body_size)
		SpiderLeg._place(pedicel, back, front)

	# solve every leg against the new body transform
	var hip_xf := rig.global_transform
	for leg in legs:
		var hip := hip_xf * leg.hip_local
		var outward := (hip - hip_xf.origin)
		outward = outward - foot_up * outward.dot(foot_up)
		if outward.length_squared() < 0.0001:
			outward = hip_xf.basis.x * leg.side
		leg.solve(hip, foot_up, outward.normalized())


func _update_mouthparts(delta: float) -> void:
	if attack_queued:
		_attack_timer = 0.34
		attack_queued = false
	_attack_timer = maxf(_attack_timer - delta, 0.0)

	var snap := sin(clampf(_attack_timer / 0.34, 0.0, 1.0) * PI) * 0.85
	var idle := sin(_time * 2.1) * 0.05
	if fang_l:
		fang_l.rotation = Vector3(snap * 0.9 + idle, 0.0, snap * 0.3)
	if fang_r:
		fang_r.rotation = Vector3(snap * 0.9 + idle, 0.0, -snap * 0.3)
	# pedipalps feel around constantly
	if palp_l:
		palp_l.rotation_degrees = Vector3(-62.0 + sin(_time * 3.1) * 9.0, 0.0,
			22.0 + cos(_time * 2.3) * 7.0)
	if palp_r:
		palp_r.rotation_degrees = Vector3(-62.0 + sin(_time * 3.1 + 1.7) * 9.0, 0.0,
			-22.0 - cos(_time * 2.6) * 7.0)


# ---------------------------------------------------------------------------
#  public helpers
# ---------------------------------------------------------------------------

func teleport(to: Vector3) -> void:
	global_position = to
	surface_normal = Vector3.UP
	velocity = Vector3.ZERO
	_sway = Vector3.ZERO
	_sway_vel = Vector3.ZERO
	for leg in legs:
		leg.foot = to + leg.rest_local
		leg.step_t = 1.0


func body_forward() -> Vector3:
	return -_rig_basis.z


func body_up() -> Vector3:
	return _rig_basis.y
