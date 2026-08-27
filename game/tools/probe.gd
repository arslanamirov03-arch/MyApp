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


func _run() -> void:
	var ride := spider.ride_height

	print("\n--- 1. drop into the ballroom and settle ---")
	_reset(Vector3(BALLROOM_X, 2.2, 32.0))
	await _drive(90, Vector2.ZERO)
	print("  ", _state())
	_check("lands on the floor", absf(spider.global_position.y - ride) < 0.25,
		"y=%.3f (ride height is %.2f)" % [spider.global_position.y, ride])
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
	_check("buttons are inside the screen", on_screen, str(buttons.keys()))
	_check("the bite button is gone", not buttons.has("bite"), str(buttons.keys()))
	_check("there is a fast-walk button", buttons.has("fast"), str(buttons.keys()))

	print("\n--- 2. the three gaits are actually different speeds ---")
	var travelled: Array[float] = []
	for mode_v in [0, 1, 2]:
		var mode: int = mode_v
		_reset(Vector3(BALLROOM_X, 1.4, 34.0))
		await _drive(60, Vector2.ZERO)
		var from := spider.global_position
		await _drive(60, Vector2(0.0, 1.0), mode)
		travelled.append(from.distance_to(spider.global_position))
	print("  walk=%.2f m  fast=%.2f m  run=%.2f m (1 s each)" % travelled)
	_check("slow walk is slow", travelled[0] < 2.2, "%.2f m/s" % travelled[0])
	_check("fast walk beats walking", travelled[1] > travelled[0] + 0.8,
		"%.2f vs %.2f" % [travelled[1], travelled[0]])
	_check("running beats fast walking", travelled[2] > travelled[1] + 0.8,
		"%.2f vs %.2f" % [travelled[2], travelled[1]])

	print("\n--- 3. walk into the ballroom wall: it should climb ---")
	_reset(Vector3(BALLROOM_X, 1.4, 30.0))
	await _drive(60, Vector2.ZERO)
	steps_heard = 0
	await _drive(150, Vector2(0.0, 1.0), 2)
	print("  ", _state())
	_check("legs are stepping", steps_heard >= 10, "%d footfalls" % steps_heard)
	_check("climbed off the floor", spider.global_position.y > 2.0,
		"%.2f m up the wall" % spider.global_position.y)
	_check("body rolled onto the wall", spider.surface_normal.y < 0.45,
		"up.y=%.3f" % spider.surface_normal.y)
	_check("legs still reach", _worst_leg_stretch() < 0.99,
		"worst stretch=%.2f" % _worst_leg_stretch())

	print("\n--- 4. keep going: onto the 7 m ceiling ---")
	await _drive(60, Vector2(0.0, 1.0), 2)
	print("  ", _state())
	_check("reached ceiling height", spider.global_position.y > 5.0,
		"y=%.2f (the ceiling is at 7.0)" % spider.global_position.y)
	_check("hanging upside down", spider.surface_normal.y < 0.0,
		"up.y=%.3f" % spider.surface_normal.y)

	print("\n--- 5. jump ---")
	_reset(Vector3(48.0, 1.2, 24.0))
	await _drive(60, Vector2.ZERO)
	var ground_y := spider.global_position.y
	hud.jump_pressed = true
	await _drive(14, Vector2.ZERO)
	_check("jump leaves the ground", spider.global_position.y - ground_y > 0.5,
		"rose %.2f m" % (spider.global_position.y - ground_y))
	await _drive(150, Vector2.ZERO)
	_check("lands again", spider.attached
		and absf(spider.global_position.y - ground_y) < 0.5,
		"y=%.2f, attached=%s" % [spider.global_position.y, spider.attached])

	print("\n--- 6. the grand staircase ---")
	_reset(Vector3(31.0, 1.4, 38.0))
	await _drive(60, Vector2.ZERO)
	var stair_start := spider.global_position.y
	await _drive(320, Vector2(0.0, 1.0), 2)
	print("  ", _state())
	_check("climbed the grand stair", spider.global_position.y - stair_start > 1.5,
		"climbed %.2f m (a storey is 7.5)" % (spider.global_position.y - stair_start))

	print("\n--- 7. the garden exists and is solid ---")
	_reset(Vector3(30.0, 2.5, -20.0))
	await _drive(110, Vector2.ZERO)
	print("  ", _state())
	_check("stands on the lawn", spider.attached and absf(spider.global_position.y - ride) < 0.45,
		"y=%.2f" % spider.global_position.y)
	var from_g := spider.global_position
	await _drive(90, Vector2(0.0, 1.0), 2)
	_check("can walk in the garden", from_g.distance_to(spider.global_position) > 3.0,
		"%.2f m" % from_g.distance_to(spider.global_position))
	_check("legs reach garden ground", _worst_leg_stretch() < 0.99,
		"worst stretch=%.2f" % _worst_leg_stretch())

	print("\n--- 8. climb a gallery column ---")
	_reset(Vector3(6.0, 1.4, 25.0))
	await _drive(60, Vector2.ZERO)
	var col_start := spider.global_position.y
	# 100 frames is the top of the shaft; much longer and the spider has already
	# gone over the capital, along the ceiling and back down the far side
	await _drive(100, Vector2(0.0, 1.0), 1)
	print("  ", _state())
	_check("climbed the column", spider.global_position.y - col_start > 1.0,
		"rose %.2f m (the column is 7 m)" % (spider.global_position.y - col_start))

	print("\n--- 9. the roof is a real surface you can stand on ---")
	_reset(Vector3(12.0, 15.6, 30.0))
	await _drive(110, Vector2.ZERO)
	print("  ", _state())
	_check("stands on the roof", spider.attached and spider.global_position.y > 14.0
		and spider.global_position.y < 15.6,
		"y=%.2f (the roof deck is at 14.06)" % spider.global_position.y)
	var roof_from := spider.global_position
	await _drive(90, Vector2(0.0, 1.0), 2)
	_check("can walk on the roof", roof_from.distance_to(spider.global_position) > 3.0
		and spider.global_position.y > 13.5,
		"%.2f m at y=%.2f" % [roof_from.distance_to(spider.global_position),
			spider.global_position.y])
