# Multiverse Heroes: Stage 1 (Homelander)

Standalone Forge 1.20.1 mod (Java 17). Built by GitHub Actions; the JAR appears as the `multiverse-jar` artifact.

## Build on GitHub
1. Push this folder to a new repository (or upload it through the GitHub web UI).
2. Open the Actions tab, pick "Build mod", and wait for the run to finish.
3. Download `multiverse-jar` from the run's Artifacts section. Put the JAR in your `mods` folder with Forge 1.20.1 (47.3.0).

## Stage 1 contents
- Homelander suit: chestplate, leggings and boots (vanilla armor layer, textured navy with red piping, gold belt, gold stars on the gloves, red boots).
- Suit-only render layer: one large gold pauldron on the right shoulder, glowing eyes (blue at rest, fading to red while charging the laser), and a cloth cape with the striped flag pattern.
- Flight: double-jump in the air toggles flight while wearing the full set. Speed is raised, and the body leans forward more at higher speed (hover, slow, fast).
- Laser Vision (Z): hold to fire. Damages targets in line of sight. Burnout starts after 60 seconds of holding.
- Clap (X): stuns a player within 2 blocks for 4 seconds and deals 4 hearts. Otherwise it creates a 3-heart shockwave and breaks glass.
- Launch (C): uppercut, then a flight meeting and a slam (18 hearts to the target). Cooldown 30 s.
- Grab (R): holds a target in front. Hold R to strangle (2 hearts every 2 seconds). Tap R to release. Players must type the 8 letters from W, A, S, D, E, R in order to break free.
- Tear-A rules: 4x damage reduction taken, 3.5-heart fist hits, +40 max health (3 rows), no fall damage, fire and poison immunity, projectiles reverse off the suit.
- Ability HUD in the bottom-right corner, showing key, name and local cooldown.

## Keys (rebindable in Controls > Multiverse Heroes)
Z Laser · X Clap · C Launch · R Grab · Y Helmet (reserved for Soldier Boy)

## Not in Stage 1 (planned for Stage 2)
- Rage state, red eye glow for the whole effect, Super Speed (freezing other entities, trail), and Rage-state abilities.
- Burnout's full kneel animation and red outline glow. Currently a lean-forward approximation.
- Block cracks from Launch and Rage dashes. Stage 1 does damage only.
- LMB throw of grabbed targets.
- PlayerAnimator poses and GeckoLib models. Stage 1 uses code-built geometry.
- Full momentum-based flight, banking and camera work. Stage 1 uses vanilla movement with a speed boost.
- Sound and sustained particle effects.

## Known approximations
- Textures are generated programmatically. Replace `src/main/resources/assets/multiverse/textures/...` PNGs with better art without changing code.
- Stuns and grabs are enforced on the server. Clients may still show movement briefly before the correction.
- Projectile "bounce" reverses the projectile and cancels the hit, so it is not a true reflection.
- Eye charge is only shown on your own character. Other players don't see the charge until it's synced in Stage 2.

## First-build checklist
- Build succeeds and the JAR is produced.
- Game loads with the mod listed, and the Homelander items appear in the creative inventory (search "Homelander").
- Wearing all three pieces: the pauldron, eyes and cape appear.
- Double-jump in the air starts flying. Fast flight leans the body forward.
- Each ability key triggers its effect, and the HUD shows cooldowns.
