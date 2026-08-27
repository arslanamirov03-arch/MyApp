extends Node
var main: Node3D
var spider: Spider

func _ready() -> void:
	main = load("res://scenes/main.tscn").instantiate()
	add_child(main)
	await get_tree().process_frame
	spider = main.get("spider")
	spider.teleport(Vector3(6.0, 1.4, 25.0))
	spider.facing = Vector3.FORWARD
	var rig = main.get("rig")
	rig._fwd = Vector3.FORWARD; rig._up = Vector3.UP; rig.pitch = -0.20
	var hud = main.get("hud")
	for i in range(70):
		await get_tree().physics_frame
	print("frame  pos                    up                 att lift  vel                  wish")
	for i in range(220):
		hud.move_vector = Vector2(0.0, 1.0)
		hud._pressed["fast"] = true
		await get_tree().physics_frame
		if i % 10 == 0:
			print("%4d  (%6.2f,%6.2f,%6.2f)  (%5.2f,%5.2f,%5.2f) %s %5.2f (%6.2f,%6.2f,%6.2f) (%5.2f,%5.2f,%5.2f)" % [
				i, spider.global_position.x, spider.global_position.y, spider.global_position.z,
				spider.surface_normal.x, spider.surface_normal.y, spider.surface_normal.z,
				"Y" if spider.attached else "n", spider._step_lift,
				spider.velocity.x, spider.velocity.y, spider.velocity.z,
				spider._wish_dir.x, spider._wish_dir.y, spider._wish_dir.z])
	get_tree().quit()
