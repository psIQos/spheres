#!/usr/bin/env python3
"""End-to-end check on an emulator: installs the APK, plays a full "moves" game
by reading the dot colors from screenshots, checks score/HUD/game-over, then
runs a monkey stress test and fails on any crash.

Usage: play.py <apk> <output-dir>

Screenshots are written to <output-dir>; publish_shots.py makes them readable
through the GitHub API.
"""
import io
import os
import re
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET

from PIL import Image

PKG = "com.psiqos.spheres"
APK, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)

# Must match game/Palette in GameView.kt.
PALETTE = [(0xEC, 0x5B, 0x57), (0xF4, 0xC8, 0x42), (0x83, 0xD6, 0x6A), (0x5C, 0xA8, 0xEC), (0x9E, 0x6C, 0xDB),
           (0x27, 0xB9, 0xA6)]
# Board of the difficulty being played (see Difficulty in GameMode.kt).
ROWS = COLS = 6
COLORS = 5


def set_board(size, colors):
    global ROWS, COLS, COLORS
    ROWS = COLS = size
    COLORS = colors

failures = []
results = []


def annotate(level, msg):
    """GitHub workflow command; shows up as an annotation on the job."""
    msg = msg.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::{level}::{msg}", flush=True)


def check(cond, msg):
    line = ("ok   " if cond else "FAIL ") + msg
    print(line, flush=True)
    results.append(line)
    if not cond:
        failures.append(msg)
        annotate("error", msg)
    return cond


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True).stdout.strip()


def sh(cmd):
    return adb("shell", cmd)


SDK = int(sh("getprop ro.build.version.sdk") or 0)


def screencap():
    raw = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout
    return Image.open(io.BytesIO(raw)).convert("RGB")


def shot(name, img=None):
    img = img or screencap()
    img.save(f"{OUT}/{name}.png")


def dump():
    for _ in range(5):
        out = sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml")
        if "<?xml" in out:
            root = ET.fromstring(out[out.index("<?xml"):])
            if dismiss_system_dialog(root):
                continue
            nodes = {}
            for n in root.iter("node"):
                rid = n.get("resource-id", "")
                if rid.startswith(PKG + ":id/"):
                    nodes[rid.split("/")[-1]] = n
            return nodes
        time.sleep(1)
    print("warning: uiautomator dump failed: " + out[-200:], flush=True)
    return {}


def wait_for(rid, timeout=15):
    """Dumps the UI until a view with this id shows up."""
    end = time.time() + timeout
    nodes = {}
    while time.time() < end:
        nodes = dump()
        if rid in nodes:
            return nodes
        time.sleep(1)
    check(False, f"view '{rid}' did not appear (saw: {sorted(nodes)})")
    shot(f"err-missing-{rid}")
    return nodes


def dismiss_system_dialog(root):
    """The slow emulator sometimes shows "System UI isn't responding" over the app.
    Such dialogs are closed; one about our own app counts as a failure."""
    by_id = {n.get("resource-id"): n for n in root.iter("node")}
    button = by_id.get("android:id/aerr_wait") or by_id.get("android:id/aerr_close")
    if button is None:
        return False
    title = " ".join(n.get("text", "") for n in root.iter("node") if n.get("text"))
    if "Spheres" in title or "spheres" in title:
        check(False, f"system dialog about the app: {title}")
        shot(f"err-dialog-{int(time.time())}")
    else:
        print(f"dismissing unrelated system dialog: {title}", flush=True)
    tap(button)
    time.sleep(2)
    return True


def bounds(n):
    return tuple(map(int, re.findall(r"\d+", n.get("bounds"))))


def tap(n):
    l, t, r, b = bounds(n)
    sh(f"input tap {(l + r) // 2} {(t + b) // 2}")


def text(nodes, rid):
    n = nodes.get(rid)
    return n.get("text") if n is not None else None


def geometry(nodes):
    """Dot centers, mirroring GameView.onSizeChanged."""
    l, t, r, b = bounds(nodes["game_view"])
    w, h = r - l, b - t
    density = int(re.findall(r"\d+", sh("wm density"))[-1]) / 160
    pad = 16 * density
    cell = min(w - 2 * pad, h - 2 * pad) / max(ROWS, COLS)
    ox, oy = l + (w - cell * COLS) / 2, t + (h - cell * ROWS) / 2
    return lambda row, col: (int(ox + (col + 0.5) * cell), int(oy + (row + 0.5) * cell))


def read_board(img, pos):
    grid = []
    for r in range(ROWS):
        row = []
        for c in range(COLS):
            px = img.getpixel(pos(r, c))
            d = [sum((a - b) ** 2 for a, b in zip(px, p)) for p in PALETTE[:COLORS]]
            k = min(range(COLORS), key=d.__getitem__)
            row.append(k if d[k] < 3 * 40 ** 2 else -1)
        grid.append(row)
    return grid


def neighbors(r, c):
    for dr, dc in ((0, 1), (1, 0), (0, -1), (-1, 0)):
        if 0 <= r + dr < ROWS and 0 <= c + dc < COLS:
            yield r + dr, c + dc


def find_square(grid):
    for r in range(ROWS - 1):
        for c in range(COLS - 1):
            k = grid[r][c]
            if k >= 0 and grid[r][c + 1] == k and grid[r + 1][c] == k and grid[r + 1][c + 1] == k:
                return [(r, c), (r, c + 1), (r + 1, c + 1), (r + 1, c), (r, c)]
    return None


def longest_path(grid, limit=7):
    best = []

    def dfs(path):
        nonlocal best
        if len(path) > len(best):
            best = list(path)
        if len(path) >= limit:
            return
        r, c = path[-1]
        for n in neighbors(r, c):
            if n not in path and grid[n[0]][n[1]] == grid[r][c]:
                path.append(n)
                dfs(path)
                path.pop()

    for r in range(ROWS):
        for c in range(COLS):
            if grid[r][c] >= 0:
                dfs([(r, c)])
    return best


def drag(points, hold=None):
    """Draws a path with the finger. With `hold`, takes a screenshot before lifting."""
    if SDK >= 29:
        moves = "; ".join(f"input motionevent MOVE {x} {y}" for x, y in points[1:])
        sh(f"input motionevent DOWN {points[0][0]} {points[0][1]}; {moves}")
        if hold:
            time.sleep(0.3)
            shot(hold)
        sh(f"input motionevent UP {points[-1][0]} {points[-1][1]}")
    else:
        (x0, y0), (x1, y1) = points[0], points[1]
        sh(f"input swipe {x0} {y0} {x1} {y1} 400")


def check_anrs(phase):
    log = adb("logcat", "-d", "-b", "main,system")
    anrs = sorted({l.split("ANR in", 1)[1].strip() for l in log.splitlines() if "ANR in" in l})
    ours = [a for a in anrs if a.startswith(PKG)]
    check(not ours, f"no ANR of the app during {phase}" + (f": {ours}" if ours else ""))
    if anrs and not ours:
        print(f"ignored ANRs of other processes during {phase}: {anrs}", flush=True)
        results.append(f"info ignored ANRs of other processes during {phase}: {anrs}")


def backtrack_test():
    """Draws a path, swipes back over it sloppily (offset sideways) and releases on the
    second dot: only the first two dots may be removed (issue #2)."""
    nodes = wait_for("game_view")
    if "game_view" not in nodes:
        return
    pos = geometry(nodes)
    cell = pos(0, 1)[0] - pos(0, 0)[0]
    # The board is random: play ordinary moves until it has a path of 4 or more.
    for _ in range(15):
        path = longest_path(read_board(screencap(), pos), limit=6)
        if len(path) >= 4:
            break
        drag([pos(r, c) for r, c in path])
        time.sleep(1.5)
    if not check(len(path) >= 4, f"backtrack: found a path of {len(path)} >= 4 dots"):
        return
    nodes = dump()
    before = int(text(nodes, "score_value") or 0)
    forward = [pos(r, c) for r, c in path]
    back = []
    for (r0, c0), (r1, c1) in zip(reversed(path[1:]), reversed(path[:-1])):
        # Offset perpendicular to the segment by 0.4 cells, like a hurried finger.
        ox, oy = (0, 0.4 * cell) if r0 == r1 else (0.4 * cell, 0)
        for k in (1, 2):  # two samples per segment
            x = pos(r0, c0)[0] + (pos(r1, c1)[0] - pos(r0, c0)[0]) * k / 2 + ox
            y = pos(r0, c0)[1] + (pos(r1, c1)[1] - pos(r0, c0)[1]) * k / 2 + oy
            back.append((int(x), int(y)))
        if (r1, c1) == path[1]:
            break
    drag(forward + back, hold="09-backtrack")
    time.sleep(1.2)
    nodes = dump()
    after = text(nodes, "score_value")
    check(after == str(before + 2), f"backtrack over {len(path)} dots back to the 2nd: score {before} -> {after}, expected +2")


def play_moves(total, label, shots=False):
    """Plays a whole moves-mode game on the current screen; returns the final score."""
    nodes = dump()
    pos = geometry(nodes)
    expected = 0
    # A continued game does not start at 0.
    observed = int(text(nodes, "score_value") or 0)
    moves_left = total
    squares = 0
    took_drag_shot = False
    for move in range(total):
        grid = None
        for _ in range(4):
            grid = read_board(screencap(), pos)
            if all(k >= 0 for row in grid for k in row):
                break
            dump()  # closes a system dialog covering the board, if any
            time.sleep(0.5)
        if not check(all(k >= 0 for row in grid for k in row), f"{label} move {move + 1}: all dots recognized"):
            shot(f"err-board-{label}-{move + 1}")
            break

        square = find_square(grid) if SDK >= 29 else None
        path = square or longest_path(grid, limit=7 if SDK >= 29 else 2)
        if len(path) < 2:
            check(False, f"{label} move {move + 1}: no move found on board {grid}")
            break
        color = grid[path[0][0]][path[0][1]]
        gain = sum(row.count(color) for row in grid) if square else len(path)

        hold = None
        if shots and square and squares == 0:
            hold = "03-square"
        elif shots and not square and len(path) >= 4 and not took_drag_shot:
            hold, took_drag_shot = "03-drag", True
        drag([pos(r, c) for r, c in path], hold=hold)
        squares += 1 if square else 0
        expected += gain
        time.sleep(1.0)

        if shots and move == total // 2:
            shot("04-midgame")
        if move < total - 1:
            # Compare with the previous reading, so one lost move is reported once
            # instead of shifting every later comparison. A busy emulator can deliver
            # the injected swipe late, so wait a little for the HUD to change.
            deadline = time.time() + 5
            while True:
                nodes = dump()
                score, left = text(nodes, "score_value"), text(nodes, "limit_value")
                if left != str(moves_left) or time.time() > deadline:
                    break
                time.sleep(0.5)
            check(score == str(observed + gain),
                  f"{label} move {move + 1} ({'square' if square else f'{len(path)} dots'}): "
                  f"score {score} == {observed} + {gain}")
            check(left == str(moves_left - 1), f"{label} move {move + 1}: moves left {left} == {moves_left - 1}")
            if score is not None and score.isdigit():
                observed = int(score)
            if left is not None and left.isdigit():
                moves_left = int(left)
        else:
            observed += gain
    print(f"{label}: played {total} moves, {squares} squares, expected score {expected}")
    return observed


def leave_game():
    """Back to the menu: through the pause menu, or from the game-over screen."""
    sh("input keyevent 4")
    time.sleep(1)
    nodes = dump()
    if "pause_to_menu" in nodes:
        tap(nodes["pause_to_menu"])
    elif "to_menu" in nodes:
        tap(nodes["to_menu"])
    time.sleep(1.5)


def resume_test():
    """Leaving an endless game and even killing the app keeps it (issue #6)."""
    nodes = wait_for("game_view")
    pos = geometry(nodes)
    path = longest_path(read_board(screencap(), pos), limit=3)
    drag([pos(r, c) for r, c in path] if SDK >= 29 else [pos(*path[0]), pos(*path[1])])
    time.sleep(1.5)
    nodes = dump()
    score = text(nodes, "score_value")
    board = read_board(screencap(), pos)
    leave_game()
    nodes = wait_for("best_endless")
    hint = text(nodes, "best_endless") or ""
    check(score is not None and score in hint, f"menu offers to resume ({hint})")

    sh(f"am force-stop {PKG}")
    time.sleep(1)
    launch()
    time.sleep(2)
    nodes = wait_for("best_endless")
    check(score is not None and score in (text(nodes, "best_endless") or ""),
          f"saved game survives killing the app ({text(nodes, 'best_endless')})")
    tap(nodes["mode_endless"])
    time.sleep(2.5)
    nodes = wait_for("game_view")
    check("resume" not in nodes, "continues directly, without the pause menu")
    check(text(nodes, "score_value") == score, f"resumed with score {text(nodes, 'score_value')} == {score}")
    check(read_board(screencap(), pos) == board, "resumed with the same board")
    shot("15-resumed")


def open_settings():
    nodes = wait_for("settings_button")
    tap(nodes["settings_button"])
    time.sleep(1.5)
    return wait_for("difficulty_group")


def choose_difficulty(rid):
    nodes = open_settings()
    if rid in nodes:
        tap(nodes[rid])
    time.sleep(0.5)
    sh("input keyevent 4")
    time.sleep(1.5)
    return wait_for("difficulty_summary")


def settings_test():
    """Settings screen (issue #7): options are shown, records can be reset after confirming."""
    nodes = open_settings()
    for rid in ("difficulty_easy", "difficulty_normal", "difficulty_hard", "sound_switch", "vibration_switch", "reset_records"):
        check(rid in nodes, f"settings show {rid}")
    check("checked=\"true\"" in ET.tostring(nodes["difficulty_normal"]).decode() if "difficulty_normal" in nodes else False,
          "normal is selected")
    check(re.search(r"5\D+6.6\D+60\D+30", text(nodes, "difficulty_normal") or "") is not None,
          f"normal lists its rules ({text(nodes, 'difficulty_normal')})")
    shot("16-settings")
    tap(nodes["reset_records"])
    time.sleep(1)
    root = ET.fromstring(sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml"))
    confirm = next((n for n in root.iter("node") if n.get("resource-id") == "android:id/button1"), None)
    if check(confirm is not None, "reset asks for confirmation"):
        tap(confirm)
        time.sleep(1)
    sh("input keyevent 4")
    time.sleep(1.5)
    nodes = wait_for("best_moves")
    check("No score" in (text(nodes, "best_moves") or ""), f"records were reset ({text(nodes, 'best_moves')})")


def our_vibrations():
    """Lines of the system's recent-vibration log that belong to the app."""
    log = sh("dumpsys vibrator_manager") if SDK >= 31 else sh("dumpsys vibrator")
    return {l.strip() for l in log.splitlines() if PKG in l}


def one_move():
    nodes = wait_for("game_view")
    pos = geometry(nodes)
    path = longest_path(read_board(screencap(), pos), limit=3 if SDK >= 29 else 2)
    drag([pos(r, c) for r, c in path])
    time.sleep(1.5)


def vibration_test():
    """Connecting dots vibrates, unless switched off in the settings."""
    before = our_vibrations()
    nodes = wait_for("mode_moves")
    tap(nodes["mode_moves"])
    time.sleep(2.5)
    one_move()
    after = our_vibrations()
    if SDK >= 31:
        check(after - before, f"connecting dots vibrates ({len(after - before)} new vibrations logged)")
    else:
        # Older dumpsys output does not name the calling app.
        print(f"vibrations logged for the app: {len(after)}", flush=True)
    leave_game()

    nodes = open_settings()
    if "vibration_switch" in nodes:
        tap(nodes["vibration_switch"])
    time.sleep(0.5)
    sh("input keyevent 4")
    time.sleep(1.5)
    nodes = wait_for("mode_moves")
    tap(nodes["mode_moves"])
    time.sleep(2.5)
    before = our_vibrations()
    one_move()
    if SDK >= 31:
        check(our_vibrations() == before, "no vibration when switched off in the settings")
    leave_game()

    # Switch it on again; that plays a sample vibration.
    nodes = open_settings()
    before = our_vibrations()
    if "vibration_switch" in nodes:
        tap(nodes["vibration_switch"])
    time.sleep(1)
    if SDK >= 31:
        check(our_vibrations() - before, "switching vibration on plays a sample")
    sh("input keyevent 4")
    time.sleep(1.5)


def wallet(nodes, rid="wallet_value"):
    digits = re.sub(r"\D", "", text(nodes, rid) or "")
    return int(digits) if digits else None


def earn_dots(need, rounds=6):
    """Plays moves-mode games until the account of the current difficulty holds [need] dots."""
    for _ in range(rounds):
        nodes = wait_for("menu_wallet")
        if (wallet(nodes, "menu_wallet") or 0) >= need:
            break
        tap(nodes["mode_moves"])
        time.sleep(2.5)
        nodes = wait_for("game_view")
        left = text(nodes, "limit_value")
        play_moves(int(left) if left and left.isdigit() else 30, "earn")
        time.sleep(1.5)
        leave_game()
    return wallet(wait_for("menu_wallet"), "menu_wallet")


def top_up(key, dots):
    """Sets the stored dot account [key] (it must exist already) and restarts the app.
    Needs the debuggable e2e build for run-as."""
    sh(f"am force-stop {PKG}")
    sh(f"run-as {PKG} sed -i 's/name=\"{key}\" value=\"[0-9]*\"/name=\"{key}\" value=\"{dots}\"/' shared_prefs/spheres.xml")
    launch()
    time.sleep(2)


def powerup_test():
    """Power-ups (issue #3): paid with collected dots, each with its exact effect.
    The account is kept per difficulty and endless mode earns nothing."""
    need = 100 + 1000 + 300 + 300  # shrinker, expander, time stop, +5 moves
    hard_before = wallet(choose_difficulty("difficulty_hard"), "menu_wallet")
    normal_before = wallet(choose_difficulty("difficulty_normal"), "menu_wallet")
    have = earn_dots(need, rounds=1)  # one game shows earning; the prices take dozens
    check(normal_before is not None and have is not None and have > normal_before,
          f"moves games fill the normal account ({normal_before} -> {have})")
    hard_after = wallet(choose_difficulty("difficulty_hard"), "menu_wallet")
    check(hard_after == hard_before, f"hard account untouched by normal games ({hard_before} -> {hard_after})")
    choose_difficulty("difficulty_normal")
    if have is not None and have < need:
        top_up("wallet", need)
        have = wallet(wait_for("menu_wallet"), "menu_wallet")
    if not check(have is not None and have >= need, f"enough dots for the test ({have})"):
        return

    nodes = wait_for("mode_endless")
    tap(nodes["mode_endless"])
    time.sleep(2.5)
    nodes = wait_for("game_view")
    pos = geometry(nodes)
    check("powerup_shrinker" in nodes and "powerup_expander" in nodes, "endless offers shrinker and expander")
    check("powerup_special" not in nodes, "endless has no time stop / extra moves")

    # Endless earns nothing for the account, but still scores.
    have = wallet(nodes)
    score = int(text(nodes, "score_value") or 0)
    one_move()
    nodes = dump()
    check(int(text(nodes, "score_value") or 0) > score and wallet(nodes) == have,
          f"endless scores but earns no dots (score {score} -> {text(nodes, 'score_value')}, account {have} -> {wallet(nodes)})")

    # Shrinker: choose, cancel, choose again, use.
    tap(nodes["powerup_shrinker"])
    time.sleep(0.8)
    nodes = dump()
    check("powerup_hint" in nodes, f"shrinker asks to tap a dot ({text(nodes, 'powerup_hint')})")
    tap(nodes["powerup_shrinker"])
    time.sleep(0.8)
    nodes = dump()
    check("powerup_hint" not in nodes and wallet(nodes) == have, "tapping again cancels without paying")
    score = int(text(nodes, "score_value") or 0)
    moves = text(nodes, "limit_value")
    tap(nodes["powerup_shrinker"])
    time.sleep(0.8)
    shot("17-shrinker")
    sh("input tap {} {}".format(*pos(2, 2)))
    time.sleep(1.5)
    nodes = dump()
    check(wallet(nodes) == have - 100, f"shrinker costs 100 ({have} -> {wallet(nodes)})")
    check(text(nodes, "score_value") == str(score + 1), f"shrinker scores 1 dot ({score} -> {text(nodes, 'score_value')})")
    check(text(nodes, "limit_value") == moves, "a power-up is not a move")
    have = wallet(nodes)

    # Expander: all dots of the tapped color.
    grid = read_board(screencap(), pos)
    color = grid[0][0]
    count = sum(row.count(color) for row in grid)
    score = int(text(nodes, "score_value") or 0)
    tap(nodes["powerup_expander"])
    time.sleep(0.8)
    sh("input tap {} {}".format(*pos(0, 0)))
    time.sleep(1.5)
    nodes = dump()
    check(wallet(nodes) == have - 1000, f"expander costs 1000 ({have} -> {wallet(nodes)})")
    check(text(nodes, "score_value") == str(score + count),
          f"expander clears all {count} dots of the color ({score} -> {text(nodes, 'score_value')})")
    shot("18-after-expander")
    leave_game()

    # Time stop: the clock stands still for 5 seconds.
    nodes = wait_for("mode_timed")
    tap(nodes["mode_timed"])
    time.sleep(2.5)
    one_move()  # the continued game's clock starts with a touch
    nodes = dump()
    if check("powerup_special" in nodes, "timed offers time stop"):
        # Reading the UI takes a second or two, so measure over a longer window:
        # the clock has to lose about 5 s less than the real time that passed.
        have = wallet(nodes)
        clock = nodes["limit_value"]
        before = text(nodes, "limit_value")
        vibrations = our_vibrations()
        t0 = time.time()
        tap(nodes["powerup_special"])
        l, t, r, b = bounds(clock)
        blue = 0
        for _ in range(6):  # the tap can take a moment to arrive on a busy emulator
            img = screencap()
            blue = sum(1 for x in range(l, r, 3) for y in range(t, b, 3)
                       if sum((a - c) ** 2 for a, c in zip(img.getpixel((x, y)), PALETTE[3])) < 3 * 40 ** 2)
            if blue > 20:
                break
            time.sleep(0.5)
        shot("19-time-stop", img)
        nodes = dump()
        check("time_stop_bar" in nodes, "time stop shows the running-out bar")
        if SDK >= 31:
            check(our_vibrations() - vibrations, "time stop vibrates")
        check(blue > 20, f"clock turns blue during the time stop ({blue} blue pixels)")
        while time.time() < t0 + 10:
            time.sleep(0.5)
        nodes = dump()
        elapsed = time.time() - t0
        after = text(nodes, "limit_value")
        check("time_stop_bar" not in nodes, "the bar is gone when the time stop ends")
        if check(before is not None and after is not None, f"clock readable ({before}, {after})"):
            lost = int(before) - int(after)
            check(elapsed - 7 <= lost <= elapsed - 3,
                  f"time stop holds the clock for 5 s: {lost} s lost in {elapsed:.1f} s")
        check(wallet(nodes) == have - 300, f"time stop costs 300 ({have} -> {wallet(nodes)})")
    leave_game()

    # +5 moves.
    nodes = wait_for("mode_moves")
    tap(nodes["mode_moves"])
    time.sleep(2.5)
    nodes = wait_for("game_view")
    if check("powerup_special" in nodes, "moves mode offers +5 moves"):
        have, left = wallet(nodes), text(nodes, "limit_value")
        tap(nodes["powerup_special"])
        time.sleep(1)
        nodes = dump()
        check(left is not None and text(nodes, "limit_value") == str(int(left) + 5),
              f"+5 moves ({left} -> {text(nodes, 'limit_value')})")
        check(wallet(nodes) == have - 300, f"+5 moves costs 300 ({have} -> {wallet(nodes)})")
    leave_game()
    nodes = wait_for("menu_wallet")
    check(re.search(r"\d", text(nodes, "menu_wallet") or "") is not None, f"menu shows the account ({text(nodes, 'menu_wallet')})")


def difficulty_test(normal_best):
    """Hard: 7x7 board with 6 colors and 25 moves, played through. Best scores must be
    kept per difficulty. Easy: 4 colors and 35 moves."""
    nodes = wait_for("difficulty_summary")
    if "difficulty_summary" not in nodes:
        return
    check("Normal" in (text(nodes, "difficulty_summary") or ""), f"default difficulty ({text(nodes, 'difficulty_summary')})")
    nodes = choose_difficulty("difficulty_hard")
    check("Hard" in (text(nodes, "difficulty_summary") or ""), f"switched to hard ({text(nodes, 'difficulty_summary')})")
    check("25" in (text(nodes, "mode_moves") or ""), f"hard: moves button shows 25 ({text(nodes, 'mode_moves')})")
    check("45" in (text(nodes, "mode_timed") or ""), f"hard: timed button shows 45 s ({text(nodes, 'mode_timed')})")
    check(str(normal_best) not in (text(nodes, "best_moves") or ""),
          f"hard has its own best score, not normal's ({text(nodes, 'best_moves')})")
    shot("10-menu-hard")

    tap(nodes["mode_moves"])
    time.sleep(2.5)
    nodes = wait_for("game_view")
    if "game_view" not in nodes:
        return
    check(text(nodes, "limit_value") == "25", f"hard: 25 moves ({text(nodes, 'limit_value')})")
    check((text(nodes, "difficulty_label") or "").lower() == "hard", f"HUD shows difficulty ({text(nodes, 'difficulty_label')})")
    set_board(7, 6)
    shot("11-board-hard")
    hard = play_moves(25, "hard")
    time.sleep(1.5)
    nodes = dump()
    check(text(nodes, "final_score") == str(hard), f"hard: final score {text(nodes, 'final_score')} == {hard}")
    check("Hard" in (text(nodes, "final_best") or ""), f"game over names the difficulty ({text(nodes, 'final_best')})")
    shot("12-gameover-hard")
    sh("input keyevent 4")
    time.sleep(1.5)

    nodes = wait_for("best_moves")
    check(str(hard) in (text(nodes, "best_moves") or ""), f"menu shows hard best {hard} ({text(nodes, 'best_moves')})")
    nodes = choose_difficulty("difficulty_easy")
    check("Easy" in (text(nodes, "difficulty_summary") or ""), f"switched to easy ({text(nodes, 'difficulty_summary')})")
    check("35" in (text(nodes, "mode_moves") or ""), f"easy: moves button shows 35 ({text(nodes, 'mode_moves')})")
    best_easy = text(nodes, "best_moves") or ""
    check(str(hard) not in best_easy and str(normal_best) not in best_easy, f"easy has its own best score ({best_easy})")

    tap(nodes["mode_moves"])
    time.sleep(2.5)
    nodes = wait_for("game_view")
    check(text(nodes, "limit_value") == "35", f"easy: 35 moves ({text(nodes, 'limit_value')})")
    set_board(6, 4)
    grid = read_board(screencap(), geometry(nodes))
    used = {k for row in grid for k in row}
    check(-1 not in used and len(used) <= 4, f"easy: board uses at most 4 colors ({sorted(used)})")
    shot("13-board-easy")
    leave_game()

    # Back to normal: its best score is untouched.
    nodes = choose_difficulty("difficulty_normal")
    check("Normal" in (text(nodes, "difficulty_summary") or ""), "switched back to normal")
    check(str(normal_best) in (text(nodes, "best_moves") or ""),
          f"normal best {normal_best} unchanged ({text(nodes, 'best_moves')})")
    set_board(6, 5)


def launch():
    """Starts the app the way the launcher icon does."""
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")


def crash_log():
    """Crashes of the app only. A crash report starts with "FATAL EXCEPTION" and names the
    process; crashes of other processes (e.g. the uiautomator tool) are ignored."""
    log = adb("logcat", "-d", "-b", "crash").splitlines()
    blocks, current = [], []
    for line in log:
        if "FATAL EXCEPTION" in line and current:
            blocks.append(current)
            current = []
        current.append(line)
    if current:
        blocks.append(current)
    ours = [b for b in blocks if any(f"Process: {PKG}" in l for l in b)]
    others = [b[0] for b in blocks if b not in ours and any("FATAL EXCEPTION" in l for l in b)]
    if others:
        print("ignored crashes of other processes:\n" + "\n".join(others), flush=True)
    return "\n".join(l for b in ours for l in b[:15])


def main():
    print(f"device API {SDK}")
    adb("logcat", "-c")
    out = subprocess.run(["adb", "install", "-r", APK], capture_output=True, text=True)
    check("Success" in out.stdout, f"install: {out.stdout.strip()} {out.stderr.strip()}")
    # Deliberately not like the launcher (as "Open" after installing): coming back via the
    # launcher icon later must still return to the running game.
    sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(2)

    nodes = dump()
    for rid in ("mode_timed", "mode_moves", "mode_endless", "settings_button", "difficulty_summary"):
        check(rid in nodes, f"menu shows {rid}")
    shot("01-menu")

    # --- Moves mode: play all 30 moves ---------------------------------------------
    tap(nodes["mode_moves"])
    time.sleep(2.5)
    nodes = wait_for("game_view")
    if "game_view" not in nodes:
        return finish()
    check(text(nodes, "limit_value") == "30", f"moves start at 30 (got {text(nodes, 'limit_value')})")
    check(text(nodes, "score_value") == "0", "score starts at 0")
    pos = geometry(nodes)
    shot("02-board")

    observed = play_moves(30, "normal", shots=True)

    time.sleep(1.5)
    nodes = dump()
    check("play_again" in nodes, "game over screen shown")
    check(text(nodes, "final_score") == str(observed), f"final score {text(nodes, 'final_score')} == {observed}")
    shot("05-gameover")

    if "play_again" not in nodes:
        return finish()
    tap(nodes["play_again"])
    time.sleep(2.5)
    nodes = wait_for("score_value")
    check(text(nodes, "score_value") == "0" and text(nodes, "limit_value") == "30", "play again resets HUD")
    leave_game()
    nodes = wait_for("best_moves")
    best = text(nodes, "best_moves") or ""
    check(str(observed) in best, f"menu shows best score ({best})")
    shot("06-menu-best")

    # --- Timed mode: clock waits for the first touch ------------------------------
    tap(nodes["mode_timed"])
    time.sleep(3)
    nodes = wait_for("game_view")
    if "game_view" not in nodes:
        return finish()
    check(text(nodes, "limit_value") == "60", f"timer waits for first touch ({text(nodes, 'limit_value')})")
    pos = geometry(nodes)
    grid = read_board(screencap(), pos)
    path = longest_path(grid, limit=2)
    if path:
        drag([pos(r, c) for r, c in path])
    time.sleep(3)
    nodes = dump()
    left = text(nodes, "limit_value")
    check(left is not None and int(left) < 60, f"timer runs after first move ({left})")
    shot("07-timed")

    # Back pauses instead of ending the game (issue #6); the clock stands still.
    sh("input keyevent 4")
    nodes = wait_for("resume")
    frozen = text(nodes, "limit_value")
    shot("14-paused")
    time.sleep(3)
    nodes = dump()
    check("resume" in nodes and text(nodes, "limit_value") == frozen,
          f"back pauses the game and the clock ({frozen} -> {text(nodes, 'limit_value')})")
    if "resume" in nodes:
        tap(nodes["resume"])
    time.sleep(3)
    nodes = dump()
    now = text(nodes, "limit_value")
    check(now is not None and frozen is not None and int(now) < int(frozen), f"clock runs again after resume ({frozen} -> {now})")

    # Leaving the app and coming back continues directly; the clock waits for a touch.
    now = text(dump(), "limit_value")
    for _ in range(3):  # a busy emulator sometimes drops the key press
        sh("input keyevent 3")  # Home
        time.sleep(1.5)
        away = text(dump(), "limit_value")  # None: launcher in front
        if away is None:
            break
    check(away is None, "Home leaves the app")
    # Long enough away that a clock running in the background could not hide in the
    # 1-2 s the emulator needs to read the UI and switch apps.
    time.sleep(8)
    launch()
    time.sleep(2)
    nodes = wait_for("game_view")
    back_at = text(nodes, "limit_value")
    check("resume" not in nodes and "game_view" in nodes, "back in the app: game continues without pause menu")
    check(back_at is not None and now is not None and int(back_at) >= int(now) - 3,
          f"clock stood still while away ({now} -> {back_at}, launcher showed {away})")
    one_move()
    time.sleep(2)
    later = text(dump(), "limit_value")
    check(later is not None and back_at is not None and int(later) < int(back_at),
          f"clock runs again with the next touch ({back_at} -> {later})")
    leave_game()

    # --- Endless mode -------------------------------------------------------------
    nodes = wait_for("mode_endless")
    tap(nodes["mode_endless"])
    time.sleep(2.5)
    shot("08-endless")
    resume_test()
    if SDK >= 29:
        backtrack_test()
    leave_game()

    difficulty_test(normal_best=observed)
    settings_test()
    vibration_test()
    powerup_test()

    crashes = crash_log()
    check(not crashes, "no crash during scripted play" + (f":\n{crashes}" if crashes else ""))
    check_anrs("scripted play")

    # --- Random input stress test ---------------------------------------------------
    # Monkey would abort on any ANR, including ones of System UI, which the slow
    # emulator produces now and then. Timeouts are therefore ignored here and ANRs
    # of our app are detected from the system log instead.
    adb("logcat", "-c")
    monkey = sh(f"monkey -v -p {PKG} --pct-syskeys 0 --ignore-timeouts --throttle 20 -s 1234 5000")
    lines = monkey.splitlines()
    notable = [l for l in lines if re.search(r"CRASH|NOT RESPONDING|aborted|Exception|Error", l)]
    check("Monkey finished" in monkey and not any("CRASH" in l for l in lines),
          "monkey: 5000 random events without crash\n" + "\n".join((notable + lines[-3:])[:20]))
    crashes = crash_log()
    check(not crashes, "no crash after monkey" + (f":\n{crashes}" if crashes else ""))
    check_anrs("monkey")

    return finish()


def finish():
    # Annotations are cut at 4 KB: list failures and the non-repetitive checks only.
    shown = [r for r in results if not re.match(r"ok   \w+ move \d+", r)]
    total = sum(1 for r in results if not r.startswith("info "))
    annotate("notice", f"API {SDK}: {total - len(failures)}/{total} checks passed\n" + "\n".join(shown))
    return 1 if failures else 0


if __name__ == "__main__":
    try:
        code = main()
    except Exception:
        annotate("error", f"API {SDK}: script crashed\n{traceback.format_exc()}")
        finish()
        code = 1
    sys.exit(code)
