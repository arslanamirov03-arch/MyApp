class_name Props
extends Node3D
## Furnishes the palace with the CC0 Poly Haven models and lights it for a late
## evening: cold moonlight through the tall windows, chandeliers hung from every
## ceiling anchor the house reports, and a fire that will not sit still.

const MODEL_ROOT := "res://assets/models/"

var house: House
## Lights that would like a shadow, most important first. How many actually get
## one is a quality setting: each omni shadow is a cubemap render of the whole
## palace, which is the most expensive thing in the scene.
var shadow_candidates: Array[OmniLight3D] = []
var _flickers: Array[Dictionary] = []
var _time := 0.0

## slug, position, yaw (degrees), kind.
##   "static" — trimesh collision, climbable
##   "rigid"  — a physics prop that can be knocked over
##   "deco"   — no collision (wall-hung things)
const LAYOUT := [
	# ================= BALLROOM  (x 2..24, z 20..38) =================
	{"m": "chinese_sofa", "p": Vector3(11.0, 0, 21.6), "r": 0, "k": "static"},
	{"m": "Sofa_01", "p": Vector3(16.5, 0, 21.6), "r": 0, "k": "static"},
	{"m": "ArmChair_01", "p": Vector3(10.0, 0, 25.5), "r": 120, "k": "static"},
	{"m": "ArmChair_01", "p": Vector3(17.0, 0, 25.5), "r": -120, "k": "static"},
	{"m": "gothic_coffee_table", "p": Vector3(13.5, 0, 24.6), "r": 0, "k": "static"},
	{"m": "gothic_statue", "p": Vector3(9.0, 0, 36.4), "r": 160, "k": "static"},
	{"m": "gothic_statue", "p": Vector3(17.5, 0, 36.4), "r": -160, "k": "static"},
	{"m": "chinese_screen_panels", "p": Vector3(13.0, 0, 34.5), "r": 0, "k": "static"},
	{"m": "brass_candleholders", "p": Vector3(13.5, 0.55, 24.6), "r": 0, "k": "rigid", "mass": 1.2},
	{"m": "throw_pillows_01", "p": Vector3(16.0, 0.5, 21.6), "r": 15, "k": "deco"},

	# ================= GRAND HALL  (x 24..38, z 20..38) =================
	{"m": "marble_bust_01", "p": Vector3(25.6, 0, 22.0), "r": 45, "k": "static"},
	{"m": "marble_bust_01", "p": Vector3(36.4, 0, 22.0), "r": -45, "k": "static"},
	{"m": "potted_plant_01", "p": Vector3(25.6, 0, 35.5), "r": 0, "k": "static"},
	{"m": "potted_plant_01", "p": Vector3(36.4, 0, 35.5), "r": 0, "k": "static"},
	{"m": "ornate_mirror_01", "p": Vector3(24.4, 3.0, 30.0), "r": -90, "k": "deco"},

	# ================= THRONE ROOM  (x 38..58, z 20..38) =================
	{"m": "GothicCabinet_01", "p": Vector3(39.8, 0, 23.0), "r": 90, "k": "static"},
	{"m": "GothicCommode_01", "p": Vector3(56.2, 0, 23.0), "r": -90, "k": "static"},
	{"m": "horse_statue_01", "p": Vector3(41.5, 0, 35.0), "r": -30, "k": "static"},
	{"m": "brass_vase_02", "p": Vector3(45.5, 1.05, 29.0), "r": 0, "k": "rigid", "mass": 3.0},
	{"m": "brass_vase_02", "p": Vector3(50.5, 1.05, 29.0), "r": 0, "k": "rigid", "mass": 3.0},
	{"m": "fancy_picture_frame_02", "p": Vector3(57.5, 4.0, 29.0), "r": -90, "k": "deco"},
	{"m": "chinese_console_table", "p": Vector3(48.0, 0, 21.5), "r": 0, "k": "static"},

	# ================= GALLERY  (x 2..58, z 14..20) =================
	{"m": "fancy_picture_frame_01", "p": Vector3(10.0, 3.6, 19.6), "r": 180, "k": "deco"},
	{"m": "fancy_picture_frame_02", "p": Vector3(24.0, 3.6, 19.6), "r": 180, "k": "deco"},
	{"m": "fancy_picture_frame_01", "p": Vector3(38.0, 3.6, 19.6), "r": 180, "k": "deco"},
	{"m": "fancy_picture_frame_02", "p": Vector3(52.0, 3.6, 19.6), "r": 180, "k": "deco"},
	{"m": "vintage_grandfather_clock_01", "p": Vector3(3.4, 0, 17.0), "r": 90, "k": "static"},
	{"m": "potted_plant_01", "p": Vector3(56.6, 0, 17.0), "r": 0, "k": "static"},
	{"m": "wooden_ladder", "p": Vector3(33.0, 0, 14.8), "r": 6, "k": "static"},

	# ================= LIBRARY  (x 2..22, z 2..14) =================
	{"m": "WoodenTable_01", "p": Vector3(12.0, 0, 8.0), "r": 0, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(12.0, 0, 6.2), "r": 180, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(12.0, 0, 9.8), "r": 0, "k": "static"},
	{"m": "book_encyclopedia_set_01", "p": Vector3(12.0, 0.78, 8.0), "r": 20, "k": "deco"},
	{"m": "vintage_oil_lamp", "p": Vector3(12.9, 0.78, 8.6), "r": 0, "k": "deco"},
	{"m": "wooden_bookshelf_worn", "p": Vector3(20.6, 0, 5.5), "r": -90, "k": "static"},
	{"m": "wooden_bookshelf_worn", "p": Vector3(20.6, 0, 11.0), "r": -90, "k": "static"},
	{"m": "ArmChair_01", "p": Vector3(17.0, 0, 8.0), "r": -90, "k": "static"},

	# ================= DINING HALL  (x 24..40, z 2..14) =================
	{"m": "WoodenChair_01", "p": Vector3(29.6, 0, 4.4), "r": 90, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(29.6, 0, 8.0), "r": 90, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(29.6, 0, 11.6), "r": 90, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(34.4, 0, 4.4), "r": -90, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(34.4, 0, 8.0), "r": -90, "k": "static"},
	{"m": "WoodenChair_01", "p": Vector3(34.4, 0, 11.6), "r": -90, "k": "static"},
	{"m": "brass_candleholders", "p": Vector3(32.0, 1.18, 6.0), "r": 0, "k": "rigid", "mass": 1.2},
	{"m": "brass_goblets", "p": Vector3(32.0, 1.18, 8.0), "r": 30, "k": "rigid", "mass": 0.5},
	{"m": "wine_bottles_01", "p": Vector3(32.0, 1.18, 10.0), "r": 0, "k": "rigid", "mass": 1.0},
	{"m": "ceramic_vase_01", "p": Vector3(25.5, 0, 12.6), "r": 0, "k": "rigid", "mass": 1.2},

	# ================= KITCHEN  (x 42..58, z 2..14) =================
	{"m": "electric_stove", "p": Vector3(44.0, 0, 3.2), "r": 0, "k": "static"},
	{"m": "WoodenTable_01", "p": Vector3(49.0, 0, 8.5), "r": 90, "k": "static"},
	{"m": "wicker_basket_01", "p": Vector3(53.5, 0, 12.4), "r": 25, "k": "static"},
	{"m": "brass_pot_01", "p": Vector3(47.5, 1.35, 2.9), "r": 0, "k": "rigid", "mass": 1.8},
	{"m": "vintage_electric_kettle", "p": Vector3(52.0, 1.35, 2.9), "r": -20, "k": "rigid", "mass": 1.2},
	{"m": "wooden_bowl_01", "p": Vector3(49.0, 0.78, 8.5), "r": 0, "k": "rigid", "mass": 0.8},
	{"m": "food_apple_01", "p": Vector3(49.3, 0.86, 8.8), "r": 0, "k": "rigid", "mass": 0.2},
	{"m": "plastic_crate_01", "p": Vector3(55.0, 0, 5.0), "r": -15, "k": "rigid", "mass": 2.5},

	# ================= STATE BEDROOM  (first floor) =================
	{"m": "GothicBed_01", "p": Vector3(8.0, 7.5, 26.0), "r": 0, "k": "static"},
	{"m": "ClassicNightstand_01", "p": Vector3(11.2, 7.5, 24.2), "r": 0, "k": "static"},
	{"m": "desk_lamp_arm_01", "p": Vector3(11.2, 8.12, 24.2), "r": -30, "k": "deco"},
	{"m": "alarm_clock_01", "p": Vector3(10.8, 8.12, 24.6), "r": 20, "k": "rigid", "mass": 0.4},
	{"m": "GothicCommode_01", "p": Vector3(21.0, 7.5, 30.0), "r": -90, "k": "static"},
	{"m": "vintage_cabinet_01", "p": Vector3(4.0, 7.5, 33.0), "r": 90, "k": "static"},
	{"m": "ornate_mirror_01", "p": Vector3(2.9, 9.6, 28.0), "r": 90, "k": "deco"},

	# ================= MUSIC ROOM  (first floor) =================
	{"m": "chinese_sofa", "p": Vector3(45.0, 7.5, 24.0), "r": 0, "k": "static"},
	{"m": "gothic_coffee_table", "p": Vector3(45.0, 7.5, 27.0), "r": 0, "k": "static"},
	{"m": "chinese_console_table", "p": Vector3(52.0, 7.5, 36.0), "r": 180, "k": "static"},
	{"m": "vintage_radio_transceiver", "p": Vector3(52.0, 8.35, 36.0), "r": -30, "k": "deco"},
	{"m": "boombox", "p": Vector3(48.0, 7.5, 31.0), "r": 15, "k": "rigid", "mass": 3.0},
	{"m": "marble_bust_01", "p": Vector3(56.0, 7.5, 24.0), "r": -40, "k": "static"},

	# ================= UPPER GALLERY / STUDY / GUEST =================
	{"m": "hanging_picture_frame_01", "p": Vector3(12.0, 10.6, 14.4), "r": 0, "k": "deco"},
	{"m": "hanging_picture_frame_01", "p": Vector3(44.0, 10.6, 14.4), "r": 0, "k": "deco"},
	{"m": "potted_plant_01", "p": Vector3(3.5, 7.5, 18.0), "r": 0, "k": "static"},
	{"m": "potted_plant_01", "p": Vector3(56.5, 7.5, 18.0), "r": 0, "k": "static"},
	{"m": "WoodenTable_01", "p": Vector3(8.0, 7.5, 7.0), "r": 0, "k": "static"},
	{"m": "wooden_bookshelf_worn", "p": Vector3(20.6, 7.5, 7.0), "r": -90, "k": "static"},
	{"m": "vintage_oil_lamp", "p": Vector3(8.0, 8.28, 7.0), "r": 0, "k": "deco"},
	{"m": "old_bed_frame", "p": Vector3(29.0, 7.5, 5.0), "r": 0, "k": "static"},
	{"m": "old_bed_frame", "p": Vector3(29.0, 7.5, 11.0), "r": 0, "k": "static"},
	{"m": "vintage_suitcase", "p": Vector3(38.0, 7.5, 8.0), "r": 25, "k": "rigid", "mass": 4.0},
	{"m": "wooden_crate_01", "p": Vector3(50.0, 7.5, 4.5), "r": 12, "k": "rigid", "mass": 9.0},
	{"m": "cardboard_box_01", "p": Vector3(52.0, 7.5, 6.5), "r": 30, "k": "rigid", "mass": 2.0},

	# ================= ROOF AND TOWER =================
	{"m": "wooden_crate_01", "p": Vector3(12.0, 14.06, 8.0), "r": 20, "k": "rigid", "mass": 9.0},
	{"m": "old_military_crate", "p": Vector3(46.0, 14.06, 30.0), "r": -10, "k": "rigid", "mass": 11.0},
	{"m": "metal_toolbox", "p": Vector3(14.0, 14.06, 30.0), "r": 45, "k": "rigid", "mass": 6.0},
	{"m": "wooden_barrels_01", "p": Vector3(48.0, 14.06, 8.0), "r": 0, "k": "static"},
]

## Garden props, placed on the lawn at y = 0.
const GARDEN_LAYOUT := [
	{"m": "painted_wooden_bench", "p": Vector3(21.0, 0, -14.0), "r": 90, "k": "static"},
	{"m": "painted_wooden_bench", "p": Vector3(39.0, 0, -14.0), "r": -90, "k": "static"},
	{"m": "outdoor_table_chair_set_01", "p": Vector3(14.0, 0, -36.0), "r": 20, "k": "static"},
	{"m": "wooden_picnic_table", "p": Vector3(46.0, 0, -36.0), "r": -15, "k": "static"},
	{"m": "stone_fire_pit", "p": Vector3(46.0, 0, -40.0), "r": 0, "k": "static"},
	{"m": "garden_gnome", "p": Vector3(24.5, 0, -10.0), "r": 30, "k": "static"},
	{"m": "concrete_cat_statue", "p": Vector3(35.5, 0, -10.0), "r": -30, "k": "static"},
	{"m": "planter_box_01", "p": Vector3(24.0, 0, -20.0), "r": 0, "k": "static"},
	{"m": "planter_box_01", "p": Vector3(36.0, 0, -20.0), "r": 0, "k": "static"},
	{"m": "planter_pot_clay", "p": Vector3(24.0, 0, -26.0), "r": 0, "k": "static"},
	{"m": "planter_pot_clay", "p": Vector3(36.0, 0, -26.0), "r": 0, "k": "static"},
	{"m": "boulder_01", "p": Vector3(10.0, 0, -24.0), "r": 40, "k": "static"},
	{"m": "rock_07", "p": Vector3(52.0, 0, -24.0), "r": -60, "k": "static"},
	{"m": "shrub_01", "p": Vector3(8.0, 0, -12.0), "r": 0, "k": "static"},
	{"m": "shrub_02", "p": Vector3(52.0, 0, -12.0), "r": 45, "k": "static"},
	{"m": "shrub_03", "p": Vector3(8.0, 0, -40.0), "r": 90, "k": "static"},
	{"m": "shrub_01", "p": Vector3(52.0, 0, -40.0), "r": -45, "k": "static"},
	{"m": "fern_02", "p": Vector3(20.0, 0, -33.0), "r": 0, "k": "deco"},
	{"m": "fern_02", "p": Vector3(40.0, 0, -33.0), "r": 60, "k": "deco"},
	{"m": "grass_medium_01", "p": Vector3(18.0, 0, -8.0), "r": 0, "k": "deco"},
	{"m": "grass_medium_01", "p": Vector3(42.0, 0, -8.0), "r": 30, "k": "deco"},
	{"m": "calathea_orbifolia_01", "p": Vector3(26.0, 0, -6.0), "r": 0, "k": "deco"},
	{"m": "calathea_orbifolia_01", "p": Vector3(34.0, 0, -6.0), "r": 0, "k": "deco"},
	{"m": "flower_gazania", "p": Vector3(22.5, 0, -17.0), "r": 0, "k": "deco"},
	{"m": "flower_ursinia", "p": Vector3(37.5, 0, -17.0), "r": 0, "k": "deco"},
	{"m": "dandelion_01", "p": Vector3(16.0, 0, -28.0), "r": 0, "k": "deco"},
	{"m": "street_lamp_01", "p": Vector3(19.0, 0, -3.0), "r": 0, "k": "static"},
	{"m": "street_lamp_01", "p": Vector3(41.0, 0, -3.0), "r": 0, "k": "static"},
	{"m": "street_lamp_02", "p": Vector3(6.0, 0, -23.0), "r": 90, "k": "static"},
	{"m": "street_lamp_02", "p": Vector3(54.0, 0, -23.0), "r": -90, "k": "static"},
	{"m": "wooden_lantern_01", "p": Vector3(30.0, 0, -44.0), "r": 0, "k": "static"},
]


func build(h: House) -> void:
	house = h
	_place_models(LAYOUT)
	_place_models(GARDEN_LAYOUT)
	_place_chandeliers()
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
## lowest point rests on pos.y. Poly Haven models do not share a common origin,
## so without this a sofa can end up metres from where the layout says it is.
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


func _place_models(table: Array) -> void:
	for entry in table:
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
				mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
				# distant furniture stops drawing; the palace is big enough that
				# this matters more than it did in a house
				mi.visibility_range_end = 65.0
				mi.visibility_range_end_margin = 10.0


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
	var box := _world_aabb(node)
	if box.size != Vector3.ZERO:
		node.global_position -= box.get_center()
		body.global_position = pos + Vector3(0.0, box.size.y * 0.5, 0.0)

	for mi in _all_meshes(node):
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		var shape := mi.mesh.create_convex_shape(true, true)
		if shape == null:
			continue
		var cs := CollisionShape3D.new()
		cs.shape = shape
		body.add_child(cs)
		cs.global_transform = mi.global_transform


# ---------------------------------------------------------------------------
#  lighting
# ---------------------------------------------------------------------------

## One chandelier per ceiling anchor the house reported, sized to the room.
func _place_chandeliers() -> void:
	var fittings := ["Chandelier_01", "Chandelier_02", "Chandelier_03",
		"lantern_chandelier_01"]
	var i := 0
	for anchor in house.ceiling_anchors:
		var pos: Vector3 = anchor.pos
		var ceiling: float = anchor.ceiling
		var size: float = anchor.size
		var drop: float = clampf(size * 1.4, 1.0, 3.0)

		var fixture := _load_model(fittings[i % fittings.size()])
		if fixture:
			add_child(fixture)
			fixture.global_position = Vector3.ZERO
			var box := _world_aabb(fixture)
			var centre := box.get_center()
			fixture.global_position += Vector3(pos.x, ceiling, pos.z) \
				- Vector3(centre.x, box.end.y, centre.z)
			fixture.scale = Vector3.ONE * clampf(size, 0.9, 2.0)
			for mi in _all_meshes(fixture):
				mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
				mi.visibility_range_end = 80.0

		var bulb_y := ceiling - drop * 0.5
		var lamp := OmniLight3D.new()
		lamp.position = Vector3(pos.x, bulb_y, pos.z)
		lamp.light_color = Color(1.0, 0.87, 0.72)
		lamp.light_energy = lerpf(2.2, 4.2, clampf((size - 0.9) / 1.3, 0.0, 1.0))
		lamp.omni_range = lerpf(14.0, 26.0, clampf((size - 0.9) / 1.3, 0.0, 1.0))
		lamp.omni_attenuation = 1.15
		lamp.shadow_enabled = false
		lamp.shadow_bias = 0.03
		lamp.shadow_normal_bias = 0.7
		lamp.shadow_blur = 0.5
		lamp.distance_fade_enabled = true
		lamp.distance_fade_begin = 48.0
		lamp.distance_fade_length = 14.0
		add_child(lamp)

		var bulb := MeshInstance3D.new()
		var sm := SphereMesh.new()
		sm.radius = 0.12
		sm.height = 0.24
		sm.radial_segments = 10
		sm.rings = 6
		bulb.mesh = sm
		bulb.material_override = MaterialLib.emissive(Color(1.0, 0.9, 0.76), 3.0)
		bulb.position = Vector3(pos.x, bulb_y, pos.z)
		bulb.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		add_child(bulb)

		if i % 4 == 3:
			_flickers.append({"light": lamp, "base": lamp.light_energy,
				"amt": 0.35, "seed": randf() * 20.0})
		i += 1


func _fireplace() -> void:
	var fire := OmniLight3D.new()
	fire.position = Vector3(3.9, 1.4, 29.0)
	fire.light_color = Color(1.0, 0.63, 0.36)
	fire.light_energy = 3.0
	fire.omni_range = 12.0
	fire.shadow_enabled = false
	fire.shadow_bias = 0.025
	fire.shadow_normal_bias = 0.7
	fire.shadow_blur = 0.5
	add_child(fire)
	_flickers.append({"light": fire, "base": 3.0, "amt": 0.75, "seed": 3.7})

	for i in range(9):
		var e := MeshInstance3D.new()
		var sm := SphereMesh.new()
		sm.radius = randf_range(0.05, 0.13)
		sm.height = sm.radius * 2.0
		sm.radial_segments = 8
		sm.rings = 5
		e.mesh = sm
		e.material_override = MaterialLib.emissive(
			Color(1.0, randf_range(0.30, 0.55), 0.10), randf_range(3.0, 8.0))
		e.position = Vector3(3.5 + randf_range(-0.2, 0.2), 0.3 + randf_range(0.0, 0.3),
			29.0 + randf_range(-1.2, 1.2))
		e.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		add_child(e)


func _process(delta: float) -> void:
	_time += delta
	for f in _flickers:
		var t: float = _time * 7.0 + float(f.seed)
		var n: float = sin(t) * 0.5 + sin(t * 2.37 + 1.1) * 0.3 + sin(t * 5.11 + 2.7) * 0.2
		var light: OmniLight3D = f.light
		light.light_energy = float(f.base) * (1.0 - float(f.amt) * (0.5 + 0.5 * n) * 0.5)
