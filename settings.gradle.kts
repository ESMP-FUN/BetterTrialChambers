rootProject.name = "BetterTrialChambers"

// The shared code and one adapter per group of Minecraft versions that behave differently.
// The root project packs them into the one jar that ships.
include("common", "adapter-v26_3", "adapter-v26_4")
