extends Node3D
## Boots the whole thing: environment, house, props, spider, camera and HUD.

const SPAWN := Vector3(31.0, 1.20, 34.0)   # the grand hall, at the stairs
## Ambient fill at brightness 1.0. The slider scales this.
const AMBIENT_BASE := 1.70
## Below this the spider is considered to have fallen out of the world.
const FALL_LIMIT := -25.0

var terrain: Terrain
var house: House
var garden: Garden
var props: Props
var spider: Spider
var rig: CameraRig
var hud: TouchUI
var menu: Control
var fps_label: Label
var env: Environment
var world_env: WorldEnvironment
var moon: DirectionalLight3D

var _step_players: Array[AudioStreamPlayer3D] = []
var _step_index := 0
var _creak_timer := 6.0
var _rng := RandomNumberGenerator.new()


func _ready() -> void:
	_rng.randomize()
	_build_environment()

	terrain = Terrain.new()
	terrain.name = "Terrain"
	add_child(terrain)

	house = House.new()
	house.name = "House"
	add_child(house)

	garden = Garden.new()
	garden.name = "Garden"
	add_child(garden)

	props = Props.new()
	props.name = "Props"
	add_child(props)
	props.build(house)

	spider = Spider.new()
	spider.name = "Spider"
	add_child(spider)
	spider.global_position = SPAWN
	spider.teleport(SPAWN)
	spider.footstep.connect(_on_footstep)

	rig = CameraRig.new()
	rig.name = "CameraRig"
	add_child(rig)
	rig.spider = spider

	_build_hud()
	_build_audio()
	_apply_quality(Settings.quality)
	Settings.quality_changed.connect(_apply_quality)
	Settings.brightness_changed.connect(_apply_brightness)


# ---------------------------------------------------------------------------
#  environment: a house at dusk
# ---------------------------------------------------------------------------

func _build_environment() -> void:
	env = Environment.new()

	# A real 4K sky panorama — blue with cloud — rather than a procedural
	# gradient. Godot takes the ambient light straight out of it, so the whole
	# scene is lit correctly by the sky for free, which matters a lot now that
	# nothing casts a shadow.
	var sky := Sky.new()
	var sky_path := "res://assets/hdri/sky.hdr"
	if ResourceLoader.exists(sky_path):
		var pano := PanoramaSkyMaterial.new()
		pano.panorama = load(sky_path)
		pano.energy_multiplier = 1.0
		sky.sky_material = pano
	else:
		var fallback := ProceduralSkyMaterial.new()
		fallback.sky_top_color = Color(0.28, 0.48, 0.82)
		fallback.sky_horizon_color = Color(0.78, 0.86, 0.95)
		fallback.ground_bottom_color = Color(0.42, 0.46, 0.40)
		fallback.ground_horizon_color = Color(0.72, 0.78, 0.80)
		sky.sky_material = fallback
	sky.radiance_size = Sky.RADIANCE_SIZE_128

	env.background_mode = Environment.BG_SKY
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	env.ambient_light_sky_contribution = 1.0
	env.ambient_light_energy = AMBIENT_BASE * Settings.brightness

	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	env.tonemap_exposure = 1.15
	env.tonemap_white = 6.0

	env.glow_enabled = false
	env.glow_intensity = 0.4
	env.glow_strength = 1.0
	env.glow_bloom = 0.04
	env.glow_hdr_threshold = 1.3
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_SOFTLIGHT

	# With no shadows anywhere, screen-space ambient occlusion is the only thing
	# left that darkens contact points, so it does the work of grounding objects.
	env.ssao_enabled = true
	env.ssao_radius = 0.9
	env.ssao_intensity = 2.4
	env.ssao_power = 1.5
	env.ssao_light_affect = 0.2
	env.ssao_detail = 0.5

	env.ssil_enabled = false
	env.volumetric_fog_enabled = false
	env.fog_enabled = true
	env.fog_mode = Environment.FOG_MODE_DEPTH
	env.fog_light_color = Color(0.72, 0.80, 0.90)
	env.fog_density = 0.0
	env.fog_depth_begin = 90.0
	env.fog_depth_end = 260.0
	env.fog_depth_curve = 1.4
	env.fog_sky_affect = 0.0

	env.adjustment_enabled = true
	env.adjustment_brightness = 1.0
	env.adjustment_contrast = 1.04
	env.adjustment_saturation = 1.02

	world_env = WorldEnvironment.new()
	world_env.environment = env
	add_child(world_env)

	# The sun, matched to where it sits in the panorama. It casts no shadow —
	# no light in this game does any more.
	moon = DirectionalLight3D.new()
	moon.rotation_degrees = Vector3(-48.0, 138.0, 0.0)
	moon.light_color = Color(1.0, 0.97, 0.90)
	moon.light_energy = 2.60
	moon.shadow_enabled = false
	moon.light_specular = 0.5
	add_child(moon)


# ---------------------------------------------------------------------------
#  HUD
# ---------------------------------------------------------------------------

func _build_hud() -> void:
	var layer := CanvasLayer.new()
	# the HUD and the menu keep running while the tree is paused
	layer.process_mode = Node.PROCESS_MODE_ALWAYS
	add_child(layer)

	hud = TouchUI.new()
	hud.name = "TouchUI"
	layer.add_child(hud)
	hud.pause_pressed.connect(_toggle_menu)

	fps_label = Label.new()
	fps_label.position = Vector2(24, 18)
	fps_label.add_theme_font_size_override("font_size", 26)
	fps_label.add_theme_color_override("font_color", Color(1, 1, 1, 0.55))
	fps_label.visible = Settings.show_fps
	layer.add_child(fps_label)

	menu = _build_menu()
	layer.add_child(menu)
	menu.visible = false


func _build_menu() -> Control:
	var root := Control.new()
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.mouse_filter = Control.MOUSE_FILTER_STOP

	var dim := ColorRect.new()
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	dim.color = Color(0, 0, 0, 0.72)
	root.add_child(dim)

	var panel := VBoxContainer.new()
	panel.set_anchors_preset(Control.PRESET_CENTER)
	panel.position = Vector2(-260, -300)
	panel.custom_minimum_size = Vector2(520, 0)
	panel.add_theme_constant_override("separation", 18)
	root.add_child(panel)

	var title := Label.new()
	title.text = "SPIDER HOUSE"
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_font_size_override("font_size", 54)
	panel.add_child(title)

	var resume := Button.new()
	resume.text = "Resume"
	resume.custom_minimum_size = Vector2(0, 84)
	resume.add_theme_font_size_override("font_size", 34)
	resume.pressed.connect(_toggle_menu)
	panel.add_child(resume)

	panel.add_child(_slider_row("Look sensitivity", 0.25, 3.0, Settings.look_sensitivity,
		func(v: float) -> void:
			Settings.look_sensitivity = v
			Settings.save_settings()))

	panel.add_child(_slider_row("Camera distance", 0.6, 2.0, Settings.camera_distance,
		func(v: float) -> void:
			Settings.camera_distance = v
			Settings.save_settings()))

	panel.add_child(_slider_row("Brightness", 0.3, 3.0, Settings.brightness,
		func(v: float) -> void: Settings.set_brightness(v)))

	var inv := CheckBox.new()
	inv.text = "Invert vertical look"
	inv.button_pressed = Settings.invert_y
	inv.add_theme_font_size_override("font_size", 30)
	inv.toggled.connect(func(on: bool) -> void:
		Settings.invert_y = on
		Settings.save_settings())
	panel.add_child(inv)

	var fps := CheckBox.new()
	fps.text = "Show FPS"
	fps.button_pressed = Settings.show_fps
	fps.add_theme_font_size_override("font_size", 30)
	fps.toggled.connect(func(on: bool) -> void:
		Settings.show_fps = on
		Settings.save_settings()
		fps_label.visible = on)
	panel.add_child(fps)

	var quality := OptionButton.new()
	quality.add_item("Graphics: Low", 0)
	quality.add_item("Graphics: Medium", 1)
	quality.add_item("Graphics: High", 2)
	quality.selected = Settings.quality
	quality.custom_minimum_size = Vector2(0, 74)
	quality.add_theme_font_size_override("font_size", 30)
	quality.item_selected.connect(func(i: int) -> void: Settings.set_quality(i))
	panel.add_child(quality)

	var respawn := Button.new()
	respawn.text = "Back to the hallway"
	respawn.custom_minimum_size = Vector2(0, 74)
	respawn.add_theme_font_size_override("font_size", 30)
	respawn.pressed.connect(func() -> void:
		spider.teleport(SPAWN)
		_toggle_menu())
	panel.add_child(respawn)

	return root


func _slider_row(label_text: String, min_v: float, max_v: float, value: float,
		on_change: Callable) -> Control:
	var box := VBoxContainer.new()
	var l := Label.new()
	l.text = "%s: %.2f" % [label_text, value]
	l.add_theme_font_size_override("font_size", 28)
	box.add_child(l)
	var s := HSlider.new()
	s.min_value = min_v
	s.max_value = max_v
	s.step = 0.05
	s.value = value
	s.custom_minimum_size = Vector2(0, 52)
	s.value_changed.connect(func(v: float) -> void:
		l.text = "%s: %.2f" % [label_text, v]
		on_change.call(v))
	box.add_child(s)
	return box


func _toggle_menu() -> void:
	menu.visible = not menu.visible
	get_tree().paused = menu.visible
	hud.visible = not menu.visible


# ---------------------------------------------------------------------------
#  audio
# ---------------------------------------------------------------------------

func _build_audio() -> void:
	var ambient := _stream("res://assets/audio/ambient.wav", true)
	if ambient:
		var p := AudioStreamPlayer.new()
		p.stream = ambient
		p.volume_db = -14.0
		p.autoplay = true
		p.bus = "Master"
		add_child(p)
		p.play()

	for i in range(6):
		var sp := AudioStreamPlayer3D.new()
		sp.max_distance = 22.0
		sp.unit_size = 3.0
		sp.volume_db = -6.0
		add_child(sp)
		_step_players.append(sp)


func _stream(path: String, loop: bool) -> AudioStream:
	if not ResourceLoader.exists(path):
		return null
	var s: Resource = load(path)
	if loop and s is AudioStreamWAV:
		(s as AudioStreamWAV).loop_mode = AudioStreamWAV.LOOP_FORWARD
		(s as AudioStreamWAV).loop_end = (s as AudioStreamWAV).data.size() / 2
	return s


func _on_footstep(pos: Vector3, speed01: float) -> void:
	if _step_players.is_empty():
		return
	var idx := _rng.randi_range(1, 4)
	var stream := _stream("res://assets/audio/step%d.wav" % idx, false)
	if stream == null:
		return
	var p := _step_players[_step_index % _step_players.size()]
	_step_index += 1
	p.stream = stream
	p.global_position = pos
	p.pitch_scale = _rng.randf_range(0.86, 1.18)
	p.volume_db = lerpf(-22.0, -6.0, speed01)
	p.play()


# ---------------------------------------------------------------------------
#  quality presets
# ---------------------------------------------------------------------------

func _apply_quality(level: int) -> void:
	var vp := get_viewport()
	match level:
		Settings.Quality.LOW:
			env.ssao_enabled = false
			env.glow_enabled = false
			vp.scaling_3d_scale = 0.90
			vp.msaa_3d = Viewport.MSAA_DISABLED
			vp.mesh_lod_threshold = 2.5
		Settings.Quality.MEDIUM:
			env.ssao_enabled = true
			env.glow_enabled = false
			# Full resolution. Rendering 3D at 80% and upscaling was the real
			# reason everything looked soft and blocky, far more than the
			# texture resolution was.
			vp.scaling_3d_scale = 1.0
			vp.msaa_3d = Viewport.MSAA_DISABLED
			vp.mesh_lod_threshold = 1.5
		_:
			env.ssao_enabled = true
			env.glow_enabled = true
			vp.scaling_3d_scale = 1.0
			vp.msaa_3d = Viewport.MSAA_2X
			vp.mesh_lod_threshold = 1.0


func _apply_brightness(value: float) -> void:
	env.ambient_light_energy = AMBIENT_BASE * value


# ---------------------------------------------------------------------------
#  frame loop: feed input from the HUD into the spider and the camera
# ---------------------------------------------------------------------------

func _process(delta: float) -> void:
	if menu.visible:
		return

	var move: Vector2 = hud.move_vector
	# keyboard fallback so the game is playable on a desktop too
	var kb := Input.get_vector("ui_left", "ui_right", "ui_down", "ui_up")
	if kb.length() > 0.05:
		move = kb
	spider.move_input = move
	var mode := hud.speed_mode
	if Input.is_key_pressed(KEY_SHIFT):
		mode = 2
	elif Input.is_key_pressed(KEY_CTRL):
		mode = 1
	spider.speed_mode = mode
	if hud.take_jump() or Input.is_key_pressed(KEY_SPACE):
		spider.jump_queued = true
	spider.camera_basis = rig.global_transform.basis

	rig.look_delta += hud.take_look()

	if Settings.show_fps:
		fps_label.text = "%d fps" % Engine.get_frames_per_second()

	# Last-resort floor. Terrain closes the world, but if anything ever does slip
	# through a seam this puts the player back rather than dropping them forever.
	if spider.global_position.y < FALL_LIMIT:
		spider.teleport(SPAWN)

	_creak_timer -= delta
	if _creak_timer <= 0.0:
		_creak_timer = _rng.randf_range(9.0, 26.0)
		var creak := _stream("res://assets/audio/creak%d.wav" % _rng.randi_range(1, 3), false)
		if creak and not _step_players.is_empty():
			var p := _step_players[0]
			p.stream = creak
			p.global_position = spider.global_position + Vector3(
				_rng.randf_range(-6.0, 6.0), _rng.randf_range(0.0, 4.0),
				_rng.randf_range(-6.0, 6.0))
			p.pitch_scale = _rng.randf_range(0.8, 1.1)
			p.volume_db = -16.0
			p.play()


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion and Input.is_mouse_button_pressed(MOUSE_BUTTON_RIGHT):
		rig.look_delta += (event as InputEventMouseMotion).relative
