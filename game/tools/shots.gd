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
	["01_hall", Vector3(31.0, 4.0, 38.6), Vector3(31.0, 2.0, 26.0), Vector3(31.0, 1.2, 34.0), 40],
	["02_spider", Vector3(26.5, 1.30, 29.0), Vector3(30.5, 1.0, 26.5), Vector3(30.5, 1.2, 27.5), 46],
	["03_ballroom", Vector3(22.5, 3.2, 36.5), Vector3(7.0, 1.6, 24.0), Vector3(13.0, 1.2, 30.0), 40],
	["04_throne", Vector3(40.0, 3.4, 21.5), Vector3(48.0, 2.2, 30.0), Vector3(44.5, 1.2, 26.0), 40],
	["05_garden", Vector3(30.0, 7.5, -6.0), Vector3(30.0, 1.0, -32.0), Vector3(26.0, 1.2, -14.0), 40],
	["06_fountain", Vector3(30.0, 3.2, -19.0), Vector3(30.0, 2.4, -30.0), Vector3(28.0, 1.2, -22.0), 40],
	["07_roof", Vector3(18.0, 17.5, 22.0), Vector3(31.0, 17.0, 9.0), Vector3(23.0, 15.0, 17.0), 40],
	["08_gallery", Vector3(4.5, 3.4, 17.0), Vector3(56.0, 1.6, 17.0), Vector3(13.0, 1.2, 17.0), 40],
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
