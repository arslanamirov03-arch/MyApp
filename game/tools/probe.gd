extends Node
## Headless physics probe. Drives the spider with scripted input and prints what
## actually happened, so wall climbing, the gait and the jump can be verified
## without a device or a GPU.
##   godot --headless --path game tools/probe.tscn

var main: Node3D
var spider: Spider
var steps_heard := 0
var failures := 0

## Tests 1-4 run in the ballroom: a long blank stretch of the z = 20 wall with
## no opening and no furniture in front of it, so the climb measures the climb.
const BALLROOM_X := 17.0
const F1 := 7.5      # first floor
const ROOF := 14.0   # roof deck


func _ready() -> void:
	main = load("res://scenes/main.tscn").instantiate()
	add_child(main)
	await get_tree().process_frame
	spider = main.get("spider")
	spider.footstep.connect(func(_p: Vector3, _s: float) -> void: steps_heard += 1)
	await _run()
	print("\n=== %s ===" % ("PROBE FAILED: %d check(s)" % failures if failures else "PROBE OK"))
	get_tree().quit(1 if failures else 0)


## Drive through the real input path — HUD -> game script -> spider — rather
## than poking the spider directly, so the probe exercises what the player does.
## The camera starts looking down -Z and only turns when the player drags, so
## (0, 1) is reliably "walk towards -Z".
func _drive(frames: int, input: Vector2, mode := 0) -> void:
	var hud = main.get("hud")
	for i in range(frames):
		hud.move_vector = input
		hud._pressed["run"] = mode >= 2
		hud._pressed["fast"] = mode == 1
		await get_tree().physics_frame
	hud.move_vector = Vector2.ZERO
	hud._pressed["run"] = false
	hud._pressed["fast"] = false


## Put the spider AND the camera back to a known state, so each numbered test
## starts from the same frame regardless of where the previous one ended.
func _reset(at: Vector3) -> void:
	spider.teleport(at)
	spider.facing = Vector3.FORWARD
	var cam_rig = main.get("rig")
	cam_rig._fwd = Vector3.FORWARD
	cam_rig._up = Vector3.UP
	cam_rig.pitch = -0.20


func _check(label: String, ok: bool, detail: String) -> void:
	if not ok:
		failures += 1
	print("  [%s] %-28s %s" % ["ok" if ok else "!!", label, detail])


func _state() -> String:
	return "pos=(%.2f, %.2f, %.2f) up=(%.2f, %.2f, %.2f) attached=%s" % [
		spider.global_position.x, spider.global_position.y, spider.global_position.z,
		spider.surface_normal.x, spider.surface_normal.y, spider.surface_normal.z,
		spider.attached]


## Largest distance from a hip to its foot, as a fraction of the leg's reach.
## Anything at or above 1.0 means the IK is over-extended and the leg will look
## snapped straight.
func _worst_leg_stretch() -> float:
	var worst := 0.0
	var xf := spider.rig.global_transform
	for leg in spider.legs:
		var hip: Vector3 = xf * leg.hip_local
		worst = maxf(worst, hip.distance_to(leg.foot) / leg.reach())
	return worst


## What is physically overlapping the spider's body right now.
func _blockers() -> String:
	var space := spider.get_world_3d().direct_space_state
	var q := PhysicsShapeQueryParameters3D.new()
	var sh := SphereShape3D.new()
	sh.radius = 0.47
	q.shape = sh
	q.transform = Transform3D(Basis(), spider.global_position)
	q.collision_mask = 1
	q.exclude = [spider.get_rid()]
	var names: Array[String] = []
	for h in space.intersect_shape(q, 16):
		var c = h.collider
		names.append("%s<%s>" % [c.name, c.get_class()])
	return ", ".join(names) if not names.is_empty() else "nothing"


## Every light in the scene, wherever it lives.
func _all_lights(node: Node, out: Array[Light3D]) -> void:
	if node is Light3D:
		out.append(node as Light3D)
	for c in node.get_children():
		_all_lights(c, out)


func _run() -> void:
	var ride := spider.ride_height

	print("\n--- 1. settle, and the on-screen controls ---")
	_reset(Vector3(BALLROOM_X, 2.2, 32.0))
	await _drive(90, Vector2.ZERO)
	print("  ", _state())
	_check("lands on the floor", absf(spider.global_position.y - ride) < 0.25,
		"y=%.3f (ride height %.2f)" % [spider.global_position.y, ride])
	_check("legs reach the ground", _worst_leg_stretch() < 0.99,
		"worst stretch=%.2f" % _worst_leg_stretch())
	var hud: TouchUI = main.get("hud")
	_check("HUD fills the screen", hud.size.x > 100.0 and hud.size.y > 100.0,
		"%.0fx%.0f" % [hud.size.x, hud.size.y])
	var buttons: Dictionary = hud._button_positions()
	_check("buttons are FAST / RUN / JUMP", buttons.has("fast") and buttons.has("run")
		and buttons.has("jump") and not buttons.has("bite"), str(buttons.keys()))

	print("\n--- 2. nothing casts a shadow any more ---")
	var lights: Array[Light3D] = []
	_all_lights(main, lights)
	var casting := 0
	for l in lights:
		if l.shadow_enabled:
			casting += 1
	_check("no light casts shadows", casting == 0,
		"%d of %d lights had shadows on" % [casting, lights.size()])
	var sky_mat = main.get("env").sky.sky_material
	_check("the sky is a real panorama", sky_mat is PanoramaSkyMaterial,
		sky_mat.get_class())

	print("\n--- 3. the three gaits ---")
	var travelled: Array[float] = []
	for mode_v in [0, 1, 2]:
		var mode: int = mode_v
		_reset(Vector3(BALLROOM_X, 1.4, 34.0))
		await _drive(60, Vector2.ZERO)
		var from := spider.global_position
		await _drive(60, Vector2(0.0, 1.0), mode)
		travelled.append(from.distance_to(spider.global_position))
	print("  walk=%.2f  fast=%.2f  run=%.2f m/s" % travelled)
	_check("walk < fast < run", travelled[0] < travelled[1] - 0.8
		and travelled[1] < travelled[2] - 0.8, str(travelled))

	print("\n--- 4. wall and ceiling ---")
	_reset(Vector3(BALLROOM_X, 1.4, 30.0))
	await _drive(60, Vector2.ZERO)
	steps_heard = 0
	await _drive(150, Vector2(0.0, 1.0), 2)
	print("  ", _state())
	_check("legs are stepping", steps_heard >= 10, "%d footfalls" % steps_heard)
	_check("climbed the wall", spider.global_position.y > 2.0,
		"%.2f m up" % spider.global_position.y)
	await _drive(60, Vector2(0.0, 1.0), 2)
	_check("hangs under the ceiling", spider.surface_normal.y < 0.0
		and spider.global_position.y > 5.0,
		"y=%.2f up.y=%.2f" % [spider.global_position.y, spider.surface_normal.y])

	print("\n--- 5. the staircases go a whole storey ---")
	_reset(Vector3(56.0, 1.4, 17.0))
	await _drive(60, Vector2.ZERO)
	var s0 := spider.global_position.y
	await _drive(300, Vector2(-1.0, 0.0), 1)
	print("  ", _state())
	_check("ground floor to first floor", spider.global_position.y - s0 > F1 * 0.8,
		"climbed %.2f m of %.1f" % [spider.global_position.y - s0, F1])
	_check("landed on the upper floor", spider.attached
		and spider.global_position.y > F1 * 0.75,
		"y=%.2f" % spider.global_position.y)

	print("\n--- 6. and on up to the roof ---")
	_reset(Vector3(19.5, F1 + 0.9, 17.0))
	await _drive(60, Vector2.ZERO)
	var r0 := spider.global_position.y
	await _drive(300, Vector2(1.0, 0.0), 1)
	print("  ", _state())
	_check("first floor to roof", spider.global_position.y - r0 > (ROOF - F1) * 0.8,
		"climbed %.2f m of %.1f" % [spider.global_position.y - r0, ROOF - F1])

	print("\n--- 7. there is ground everywhere: no bottomless holes ---")
	var corners := [Vector3(100.0, 4.0, 60.0), Vector3(-50.0, 4.0, -80.0),
		Vector3(120.0, 4.0, -95.0), Vector3(-60.0, 4.0, 80.0),
		Vector3(30.0, 4.0, 60.0), Vector3(70.0, 4.0, -20.0)]
	var landed := 0
	for c_v in corners:
		var c: Vector3 = c_v
		_reset(c)
		await _drive(130, Vector2.ZERO)
		if spider.attached and spider.global_position.y > -3.0:
			landed += 1
		else:
			print("    fell through at ", c, " -> ", spider.global_position)
	_check("every far corner has ground", landed == corners.size(),
		"%d of %d" % [landed, corners.size()])

	print("\n--- 8. catches a wall in mid-air ---")
	# z = 26 is a solid stretch of the west wall. z = 20 is not: it is the
	# middle of a window, and the windows are open holes now, so the spider
	# correctly went through it instead of grabbing.
	_reset(Vector3(-1.15, 4.0, 26.0))
	spider.attached = false
	await _drive(50, Vector2.ZERO)
	print("  ", _state())
	_check("grabbed the wall instead of falling", spider.attached
		and spider.global_position.y > 2.0,
		"y=%.2f attached=%s up=(%.2f, %.2f, %.2f)" % [spider.global_position.y,
			spider.attached, spider.surface_normal.x, spider.surface_normal.y,
			spider.surface_normal.z])

	print("\n--- 9. rights itself when it ends up on its back ---")
	_reset(Vector3(13.0, 1.1, 30.0))
	await _drive(60, Vector2.ZERO)
	spider.surface_normal = Vector3.DOWN
	spider.attached = true
	await _drive(100, Vector2.ZERO)
	print("  ", _state())
	_check("flipped back upright", spider.surface_normal.y > 0.5,
		"up.y=%.2f" % spider.surface_normal.y)
	_check("still standing afterwards", spider.attached
		and absf(spider.global_position.y - ride) < 0.6,
		"y=%.2f" % spider.global_position.y)

	print("\n--- 10. the garden ---")
	_reset(Vector3(30.0, 2.5, -20.0))
	await _drive(110, Vector2.ZERO)
	var g0 := spider.global_position
	await _drive(90, Vector2(0.0, 1.0), 2)
	_check("can walk in the garden", g0.distance_to(spider.global_position) > 3.0
		and spider.attached, "%.2f m" % g0.distance_to(spider.global_position))
