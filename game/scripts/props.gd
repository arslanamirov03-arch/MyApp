class_name Props
extends Node3D
## Furnishes the house with the CC0 Poly Haven models and lights the place for
## a late evening: cold moonlight through the windows, a handful of warm lamps,
## and a fire that will not sit still.

const MODEL_ROOT := "res://assets/models/"

var house: House
var _flickers: Array[Dictionary] = []
var _time := 0.0

## slug, position, yaw (degrees), and how it should be placed.
##   kind: "static" (trimesh collision) | "rigid" (physics prop) | "deco" (no collision)
##   y: "floor" drops the model so its lowest point sits on `pos.y`
const LAYOUT := [
	# ---------- living room ----------
	{"m": "Sofa_01", "p": Vector3(4.4, 0, 1.35), "r": 0, "k": "static"},
	{"m": "throw_pillows_01", "p": Vector3(3.6, 0.46, 1.35), "r": 20, "k": "deco"},
	{"m": "ArmChair_01", "p": Vector3(7.9, 0, 3.4), "r": -105, "k": "static"},
	{"m": "CoffeeTable_01", "p": Vector3(4.9, 0, 3.1), "r": 4, "k": "static"},
	{"m": "Television_01", "p": Vector3(5.2, 0, 7.4), "r": 180, "k": "static"},
	{"m": "wooden_bookshelf_worn", "p": Vector3(8.9, 0, 6.1), "r": -90, "k": "static"},
	{"m": "vintage_grandfather_clock_01", "p": Vector3(9.2, 0, 1.0), "r": -90, "k": "static"},
	{"m": "potted_plant_01", "p": Vector3(1.3, 0, 7.3), "r": 0, "k": "static"},
	{"m": "fancy_picture_frame_01", "p": Vector3(0.5, 1.85, 6.4), "r": 90, "k": "deco"},
	{"m": "wooden_bowl_01", "p": Vector3(4.9, 0.44, 3.1), "r": 0, "k": "rigid", "mass": 0.7},
	{"m": "food_apple_01", "p": Vector3(5.15, 0.5, 3.3), "r": 0, "k": "rigid", "mass": 0.2},

	# ---------- kitchen ----------
	{"m": "electric_stove", "p": Vector3(11.1, 0, 1.0), "r": 0, "k": "static"},
	{"m": "WoodenTable_01", "p": Vector3(15.4, 0, 3.5), "r": 0, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(14.2, 0, 3.5), "r": 90, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(16.6, 0, 3.5), "r": -90, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(15.4, 0, 4.7), "r": 180, "k": "static"},
	{"m": "wicker_basket_01", "p": Vector3(18.6, 0, 5.3), "r": 30, "k": "static"},
	{"m": "brass_pot_01", "p": Vector3(12.6, 0.97, 0.72), "r": 0, "k": "rigid", "mass": 1.6},
	{"m": "vintage_electric_kettle", "p": Vector3(13.6, 0.97, 0.72), "r": -25, "k": "rigid", "mass": 1.1},
	{"m": "wine_bottles_01", "p": Vector3(19.3, 0.97, 1.6), "r": 0, "k": "rigid", "mass": 1.0},
	{"m": "ceramic_vase_01", "p": Vector3(15.4, 0.78, 3.5), "r": 0, "k": "rigid", "mass": 0.9},
	{"m": "brass_goblets", "p": Vector3(15.9, 0.78, 3.2), "r": 40, "k": "rigid", "mass": 0.4},

	# ---------- hall ----------
	{"m": "ornate_mirror_01", "p": Vector3(19.6, 1.55, 9.2), "r": -90, "k": "deco"},
	{"m": "wooden_ladder", "p": Vector3(10.5, 0, 7.1), "r": 8, "k": "static"},
	{"m": "wall_clock", "p": Vector3(10.35, 2.15, 11.2), "r": 90, "k": "deco"},
	{"m": "vintage_suitcase", "p": Vector3(18.2, 0, 12.6), "r": 25, "k": "rigid", "mass": 4.0},
	{"m": "plastic_crate_01", "p": Vector3(17.4, 0, 7.0), "r": -15, "k": "rigid", "mass": 2.5},

	# ---------- dining ----------
	{"m": "WoodenTable_01", "p": Vector3(7.4, 0, 11.0), "r": 90, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(7.4, 0, 9.8), "r": 180, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(7.4, 0, 12.2), "r": 0, "k": "static"},
	{"m": "book_encyclopedia_set_01", "p": Vector3(9.6, 1.05, 11.0), "r": 0, "k": "deco"},

	# ---------- bedroom 1 (first floor) ----------
	{"m": "GothicBed_01", "p": Vector3(2.7, 3.3, 2.1), "r": 0, "k": "static"},
	{"m": "ClassicNightstand_01", "p": Vector3(4.7, 3.3, 0.95), "r": 0, "k": "static"},
	{"m": "desk_lamp_arm_01", "p": Vector3(4.7, 3.92, 0.95), "r": -30, "k": "deco"},
	{"m": "alarm_clock_01", "p": Vector3(4.45, 3.92, 1.2), "r": 20, "k": "rigid", "mass": 0.4},
	{"m": "vintage_cabinet_01", "p": Vector3(8.7, 3.3, 1.4), "r": -90, "k": "static"},

	# ---------- bedroom 2 ----------
	{"m": "old_bed_frame", "p": Vector3(12.8, 3.3, 2.3), "r": 0, "k": "static"},
	{"m": "vintage_radio_transceiver", "p": Vector3(18.6, 3.3, 1.3), "r": -60, "k": "static"},
	{"m": "boombox", "p": Vector3(16.2, 3.3, 6.2), "r": 15, "k": "rigid", "mass": 3.0},

	# ---------- corridor & study ----------
	{"m": "potted_plant_01", "p": Vector3(1.1, 3.3, 9.7), "r": 40, "k": "static"},
	{"m": "hanging_picture_frame_01", "p": Vector3(9.0, 5.0, 7.15), "r": 0, "k": "deco"},
	{"m": "wooden_bookshelf_worn", "p": Vector3(6.2, 3.3, 13.6), "r": 180, "k": "static"},
	{"m": "WoodenTable_01", "p": Vector3(3.6, 3.3, 12.5), "r": 0, "k": "static"},
	{"m": "vintage_oil_lamp", "p": Vector3(3.6, 4.05, 12.5), "r": 0, "k": "deco"},
	{"m": "book_encyclopedia_set_01", "p": Vector3(3.2, 4.05, 12.8), "r": 35, "k": "deco"},

	# ---------- attic ----------
	{"m": "wooden_crate_01", "p": Vector3(6.0, 6.6, 3.0), "r": 12, "k": "rigid", "mass": 9.0},
	{"m": "wooden_crate_01", "p": Vector3(6.4, 7.05, 3.2), "r": -22, "k": "rigid", "mass": 9.0},
	{"m": "cardboard_box_01", "p": Vector3(13.5, 6.6, 4.5), "r": 30, "k": "rigid", "mass": 2.0},
	{"m": "old_military_crate", "p": Vector3(15.0, 6.6, 9.0), "r": -10, "k": "rigid", "mass": 11.0},
	{"m": "metal_toolbox", "p": Vector3(8.0, 6.6, 10.5), "r": 45, "k": "rigid", "mass": 6.0},
	{"m": "wooden_barrels_01", "p": Vector3(4.0, 6.6, 6.0), "r": 0, "k": "static"},
	{"m": "vintage_suitcase", "p": Vector3(11.0, 6.6, 11.5), "r": -35, "k": "rigid", "mass": 4.0},
]

## Warm practical lights. `shadow` is expensive, so only a few get it.
## `fix` hangs a real light fitting from the ceiling at `ceil` above the bulb.
const LIGHTS := [
	{"p": Vector3(5.0, 2.55, 3.4), "e": 2.80, "r": 11.0, "c": Color(1.0, 0.87, 0.73),
		"shadow": true, "flicker": 0.0, "fix": "Chandelier_01", "ceil": 3.0},
	{"p": Vector3(15.2, 2.60, 2.8), "e": 2.30, "r": 9.5, "c": Color(1.0, 0.90, 0.79),
		"shadow": true, "flicker": 0.12, "fix": "modern_ceiling_lamp_01", "ceil": 3.0},
	{"p": Vector3(15.0, 2.55, 10.0), "e": 1.70, "r": 8.5, "c": Color(1.0, 0.85, 0.70),
		"shadow": true, "flicker": 0.35, "fix": "caged_hanging_light", "ceil": 3.0},
	{"p": Vector3(7.5, 2.55, 11.0), "e": 1.90, "r": 8.0, "c": Color(1.0, 0.88, 0.75),
		"shadow": false, "flicker": 0.0, "fix": "caged_hanging_light", "ceil": 3.0},
	{"p": Vector3(4.7, 4.02, 0.95), "e": 1.60, "r": 6.0, "c": Color(1.0, 0.84, 0.68),
		"shadow": true, "flicker": 0.0, "fix": "", "ceil": 0.0},
	{"p": Vector3(6.0, 5.85, 9.0), "e": 1.20, "r": 8.0, "c": Color(0.97, 0.86, 0.74),
		"shadow": false, "flicker": 0.55, "fix": "caged_hanging_light", "ceil": 6.3},
	{"p": Vector3(16.0, 5.85, 9.0), "e": 1.00, "r": 7.0, "c": Color(0.97, 0.86, 0.74),
		"shadow": false, "flicker": 0.0, "fix": "caged_hanging_light", "ceil": 6.3},
	{"p": Vector3(10.0, 8.3, 7.0), "e": 0.90, "r": 9.0, "c": Color(0.95, 0.84, 0.72),
		"shadow": false, "flicker": 0.8, "fix": "", "ceil": 0.0},
]


func build(h: House) -> void:
	house = h
	_place_models()
	_place_lights()
	_fireplace()


# ---------------------------------------------------------------------------

func _load_model(slug: String) -> Node3D:
	var path := "%s%s/%s.gltf" % [MODEL_ROOT, slug, slug]
	if not ResourceLoader.exists(path):
		return null
	var packed := load(path) as PackedScene
	if packed == null:
		return null
	return packed.instantiate() as Node3D


## Bounds of every mesh under `node`, in world space.
func _world_aabb(node: Node3D) -> AABB:
	var out := AABB()
	var first := true
	for mi in _all_meshes(node):
		var box: AABB = mi.global_transform * mi.mesh.get_aabb()
		if first:
			out = box
			first = false
		else:
			out = out.merge(box)
	return out


## Move `node` so the centre of its footprint lands on (pos.x, pos.z) and its
## lowest point rests on pos.y. Poly Haven models do not share a common origin —
## some are centred, some are not — so without this a sofa can end up metres
## from where the layout table says it is. That is what put a coffee table on
## top of the player spawn.
func _seat_on_floor(node: Node3D, pos: Vector3) -> void:
	var box := _world_aabb(node)
	if box.size == Vector3.ZERO:
		node.global_position = pos
		return
	var centre := box.get_center()
	node.global_position += pos - Vector3(centre.x, box.position.y, centre.z)


func _all_meshes(node: Node) -> Array[MeshInstance3D]:
	var found: Array[MeshInstance3D] = []
	if node is MeshInstance3D and (node as MeshInstance3D).mesh != null:
		found.append(node as MeshInstance3D)
	for c in node.get_children():
		found.append_array(_all_meshes(c))
	return found


func _place_models() -> void:
	for entry in LAYOUT:
		var node := _load_model(entry.m)
		if node == null:
			push_warning("missing model: %s" % entry.m)
			continue
		var kind: String = entry.get("k", "static")
		var pos: Vector3 = entry.p
		var yaw := deg_to_rad(float(entry.get("r", 0)))

		if kind == "rigid":
			_as_rigid(node, pos, yaw, float(entry.get("mass", 2.0)))
		else:
			add_child(node)
			node.global_transform = Transform3D(Basis(Vector3.UP, yaw), Vector3.ZERO)
			_seat_on_floor(node, pos)
			if kind == "static":
				for mi in _all_meshes(node):
					mi.create_trimesh_collision()
			for mi in _all_meshes(node):
				mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON


func _as_rigid(node: Node3D, pos: Vector3, yaw: float, mass: float) -> void:
	var body := RigidBody3D.new()
	body.mass = mass
	body.collision_layer = 1
	body.collision_mask = 1
	body.continuous_cd = true
	body.can_sleep = true
	add_child(body)
	body.global_transform = Transform3D(Basis(Vector3.UP, yaw), pos)

	body.add_child(node)
	node.transform = Transform3D()
	# centre the mesh on the body origin, then lift the body so the object's
	# lowest point rests on the requested height
	var box := _world_aabb(node)
	if box.size != Vector3.ZERO:
		node.global_position -= box.get_center()
		body.global_position = pos + Vector3(0.0, box.size.y * 0.5, 0.0)

	for mi in _all_meshes(node):
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
		var shape := mi.mesh.create_convex_shape(true, true)
		if shape == null:
			continue
		var cs := CollisionShape3D.new()
		cs.shape = shape
		body.add_child(cs)
		cs.global_transform = mi.global_transform


func _place_lights() -> void:
	for spec in LIGHTS:
		if float(spec.e) <= 0.0:
			continue
		var lamp := OmniLight3D.new()
		lamp.position = spec.p
		lamp.light_color = spec.c
		lamp.light_energy = spec.e
		lamp.omni_range = spec.r
		lamp.omni_attenuation = 1.15
		lamp.shadow_enabled = spec.shadow
		lamp.shadow_bias = 0.025
		lamp.shadow_normal_bias = 0.7
		lamp.shadow_blur = 0.5
		lamp.distance_fade_enabled = true
		lamp.distance_fade_begin = 26.0
		lamp.distance_fade_length = 8.0
		add_child(lamp)

		# a glowing bulb so the source is visible in the room
		var bulb := MeshInstance3D.new()
		var sm := SphereMesh.new()
		sm.radius = 0.07
		sm.height = 0.14
		bulb.mesh = sm
		bulb.material_override = MaterialLib.emissive(spec.c, 3.0)
		bulb.position = spec.p
		bulb.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		add_child(bulb)

		# hang a real fitting from the ceiling above the bulb
		var fixture_name := String(spec.get("fix", ""))
		if fixture_name != "":
			var fixture := _load_model(fixture_name)
			if fixture:
				add_child(fixture)
				fixture.global_position = Vector3.ZERO
				var box := _world_aabb(fixture)
				var centre := box.get_center()
				# hang it so its top touches the ceiling
				fixture.global_position += Vector3(spec.p.x, float(spec.ceil), spec.p.z) \
					- Vector3(centre.x, box.end.y, centre.z)
				for mi in _all_meshes(fixture):
					mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		else:
			var shade := MeshInstance3D.new()
			var cm := CylinderMesh.new()
			cm.top_radius = 0.06
			cm.bottom_radius = 0.26
			cm.height = 0.2
			shade.mesh = cm
			shade.material_override = MaterialLib.plain(Color(0.14, 0.12, 0.1), 0.6)
			shade.position = spec.p + Vector3(0.0, 0.16, 0.0)
			add_child(shade)

		if float(spec.flicker) > 0.0:
			_flickers.append({
				"light": lamp, "base": float(spec.e), "amt": float(spec.flicker),
				"seed": randf() * 20.0,
			})


func _fireplace() -> void:
	var fire := OmniLight3D.new()
	fire.position = Vector3(1.25, 0.55, 4.0)
	fire.light_color = Color(1.0, 0.63, 0.36)
	fire.light_energy = 2.6
	fire.omni_range = 7.5
	fire.shadow_enabled = true
	fire.shadow_bias = 0.025
	fire.shadow_normal_bias = 0.7
	fire.shadow_blur = 0.5
	add_child(fire)
	_flickers.append({"light": fire, "base": 2.6, "amt": 0.75, "seed": 3.7})

	# embers
	for i in range(7):
		var e := MeshInstance3D.new()
		var sm := SphereMesh.new()
		sm.radius = randf_range(0.03, 0.08)
		sm.height = sm.radius * 2.0
		e.mesh = sm
		e.material_override = MaterialLib.emissive(
			Color(1.0, randf_range(0.28, 0.55), 0.08), randf_range(3.0, 8.0))
		e.position = Vector3(1.05 + randf_range(-0.1, 0.1), 0.2 + randf_range(0.0, 0.14),
			4.0 + randf_range(-0.5, 0.5))
		e.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		add_child(e)


func _process(delta: float) -> void:
	_time += delta
	for f in _flickers:
		var t: float = _time * 7.0 + float(f.seed)
		# layered sine noise reads as an unstable filament / open flame
		var n: float = sin(t) * 0.5 + sin(t * 2.37 + 1.1) * 0.3 + sin(t * 5.11 + 2.7) * 0.2
		var light: OmniLight3D = f.light
		light.light_energy = float(f.base) * (1.0 - float(f.amt) * (0.5 + 0.5 * n) * 0.5)
