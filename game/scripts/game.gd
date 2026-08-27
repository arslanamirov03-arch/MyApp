extends Node3D
## Boots the whole thing: environment, house, props, spider, camera and HUD.

const SPAWN := Vector3(15.0, 0.75, 11.0)
## Ambient fill at brightness 1.0. The slider scales this.
const AMBIENT_BASE := 1.75

var house: House
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

	house = House.new()
	house.name = "House"
	add_child(house)

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

	var sky_mat := ProceduralSkyMaterial.new()
	sky_mat.sky_top_color = Color(0.035, 0.055, 0.135)
	sky_mat.sky_horizon_color = Color(0.150, 0.140, 0.190)
	sky_mat.ground_bottom_color = Color(0.010, 0.010, 0.015)
	sky_mat.ground_horizon_color = Color(0.055, 0.050, 0.065)
	sky_mat.sun_angle_max = 14.0
	sky_mat.sun_curve = 0.08
	sky_mat.sky_energy_multiplier = 1.30
	var sky := Sky.new()
	sky.sky_material = sky_mat

	env.background_mode = Environment.BG_SKY
	env.sky = sky
	# A flat fill light so walls, floors and corners stay readable everywhere.
	# Sky-sourced ambient only lit what could see a window; this is a constant
	# and is what the brightness slider drives. The lamps are untouched — they
	# still make the warm pools on top of this.
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_sky_contribution = 0.0
	env.ambient_light_color = Color(0.62, 0.65, 0.76)
	env.ambient_light_energy = AMBIENT_BASE * Settings.brightness

	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	env.tonemap_exposure = 1.10
	env.tonemap_white = 6.0

	env.glow_enabled = true
	env.glow_intensity = 0.55
	env.glow_strength = 1.0
	env.glow_bloom = 0.06
	env.glow_hdr_threshold = 1.05
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_SOFTLIGHT

	env.ssao_enabled = true
	env.ssao_radius = 0.7
	env.ssao_intensity = 2.6
	env.ssao_power = 1.6
	env.ssao_light_affect = 0.25
	env.ssao_detail = 0.6

	env.ssil_enabled = false
	env.ssil_radius = 3.0
	env.ssil_intensity = 0.85

	# dust hanging in the air: this is what turns the window light into shafts
	env.volumetric_fog_enabled = false
	env.volumetric_fog_density = 0.010
	env.volumetric_fog_albedo = Color(0.62, 0.63, 0.70)
	env.volumetric_fog_emission = Color(0.012, 0.013, 0.020)
	env.volumetric_fog_gi_inject = 0.9
	env.volumetric_fog_anisotropy = 0.25
	env.volumetric_fog_length = 52.0
	env.volumetric_fog_detail_spread = 2.0
	env.volumetric_fog_ambient_inject = 0.6

	env.adjustment_enabled = true
	env.adjustment_brightness = 1.0
	env.adjustment_contrast = 1.08
	env.adjustment_saturation = 0.86

	world_env = WorldEnvironment.new()
	world_env.environment = env
	add_child(world_env)

	# low moon raking through the windows
	moon = DirectionalLight3D.new()
	# A moon this low threw shadows several rooms long. Steeper keeps the light
	# coming through the windows but the shadows short and readable.
	moon.rotation_degrees = Vector3(-56.0, 128.0, 0.0)
	moon.light_color = Color(0.60, 0.72, 1.0)
	moon.light_energy = 3.60
	moon.light_angular_distance = 0.30
	moon.shadow_enabled = true
	moon.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_4_SPLITS
	moon.directional_shadow_max_distance = 32.0
	moon.directional_shadow_blend_splits = true
	moon.shadow_bias = 0.02
	moon.shadow_normal_bias = 0.6
	moon.light_specular = 0.6
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
	# Omni shadows dominate the frame time: each one re-renders the whole house
	# into a cubemap, every frame, because the spider is always moving.
	var shadow_casters := 0

	match level:
		Settings.Quality.LOW:
			env.ssao_enabled = false
			env.ssil_enabled = false
			env.volumetric_fog_enabled = false
			env.glow_enabled = false
			shadow_casters = 0
			moon.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
			vp.scaling_3d_scale = 0.80
			vp.msaa_3d = Viewport.MSAA_DISABLED
			vp.mesh_lod_threshold = 3.0
		Settings.Quality.MEDIUM:
			env.ssao_enabled = true
			env.ssil_enabled = false
			env.volumetric_fog_enabled = false
			env.glow_enabled = true
			shadow_casters = 1
			moon.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
			vp.scaling_3d_scale = 0.90
			vp.msaa_3d = Viewport.MSAA_DISABLED
			vp.mesh_lod_threshold = 2.0
		_:
			env.ssao_enabled = true
			env.ssil_enabled = true
			env.volumetric_fog_enabled = true
			env.glow_enabled = true
			shadow_casters = 3
			moon.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_4_SPLITS
			vp.scaling_3d_scale = 1.0
			vp.msaa_3d = Viewport.MSAA_2X
			vp.mesh_lod_threshold = 1.0

	for i in range(props.shadow_candidates.size()):
		props.shadow_candidates[i].shadow_enabled = i < shadow_casters


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
	spider.run_held = hud.run_held or Input.is_key_pressed(KEY_SHIFT)
	if hud.take_jump() or Input.is_key_pressed(KEY_SPACE):
		spider.jump_queued = true
	if hud.take_attack():
		spider.attack_queued = true
	spider.camera_basis = rig.global_transform.basis

	rig.look_delta += hud.take_look()

	if Settings.show_fps:
		fps_label.text = "%d fps" % Engine.get_frames_per_second()

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
