#!/usr/bin/env python3
"""End-to-end check on an emulator: installs the APK, plays a full "moves" game
by reading the dot colors from screenshots, checks score/HUD/game-over, then
runs a monkey stress test and fails on any crash.

Usage: play.py <apk> <output-dir>

Screenshots are written to <output-dir> and additionally printed to the log as
base64 (lines starting with @@) so they can be inspected from the job log.
"""
import base64
import io
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from PIL import Image

PKG = "com.psiqos.spheres"
APK, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)

# Must match game/Palette in GameView.kt.
PALETTE = [(0xEC, 0x5B, 0x57), (0xF4, 0xC8, 0x42), (0x83, 0xD6, 0x6A), (0x5C, 0xA8, 0xEC), (0x9E, 0x6C, 0xDB)]
ROWS = COLS = 6

failures = []


def check(cond, msg):
    print(("ok   " if cond else "FAIL ") + msg, flush=True)
    if not cond:
        failures.append(msg)
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
    small = img.copy()
    small.thumbnail((360, 800))
    buf = io.BytesIO()
    small.quantize(64).save(buf, "PNG", optimize=True)
    data = base64.b64encode(buf.getvalue()).decode()
    print(f"@@SHOT {name}")
    for i in range(0, len(data), 8000):
        print("@@B64 " + data[i:i + 8000])
    print("@@END", flush=True)


def dump():
    for _ in range(5):
        out = sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml")
        if "<?xml" in out:
            root = ET.fromstring(out[out.index("<?xml"):])
            nodes = {}
            for n in root.iter("node"):
                rid = n.get("resource-id", "")
                if rid.startswith(PKG + ":id/"):
                    nodes[rid.split("/")[-1]] = n
            return nodes
        time.sleep(1)
    return {}


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
            d = [sum((a - b) ** 2 for a, b in zip(px, p)) for p in PALETTE]
            k = min(range(len(PALETTE)), key=d.__getitem__)
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


def crash_log():
    log = adb("logcat", "-d", "-b", "crash")
    return "\n".join(l for l in log.splitlines() if PKG in l or "FATAL" in l)


def main():
    print(f"device API {SDK}")
    adb("logcat", "-c")
    out = subprocess.run(["adb", "install", "-r", APK], capture_output=True, text=True)
    check("Success" in out.stdout, f"install: {out.stdout.strip()} {out.stderr.strip()}")
    sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(2)

    nodes = dump()
    for rid in ("mode_timed", "mode_moves", "mode_endless", "sound_toggle"):
        check(rid in nodes, f"menu shows {rid}")
    shot("01-menu")

    # --- Moves mode: play all 30 moves ---------------------------------------------
    tap(nodes["mode_moves"])
    time.sleep(2.5)
    nodes = dump()
    check(text(nodes, "limit_value") == "30", f"moves start at 30 (got {text(nodes, 'limit_value')})")
    check(text(nodes, "score_value") == "0", "score starts at 0")
    pos = geometry(nodes)
    shot("02-board")

    expected = 0
    squares = 0
    took_drag_shot = False
    for move in range(30):
        grid = None
        for _ in range(4):
            grid = read_board(screencap(), pos)
            if all(k >= 0 for row in grid for k in row):
                break
            time.sleep(0.5)
        if not check(all(k >= 0 for row in grid for k in row), f"move {move + 1}: all dots recognized"):
            shot(f"err-board-{move + 1}")
            break

        square = find_square(grid) if SDK >= 29 else None
        path = square or longest_path(grid, limit=7 if SDK >= 29 else 2)
        if len(path) < 2:
            check(False, f"move {move + 1}: no move found on board {grid}")
            break
        color = grid[path[0][0]][path[0][1]]
        gain = sum(row.count(color) for row in grid) if square else len(path)

        hold = None
        if square and squares == 0:
            hold = "03-square"
        elif not square and len(path) >= 4 and not took_drag_shot:
            hold, took_drag_shot = "03-drag", True
        drag([pos(r, c) for r, c in path], hold=hold)
        squares += 1 if square else 0
        expected += gain
        time.sleep(1.0)

        if move == 14:
            shot("04-midgame")
        if move < 29:
            nodes = dump()
            check(text(nodes, "score_value") == str(expected),
                  f"move {move + 1} ({'square' if square else f'{len(path)} dots'}): "
                  f"score {text(nodes, 'score_value')} == {expected}")
            check(text(nodes, "limit_value") == str(29 - move),
                  f"move {move + 1}: moves left {text(nodes, 'limit_value')} == {29 - move}")
    print(f"played moves, {squares} squares, expected score {expected}")

    time.sleep(1.5)
    nodes = dump()
    check("play_again" in nodes, "game over screen shown")
    check(text(nodes, "final_score") == str(expected), f"final score {text(nodes, 'final_score')} == {expected}")
    shot("05-gameover")

    if "play_again" not in nodes:
        return finish()
    tap(nodes["play_again"])
    time.sleep(2.5)
    nodes = dump()
    check(text(nodes, "score_value") == "0" and text(nodes, "limit_value") == "30", "play again resets HUD")
    sh("input keyevent 4")
    time.sleep(1.5)
    nodes = dump()
    best = text(nodes, "best_moves") or ""
    check(str(expected) in best, f"menu shows best score ({best})")
    shot("06-menu-best")

    # --- Timed mode: clock waits for the first touch ------------------------------
    tap(nodes["mode_timed"])
    time.sleep(3)
    nodes = dump()
    check(text(nodes, "limit_value") == "60", f"timer waits for first touch ({text(nodes, 'limit_value')})")
    pos = geometry(nodes)
    grid = read_board(screencap(), pos)
    path = longest_path(grid, limit=2)
    if path:
        drag([pos(r, c) for r, c in path])
    time.sleep(3)
    nodes = dump()
    left = int(text(nodes, "limit_value") or 60)
    check(left < 60, f"timer runs after first move ({left})")
    shot("07-timed")
    sh("input keyevent 4")
    time.sleep(1)

    # --- Endless mode -------------------------------------------------------------
    nodes = dump()
    tap(nodes["mode_endless"])
    time.sleep(2.5)
    shot("08-endless")
    sh("input keyevent 4")
    time.sleep(1)

    crashes = crash_log()
    check(not crashes, "no crash during scripted play" + (f":\n{crashes}" if crashes else ""))

    # --- Random input stress test ---------------------------------------------------
    monkey = sh(f"monkey -p {PKG} --pct-syskeys 0 --throttle 20 -s 1234 5000")
    check("Monkey finished" in monkey and "CRASH" not in monkey, "monkey: 5000 random events without crash")
    crashes = crash_log()
    check(not crashes, "no crash after monkey" + (f":\n{crashes}" if crashes else ""))

    return finish()


def finish():
    print(f"\n{len(failures)} failure(s)")
    for f in failures:
        print("  - " + f)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
