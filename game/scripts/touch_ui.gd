class_name TouchUI
extends Control
## On-screen controls: a floating left stick for movement, right-half drag to
## look, and three action buttons. Everything is drawn procedurally so the HUD
## scales cleanly to any phone resolution without shipping UI textures.

signal pause_pressed()

const STICK_RADIUS := 130.0
const KNOB_RADIUS := 54.0
const BTN_R := 74.0

var move_vector := Vector2.ZERO
var look_delta := Vector2.ZERO
## 0 = slow walk, 1 = fast walk, 2 = run
var speed_mode := 0
var jump_pressed := false

var _stick_touch := -1
var _look_touch := -1
var _stick_origin := Vector2.ZERO
var _stick_pos := Vector2.ZERO
var _btn_touches := {}          # touch index -> button name
var _pressed := {}              # button name -> bool
var _hint_alpha := 1.0
var _font: Font


func _ready() -> void:
	# set_anchors_preset() only moves the anchors — the offsets stay put, so the
	# control keeps its starting size of zero. Everything here is laid out from
	# `size`, so at zero the stick and the buttons land off the left/top edge of
	# the screen and the touch zones collapse to nothing. Set both.
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_font = ThemeDB.fallback_font
	_fit_to_viewport()
	get_viewport().size_changed.connect(_fit_to_viewport)
	set_process(true)


func _fit_to_viewport() -> void:
	var vp := get_viewport_rect().size
	if vp.x > 0.0 and vp.y > 0.0:
		size = vp
		position = Vector2.ZERO


## Where the stick sits when nobody is touching it.
func _stick_home() -> Vector2:
	return Vector2(STICK_RADIUS + 70.0, size.y - STICK_RADIUS - 70.0)


func _button_positions() -> Dictionary:
	var s := size
	return {
		"jump": Vector2(s.x - 130.0, s.y - 150.0),
		"run": Vector2(s.x - 285.0, s.y - 120.0),
			"fast": Vector2(s.x - 180.0, s.y - 320.0),
		"pause": Vector2(s.x - 70.0, 70.0),
	}


func _process(delta: float) -> void:
	if size.x < 1.0 or size.y < 1.0:
		_fit_to_viewport()
	if _hint_alpha > 0.0 and (move_vector.length() > 0.2 or look_delta.length() > 4.0):
		_hint_alpha = maxf(_hint_alpha - delta * 0.8, 0.0)
	# run beats fast walk if both thumbs are down
	if _pressed.get("run", false):
		speed_mode = 2
	elif _pressed.get("fast", false):
		speed_mode = 1
	else:
		speed_mode = 0
	queue_redraw()


func _input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo \
			and (event as InputEventKey).keycode == KEY_ESCAPE:
		pause_pressed.emit()
	elif event is InputEventScreenTouch:
		_handle_touch(event)
	elif event is InputEventScreenDrag:
		_handle_drag(event)


func _handle_touch(e: InputEventScreenTouch) -> void:
	var buttons := _button_positions()
	if e.pressed:
		# buttons win over the look/move zones
		for name in buttons:
			var r: float = BTN_R * (0.8 if name == "pause" else 1.0)
			if e.position.distance_to(buttons[name]) <= r + 14.0:
				_btn_touches[e.index] = name
				_pressed[name] = true
				match name:
					"jump": jump_pressed = true
					"pause": pause_pressed.emit()
				return
		if e.position.x < size.x * 0.46 and _stick_touch == -1:
			_stick_touch = e.index
			_stick_origin = e.position
			_stick_pos = e.position
			return
		if _look_touch == -1:
			_look_touch = e.index
			return
	else:
		if _btn_touches.has(e.index):
			_pressed[_btn_touches[e.index]] = false
			_btn_touches.erase(e.index)
			return
		if e.index == _stick_touch:
			_stick_touch = -1
			move_vector = Vector2.ZERO
			return
		if e.index == _look_touch:
			_look_touch = -1


func _handle_drag(e: InputEventScreenDrag) -> void:
	if e.index == _stick_touch:
		_stick_pos = e.position
		var d := _stick_pos - _stick_origin
		if d.length() > STICK_RADIUS:
			# the stick base follows the thumb instead of clamping hard
			_stick_origin = _stick_pos - d.normalized() * STICK_RADIUS
			d = d.normalized() * STICK_RADIUS
		move_vector = Vector2(d.x, -d.y) / STICK_RADIUS
	elif e.index == _look_touch:
		look_delta += e.relative


## Consumed once per frame by the game script.
func take_look() -> Vector2:
	var d := look_delta
	look_delta = Vector2.ZERO
	return d


func take_jump() -> bool:
	var j := jump_pressed
	jump_pressed = false
	return j


# ---------------------------------------------------------------------------

func _draw() -> void:
	var dim := Color(1, 1, 1, 0.20)
	var bright := Color(1, 1, 1, 0.42)

	# --- movement stick ---
	if _stick_touch != -1:
		draw_arc(_stick_origin, STICK_RADIUS, 0.0, TAU, 48, dim, 4.0, true)
		draw_circle(_stick_origin, STICK_RADIUS, Color(1, 1, 1, 0.05))
		var knob := _stick_origin + Vector2(move_vector.x, -move_vector.y) * STICK_RADIUS
		draw_circle(knob, KNOB_RADIUS, Color(1, 1, 1, 0.22))
		draw_arc(knob, KNOB_RADIUS, 0.0, TAU, 32, bright, 3.0, true)
	else:
		var home := _stick_home()
		draw_circle(home, STICK_RADIUS, Color(1, 1, 1, 0.06))
		draw_arc(home, STICK_RADIUS, 0.0, TAU, 48, Color(1, 1, 1, 0.30), 4.0, true)
		draw_circle(home, KNOB_RADIUS, Color(1, 1, 1, 0.16))
		draw_arc(home, KNOB_RADIUS, 0.0, TAU, 32, Color(1, 1, 1, 0.38), 3.0, true)

	# --- action buttons ---
	var buttons := _button_positions()
	_draw_button(buttons["jump"], BTN_R, "JUMP", _pressed.get("jump", false))
	_draw_button(buttons["run"], BTN_R, "RUN", _pressed.get("run", false))
	_draw_button(buttons["fast"], BTN_R * 0.88, "FAST", _pressed.get("fast", false))
	_draw_button(buttons["pause"], BTN_R * 0.8, "II", false)

	# --- first-run hint ---
	if _hint_alpha > 0.01 and _font:
		var c := Color(1, 1, 1, _hint_alpha * 0.75)
		var text := "Left thumb: walk   ·   Right side: look   ·   FAST / RUN to speed up   ·   Every wall, roof and lamp is climbable"
		var w := _font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, 34).x
		draw_string(_font, Vector2((size.x - w) * 0.5, size.y * 0.16), text,
			HORIZONTAL_ALIGNMENT_LEFT, -1, 34, c)


func _draw_button(center: Vector2, radius: float, label: String, active: bool) -> void:
	var fill := Color(1, 1, 1, 0.20) if active else Color(1, 1, 1, 0.08)
	var edge := Color(1, 1, 1, 0.55) if active else Color(1, 1, 1, 0.28)
	draw_circle(center, radius, fill)
	draw_arc(center, radius, 0.0, TAU, 40, edge, 3.0, true)
	if _font:
		var fs := 30
		var w := _font.get_string_size(label, HORIZONTAL_ALIGNMENT_LEFT, -1, fs)
		draw_string(_font, center + Vector2(-w.x * 0.5, fs * 0.36), label,
			HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Color(1, 1, 1, 0.8))
