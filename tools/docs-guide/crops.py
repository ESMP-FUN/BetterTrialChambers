# Cut the docs pictures out of the full in-game shots (full-*.png, 1280x800 at GUI scale 2).
#   python3 tools/docs-guide/crops.py <folder with full-*.png> docs/.gitbook/assets
# The boxes are (left, top, right, bottom) in the full shot. Chat boxes depend on the number of
# chat lines and menu boxes on the GUI scale, so check them after every retake (see SHOTS.md).
import sys
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
FULL = None
GUI6 = (462, 176, 824, 430)  # a six-row menu, without the inventory under it
GUI5 = (462, 196, 824, 416)  # a five-row menu

CROPS = {
    "setup-reminder.png": ("full-reminder.png", (0, 662, 680, 701)),
    "tour-discovery.png": ("full-tour-discovery.png", (320, 0, 960, 700)),
    "tour-auto-snapshot.png": ("full-tour-auto-snapshot.png", (320, 0, 960, 645)),
    "tour-fawe-missing.png": ("full-tour-fawe-missing.png", (320, 0, 960, 640)),
    "tour-reset-interval.png": ("full-tour-reset-interval.png", (320, 0, 960, 690)),
    "tour-loot-mode.png": ("full-tour-loot-mode.png", (320, 0, 960, 628)),
    "tour-complete.png": ("full-tour-complete.png", (320, 0, 960, 800)),
    "discovery-message.png": ("full-discovery.png", (0, 683, 620, 726)),
    "list-current.png": ("full-list-current.png", (0, 692, 680, 726)),
    "chamber-info.png": ("full-chamber-info.png", (0, 553, 670, 726)),
    "player-boss-bar.png": ("full-boss-bar.png", FULL),
    "hero-chamber.png": ("full-boss-bar.png", FULL),
    "player-vault-opened.png": ("full-vault-opened.png", FULL),
    "player-vault-locked.png": ("full-vault-locked.png", FULL),
    "player-cannot-place.png": ("full-cannot-place.png", (0, 300, 1280, 800)),
    "menu-main.png": ("full-menu-main.png", GUI6),
    "menu-chambers.png": ("full-menu-chambers.png", (462, 176, 912, 480)),
    "menu-chamber.png": ("full-menu-chamber.png", GUI6),
    "rename-prompt.png": ("full-rename-prompt.png", (0, 683, 660, 724)),
    "rename-done.png": ("full-rename-done.png", (0, 655, 660, 724)),
    "menu-settings.png": ("full-menu-settings.png", GUI5),
    "menu-reset-interval.png": ("full-menu-reset-interval.png", (462, 196, 910, 416)),
    "menu-chamber-protection.png": ("full-menu-chamber-protection.png", (462, 200, 1050, 462)),
    "menu-protection.png": ("full-menu-protection.png", (462, 200, 824, 414)),
    "loot-pools.png": ("full-loot-pools.png", GUI6),
    "loot-editor.png": ("full-loot-editor.png", GUI6),
    "loot-item-tooltip.png": ("full-loot-item-tooltip.png", (462, 176, 880, 452)),
    "reset-done.png": ("full-reset-done.png", (0, 664, 560, 724)),
    "stats-chat.png": ("full-stats-chat.png", (0, 592, 350, 724)),
    "leaderboard-chat.png": ("full-leaderboard-chat.png", (0, 682, 360, 724)),
    "menu-leaderboard.png": ("full-menu-leaderboard.png", GUI6),
    "hand-generate.png": ("full-hand-generate.png", (0, 505, 660, 724)),
}

for name, (source, box) in CROPS.items():
    im = Image.open(f"{src}/{source}").convert("RGB")
    (im.crop(box) if box else im).save(f"{dst}/{name}", optimize=True)
print(f"cut {len(CROPS)} pictures")
