extends Node
## Headless physics probe. Drives the spider with scripted input and prints what
## actually happened, so wall climbing, the gait and the jump can be verified
## without a device or a GPU.
##   godot --headless --path game tools/probe.tscn

var main: Node3D
var spider: Spider
var steps_heard := 0
var failures := 0

## Tests 1-4 run in bedroom 1 upstairs: a long blank wall with no window in it
## and no furniture in the way, so the climb is measuring the climb and not the
## spider's opinion of a wardrobe.
const FLOOR1 := 3.30


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
func _drive(frames: int, input: Vector2, run := false) -> void:
	var hud = main.get("hud")
	for i in range(frames):
		hud.move_vector = input
		hud.run_held = run
		hud._pressed["run"] = run
		await get_tree().physics_frame
	hud.move_vector = Vector2.ZERO
	hud._pressed["run"] = false


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


func _run() -> void:
	print("\n--- 1. drop into the bedroom and settle ---")
	_reset(Vector3(6.5, 4.90, 5.0))
	await _drive(90, Vector2.ZERO)
	print("  ", _state())
	_check("lands on the floor", absf(spider.global_position.y - (FLOOR1 + 0.62)) < 0.22,
		"y=%.3f (want ~%.2f)" % [spider.global_position.y, FLOOR1 + 0.62])
	_check("up vector is vertical", spider.surface_normal.y > 0.97,
		"up.y=%.3f" % spider.surface_normal.y)
	_check("legs reach the ground", _worst_leg_stretch() < 0.99,
		"worst stretch=%.2f of reach" % _worst_leg_stretch())

	print("\n--- 1b. the on-screen controls exist and are on screen ---")
	var hud: TouchUI = main.get("hud")
	var vp: Vector2 = hud.get_viewport_rect().size
	_check("HUD fills the screen", hud.size.x > 100.0 and hud.size.y > 100.0,
		"hud size=%.0fx%.0f, viewport=%.0fx%.0f" % [hud.size.x, hud.size.y, vp.x, vp.y])
	var buttons: Dictionary = hud._button_positions()
	var on_screen := true
	for key in buttons:
		var pos: Vector2 = buttons[key]
		if pos.x < 0.0 or pos.y < 0.0 or pos.x > hud.size.x or pos.y > hud.size.y:
			on_screen = false
	_check("buttons are inside the screen", on_screen, str(buttons))
	var home: Vector2 = hud._stick_home()
	_check("stick is inside the screen",
		home.x > 0.0 and home.y > 0.0 and home.x < hud.size.x and home.y < hud.size.y,
		"stick at (%.0f, %.0f)" % [home.x, home.y])

	print("\n--- 2. walk forward (-Z) across the room ---")
	var before := spider.global_position
	steps_heard = 0
	await _drive(72, Vector2(0.0, 1.0))
	print("  ", _state())
	print("  velocity=", spider.velocity, "  wish=", spider._wish_dir,
		"  step_lift=%.3f" % spider._step_lift)
	print("  touching: ", _blockers())
	_check("moved forward", before.z - spider.global_position.z > 1.5,
		"travelled %.2f m in -Z" % (before.z - spider.global_position.z))
	_check("legs are stepping", steps_heard >= 8, "%d footfalls in 1.5 s" % steps_heard)
	_check("still on the floor", spider.attached and spider.surface_normal.y > 0.9,
		"up.y=%.3f" % spider.surface_normal.y)

	print("\n--- 3. keep walking into the far wall: it should climb ---")
	await _drive(48, Vector2(0.0, 1.0))
	print("  ", _state())
	_check("climbed off the floor", spider.global_position.y - FLOOR1 > 1.2,
		"%.2f m up the wall" % (spider.global_position.y - FLOOR1))
	_check("body rolled onto the wall", spider.surface_normal.y < 0.45,
		"up.y=%.3f (0 = flat against a wall)" % spider.surface_normal.y)
	_check("legs still reach the wall", _worst_leg_stretch() < 0.99,
		"worst stretch=%.2f" % _worst_leg_stretch())

	print("\n--- 4. keep going: over the top onto the ceiling ---")
	await _drive(45, Vector2(0.0, 1.0))
	print("  ", _state())
	_check("reached ceiling height", spider.global_position.y - FLOOR1 > 2.2,
		"%.2f up (the ceiling is 3.0 above this floor)" % (spider.global_position.y - FLOOR1))
	_check("hanging upside down", spider.surface_normal.y < 0.0,
		"up.y=%.3f (negative = under the ceiling)" % spider.surface_normal.y)

	print("\n--- 5. run down the upstairs corridor (clear floor) ---")
	_reset(Vector3(6.0, 3.95, 9.0))
	await _drive(70, Vector2.ZERO)
	var run_from := spider.global_position
	await _drive(60, Vector2(1.0, 0.0), true)
	var travelled := run_from.distance_to(spider.global_position)
	print("  ", _state())
	_check("running covers ground", travelled > 3.5,
		"%.2f m in 1 s (walking would be ~%.1f)" % [travelled, spider.walk_speed * 0.75])

	print("\n--- 6. jump ---")
	_reset(Vector3(15.0, 1.0, 10.0))
	await _drive(60, Vector2.ZERO)
	var ground_y := spider.global_position.y
	main.get("hud").jump_pressed = true
	await _drive(14, Vector2.ZERO)
	var peak := spider.global_position.y
	_check("jump leaves the ground", peak - ground_y > 0.5,
		"rose %.2f m" % (peak - ground_y))
	await _drive(120, Vector2.ZERO)
	_check("lands again", spider.attached and absf(spider.global_position.y - ground_y) < 0.4,
		"y=%.2f, attached=%s" % [spider.global_position.y, spider.attached])

	print("\n--- 7. climb the stairs in the hall ---")
	_reset(Vector3(11.5, 0.8, 13.6))
	await _drive(60, Vector2.ZERO)
	var stair_start := spider.global_position.y
	await _drive(200, Vector2(0.0, 1.0))
	print("  ", _state())
	_check("gained a storey on the stairs", spider.global_position.y - stair_start > 1.2,
		"climbed %.2f m (a storey is 3.3)" % (spider.global_position.y - stair_start))
