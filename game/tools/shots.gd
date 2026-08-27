extends Node
## Development helper: boots the real game scene, drives the spider into the
## pose we want to look at, then renders fixed viewpoints to PNG. Run with:
##   godot --path game --rendering-driver vulkan tools/shots.tscn

const OUT_DIR := "user://shots"

var main: Node3D
var spider: Spider
var cam: Camera3D

## name, camera eye, look-at target, where to put the spider,
## and how many frames to walk it forward first (to get a real moving pose)
const SHOTS := [
	["01_living", Vector3(8.4, 1.80, 7.2), Vector3(3.2, 0.6, 2.0), Vector3(5.8, 0.9, 5.2), 26],
	["02_spider", Vector3(2.6, 1.00, 6.6), Vector3(6.0, 0.70, 3.8), Vector3(6.2, 0.9, 5.0), 34],
	["03_kitchen", Vector3(10.9, 1.9, 5.4), Vector3(17.2, 0.8, 1.8), Vector3(15.6, 0.9, 4.4), 24],
	["04_stairs", Vector3(18.4, 2.4, 8.0), Vector3(11.6, 1.6, 12.0), Vector3(11.5, 0.9, 13.4), 90],
	["05_wall", Vector3(9.4, 5.4, 4.4), Vector3(6.5, 5.4, 0.9), Vector3(6.5, 4.9, 3.4), 120],
	["06_bedroom", Vector3(9.0, 4.9, 5.8), Vector3(2.8, 3.9, 1.6), Vector3(6.0, 4.1, 3.6), 26],
	["07_attic", Vector3(16.8, 7.5, 10.4), Vector3(6.5, 7.3, 4.2), Vector3(10.0, 7.4, 6.4), 26],
	["08_hall", Vector3(19.2, 1.9, 12.6), Vector3(11.0, 1.2, 8.0), Vector3(15.6, 0.9, 10.6), 26],
]


func _ready() -> void:
	DirAccess.make_dir_recursive_absolute(OUT_DIR)
	main = load("res://scenes/main.tscn").instantiate()
	add_child(main)
	await get_tree().process_frame
	spider = main.get("spider")

	cam = Camera3D.new()
	cam.fov = 70.0
	cam.near = 0.05
	cam.far = 140.0
	add_child(cam)
	await _run()


func _run() -> void:
	# let the volumetric fog and the screen-space effects converge
	for i in range(24):
		await RenderingServer.frame_post_draw

	var hud = main.get("hud")
	var rig = main.get("rig")

	# `-- 01,02` renders just those shots, for iterating on lighting
	var only := PackedStringArray()
	var user_args := OS.get_cmdline_user_args()
	if user_args.size() > 0:
		only = String(user_args[0]).split(",")

	for shot in SHOTS:
		var shot_name: String = shot[0]
		if only.size() > 0 and not only.has(shot_name.substr(0, 2)):
			continue
		var eye: Vector3 = shot[1]
		var target: Vector3 = shot[2]
		var spider_at: Vector3 = shot[3]
		var walk_frames: int = shot[4]

		spider.teleport(spider_at)
		spider.facing = Vector3.FORWARD
		rig._fwd = Vector3.FORWARD
		rig._up = Vector3.UP
		rig.pitch = -0.20
		for i in range(24):
			await get_tree().physics_frame
		# walk it forward so the legs are caught mid-stride rather than at rest
		for i in range(walk_frames):
			hud.move_vector = Vector2(0.0, 1.0)
			await get_tree().physics_frame
		hud.move_vector = Vector2.ZERO

		cam.global_transform = Transform3D(Basis(), eye).looking_at(target, Vector3.UP)
		cam.current = true
		for i in range(8):
			await RenderingServer.frame_post_draw

		var img := get_viewport().get_texture().get_image()
		img.save_png("%s/%s.png" % [OUT_DIR, shot_name])
		print("shot: ", shot_name, "  spider at ", spider.global_position,
			" up ", spider.surface_normal)

	print("SHOTS_DONE ", ProjectSettings.globalize_path(OUT_DIR))
	get_tree().quit()
