"""Drive the hidden test client on display :5 (see test-servers/test-client/README.md).
  drive.py move X Y | click X Y [right|shift|shiftright] | scroll up|down N
  drive.py use (right click in place) | key NAME | combo KEY KEY (F3 d clears chat) | type TEXT | say TEXT (opens chat, types, Enter) | shot FILE
Unlike the shared drive.py, type handles capitals and punctuation."""
import subprocess, sys, time
from Xlib import X, XK, display
from Xlib.ext import xtest
d = display.Display(':5')
SHIFT = d.keysym_to_keycode(XK.string_to_keysym('Shift_L'))
NAMES = {'/': 'slash', ' ': 'space', '.': 'period', '-': 'minus', ',': 'comma', "'": 'apostrophe'}
SHIFTED = {'_': 'minus', ':': 'semicolon', '!': '1', '?': 'slash'}
def sync(t=0.05): d.sync(); time.sleep(t)
def press(code, shift=False):
    if shift: xtest.fake_input(d, X.KeyPress, SHIFT)
    xtest.fake_input(d, X.KeyPress, code); sync(0.02); xtest.fake_input(d, X.KeyRelease, code)
    if shift: xtest.fake_input(d, X.KeyRelease, SHIFT)
    sync(0.03)
def typ(text):
    for ch in text:
        if ch in SHIFTED: press(d.keysym_to_keycode(XK.string_to_keysym(SHIFTED[ch])), True)
        elif ch.isupper(): press(d.keysym_to_keycode(XK.string_to_keysym(ch.lower())), True)
        else: press(d.keysym_to_keycode(XK.string_to_keysym(NAMES.get(ch, ch))))
def key(name): press(d.keysym_to_keycode(XK.string_to_keysym(name)))
cmd, *a = sys.argv[1:]
if cmd == 'move':
    xtest.fake_input(d, X.MotionNotify, x=int(a[0]), y=int(a[1])); sync()
elif cmd == 'scroll':
    b = 4 if a[0] == 'up' else 5
    for _ in range(int(a[1]) if len(a) > 1 else 1):
        xtest.fake_input(d, X.ButtonPress, b); xtest.fake_input(d, X.ButtonRelease, b); sync(0.15)
elif cmd == 'click':
    mode = a[2] if len(a) > 2 else ''
    xtest.fake_input(d, X.MotionNotify, x=int(a[0]), y=int(a[1])); sync(0.1)
    if 'shift' in mode: xtest.fake_input(d, X.KeyPress, SHIFT); sync()
    b = 3 if 'right' in mode else 1
    xtest.fake_input(d, X.ButtonPress, b); sync(); xtest.fake_input(d, X.ButtonRelease, b); sync()
    if 'shift' in mode: xtest.fake_input(d, X.KeyRelease, SHIFT); sync()
elif cmd == 'use':
    # Right click without moving the pointer, so the camera stays where a teleport aimed it.
    xtest.fake_input(d, X.ButtonPress, 3); sync(); xtest.fake_input(d, X.ButtonRelease, 3); sync()
elif cmd == 'key':
    key(a[0])
elif cmd == 'combo':
    codes = [d.keysym_to_keycode(XK.string_to_keysym(n)) for n in a]
    for c in codes: xtest.fake_input(d, X.KeyPress, c); sync()
    for c in reversed(codes): xtest.fake_input(d, X.KeyRelease, c); sync()
elif cmd == 'type':
    typ(a[0])
elif cmd == 'say':
    key('t'); time.sleep(0.4); typ(a[0]); time.sleep(0.2); key('Return')
elif cmd == 'shot':
    subprocess.run(['import', '-display', ':5', '-window', 'root', a[0]], check=True)
