package org.pharos.app.data

data class SurvivalGuideEntry(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val criticalSteps: List<String>,
    val exactFormulasOrRatios: String
)

object SurvivalCodexRepository {

    val categories = listOf(
        "All",
        "First 60m",
        "Medical",
        "Water",
        "Fire & Shelter",
        "Nuclear & Gas",
        "Navigation",
        "Power & Signals",
        "Disasters"
    )

    val entries: List<SurvivalGuideEntry> = listOf(
        SurvivalGuideEntry(
            id = "grid_down_60m",
            category = "First 60m",
            title = "Blackout Hour-0 Immediate Protocol",
            summary = "What to do in the first 60 minutes when power, cell towers, and water fail simultaneously.",
            criticalSteps = listOf(
                "1. Capture Gravity Water Immediately (Minutes 0–30): Municipal water pipes retain gravity tower pressure for 15–45 minutes after pumps fail. Fill bathtubs, pots, and every clean container immediately.",
                "2. Phone Battery Preservation: Switch to Airplane Mode, lower brightness to 15%, enable PHAROS Red Mode, and rely on passive 15-minute Firefly wake bursts (:00, :15, :30, :45).",
                "3. Fridge & Freezer Lock: Keep doors sealed (a full freezer holds safe temperature for 48 hours unopened; eat perishables first, then canned goods).",
                "4. Gas & Carbon Monoxide Safety: Never run generators, charcoal, or camp stoves indoors or near windows. Only shut off the main street gas valve if you smell gas (rotten eggs) or hear hissing.",
                "5. Establish Rally Times: Agree with family/neighbors on a physical meeting point and fixed radio/light check-in windows on the hour."
            ),
            exactFormulasOrRatios = "Rule of 3s: 3 min without air/bleeding control • 3 hrs without shelter in extreme cold/heat • 3 days without water • 3 weeks without food."
        ),
        SurvivalGuideEntry(
            id = "trauma_march",
            category = "Medical",
            title = "MARCH Trauma: Bleeding, Airway & Chest Wounds",
            summary = "Stop life-threatening arterial bleeding, clear airways, and seal penetrating chest injuries.",
            criticalSteps = listOf(
                "1. Massive Bleeding (M): Apply hard, direct two-handed pressure with cloth. For spurting bright-red limb bleeding, apply a Tourniquet 5–8 cm (2–3 inches) ABOVE the wound (never over a joint). Twist windlass rod until bleeding stops completely, lock it, and write 'TK + Time' on the forehead.",
                "2. Airway (A): If unconscious and breathing, roll onto their side (Recovery Position) so the tongue and blood/vomit drain out rather than blocking the windpipe.",
                "3. Respiration / Sucking Chest Wound (R): If a chest puncture bubbles or whistles, cover it during an exhale with airtight plastic (bag/wrapper) taped on 3 SIDES ONLY. The untaped 4th side acts as a flutter valve letting trapped chest air escape.",
                "4. Wound Irrigation (Prevent Sepsis): Flush deep cuts under pressure (poke a pinhole in a clean plastic bottle) using cooled boiled water mixed with 0.9% salt before bandaging."
            ),
            exactFormulasOrRatios = "Sterile Saline Irrigation Ratio: 500 mL (2 cups) boiled water + 1 level teaspoon (4.5g) non-iodized salt.\nWHO Oral Rehydration Solution (Shock/Dehydration): 1 Liter clean water + 6 level teaspoons sugar + 1/2 level teaspoon salt."
        ),
        SurvivalGuideEntry(
            id = "hypothermia_burns",
            category = "Medical",
            title = "Hypothermia, Heatstroke, Burns & Fractures",
            summary = "Stabilize core temperature emergencies, severe burns, and broken bones without a hospital.",
            criticalSteps = listOf(
                "1. Severe Hypothermia: Remove wet clothes immediately (cut if needed). Insulate underneath from the ground first. Place warm water bottles in armpits and groin (arterial zones). Do NOT rub frostbitten skin or force alcohol.",
                "2. Heatstroke (Core > 40°C / Confusion / No Sweat): Pour cool water over neck, scalp, armpits, and groin while fanning rapidly to maximize evaporative cooling.",
                "3. Severe Burns: Cool with clean running water for 15 minutes (never ice). Remove rings/watches before swelling starts. Cover loosely with clean plastic wrap or non-stick dressing; give WHO Oral Rehydration Solution continuously.",
                "4. Fractures: Pad splints (sticks, rolled cardboard, trekking poles) with cloth and immobilize BOTH the joint above and the joint below the break. Check finger/toe warmth every 15 minutes."
            ),
            exactFormulasOrRatios = "Burn Fluid Replacement (First 24h): Give ~4 mL of Oral Rehydration Solution per kg of body weight per % of body surface burned (half given in the first 8 hours)."
        ),
        SurvivalGuideEntry(
            id = "water_purification",
            category = "Water",
            title = "Water Procurement, Filtering & Exact Disinfection",
            summary = "Locate hidden water, build a 4-stage charcoal bio-filter, and disinfect with exact ratios.",
            criticalSteps = listOf(
                "1. Safe Sources (Best to Worst): Rainwater/snow (melt snow before eating to prevent core heat loss) -> Morning dew soaked up from ankle-high grass with a cloth -> Seepage hole dug 50 cm behind the outside bend of a dry riverbed -> Flowing streams -> Stagnant ponds.",
                "2. 4-Stage Improvised Bottle Filter: Cut bottom off a bottle, invert it, and layer from bottom (neck) to top: (1) Cloth plug, (2) 5 cm crushed hardwood charcoal from a cooled fire (absorbs chemicals/toxins), (3) 5 cm fine sand, (4) 5 cm coarse gravel. Filter first, then ALWAYS boil or disinfect!",
                "3. Boiling (Gold Standard): Bring to a rolling boil for 1 full minute (3 minutes above 2,000m altitude).",
                "4. Solar UV (SODIS): Fill a clear PET plastic bottle (<= 2L), shake to oxygenate, and lay flat on a metal roof in direct sun for 6 hours.",
                "5. Saltwater / Contaminated Water Distillation: Never drink seawater or urine. Boil in a pot with an inverted lid so steam condenses on the center handle and drips into a floating cup."
            ),
            exactFormulasOrRatios = "Unscented Household Bleach (5%–6% NaOCl):\n• Clear water: 2 drops per 1 Liter (8 drops per Gallon).\n• Cloudy/Cold water: 4 drops per 1 Liter (16 drops per Gallon).\n• Wait 30 minutes (60 min if icy). Must have a faint chlorine smell."
        ),
        SurvivalGuideEntry(
            id = "shelter_fire",
            category = "Fire & Shelter",
            title = "Insulated Shelter & Starting Fire in Pouring Rain",
            summary = "Stop conductive ground heat loss and ignite wet-weather fires with or without matches.",
            criticalSteps = listOf(
                "1. Ground Insulation First: Cold/wet ground conducts heat away 25x faster than air. Always build a 15 cm (6-inch) compressed bed of dry leaves, pine needles, or cardboard UNDER your body before building a roof.",
                "2. Debris A-Frame / Urban Tent-in-Room: Keep shelter barely larger than your body so your own body heat warms the air. Indoors during a winter grid failure, pitch a tent on a mattress in the smallest south-facing interior room and blanket the windows.",
                "3. Finding Dry Wood in Pouring Rain: Never pick wood off the wet ground. Snap dead branches still attached to standing trees, then split wrist-thick pieces down the middle to expose bone-dry inner heartwood.",
                "4. Feather Sticks & Resin: Shave thin curls into dry inner heartwood sticks. Harvest sticky pine pitch/resin blisters or papery birch bark (burns even when wet).",
                "5. Battery Ignition: Touch a strip of steel wool or a foil-backed chewing gum wrapper (cut narrow in the center) across the + and - terminals of a 9V, AA, or salvaged battery to ignite dry tinder."
            ),
            exactFormulasOrRatios = "Shelter Rule: 15 cm (6 in) compressed ground bed underneath = equivalent to a -5°C sleeping pad. Roof debris thickness for rain shedding = 60 cm (24 in) at a 45° slope."
        ),
        SurvivalGuideEntry(
            id = "cbrn_nuclear_gas",
            category = "Nuclear & Gas",
            title = "Nuclear Fallout (7-10 Rule) & Chemical Gas Plumes",
            summary = "Exact sheltering geometry, fallout decay math, decontamination, and upwind chemical escape.",
            criticalSteps = listOf(
                "1. Detonation Flash: Drop face-down immediately behind hard cover, thumbs in ears, mouth slightly open (prevents eardrum/lung rupture from blast wave). Do not look at the fireball.",
                "2. The 15-Minute Window: Lethal radioactive fallout (pulverized sand/dust) takes 10–20 minutes to start falling. Get into an underground basement or the middle floors of a multi-story concrete building (away from outer walls and the roof).",
                "3. Decontamination: If dust falls on you outside, remove outer clothes outside (removes 90% of radiation). Wash skin/hair with soap and water—NEVER use hair conditioner (it binds radioactive particles to hair).",
                "4. Chemical Plumes (OPPOSITE of Nuclear!): Most toxic industrial gases (Chlorine, Phosgene, Hydrogen Sulfide, Propane) are HEAVIER than air and sink into basements and valleys. Move UPWIND and UPHILL (or to top floors), seal vents with wet towels/tape, and breathe through an 8-layer cloth dampened with baking soda water."
            ),
            exactFormulasOrRatios = "Nuclear 7-10 Decay Rule (Stay inside 48–72 hours!):\n• 1 hr = 100% radiation rate\n• 7 hrs = 10% rate\n• 49 hrs (2 days) = 1% rate\n• 2 weeks = 0.1% rate\nShielding Halving Thickness: 6 cm concrete = 9 cm packed earth = 18 cm water."
        ),
        SurvivalGuideEntry(
            id = "navigation_no_gps",
            category = "Navigation",
            title = "Zero-Tech Sun, Shadow & Star Navigation",
            summary = "Find True North and navigate accurately without a compass, GPS, or electronics.",
            criticalSteps = listOf(
                "1. Universal Shadow-Tip Method (Works anywhere on Earth): Plant a straight 1m stick vertically in flat ground. Mark the tip of its shadow with a pebble (Mark 1 = ALWAYS WEST). Wait 15–20 minutes and mark the new shadow tip (Mark 2 = ALWAYS EAST). Stand with West on your left and East on your right: you are facing True North.",
                "2. Analog Watch Method (Northern Hemisphere): Hold watch flat and point the Hour Hand at the Sun. Bisect (find the halfway angle between) the Hour Hand and 12 o'clock -> that line points True South. (In Southern Hemisphere: point 12 o'clock at the Sun; halfway to the Hour Hand is True North).",
                "3. Night Stars (Northern): Follow the two outer pointer stars at the front of the Big Dipper's bowl 5x their separation distance to Polaris (True North).",
                "4. Night Stars (Southern): Extend the long bar of the Southern Cross (Crux) 4.5x its length and drop straight down to the horizon for True South."
            ),
            exactFormulasOrRatios = "Distance Estimation on Foot: Average pace count on flat terrain = 65–70 double-steps (counting every time your right foot hits) per 100 meters. Add 1 minute of travel time per 10 meters of elevation gain."
        ),
        SurvivalGuideEntry(
            id = "power_faraday_signals",
            category = "Power & Signals",
            title = "Battery Scavenging, Faraday Shield & Rescue Codes",
            summary = "Charge 5V phones from 12V car/AA batteries, build an EMP shield, and signal aircraft.",
            criticalSteps = listOf(
                "1. Charging Phones from 12V Car / Truck Batteries: Never wire 12V directly to a 5V phone! Use a 12V car cigarette-lighter USB adapter (wire the center tip of the adapter to Car +12V and the side metal springs to Car -GND).",
                "2. Charging from AA / D Cells: Tape 4 alkaline 1.5V cells in series (+ to -) to produce 6.0V (drops to safe ~5.1V under phone load). USB Pinout (looking into standard USB-A socket, contacts down): Far Right Pin 1 = +5V (Red), Far Left Pin 4 = GND (Black).",
                "3. Improvised EMP / Solar Storm Faraday Shield: Wrap spare electronics in a plastic bag or cardboard (insulator), then wrap tightly in 3 separate layers of heavy-duty aluminum foil (or place inside a metal ammo/trash can without touching the metal walls).",
                "4. Universal Distress Signals: Anything in 3s (3 whistle blasts, 3 fires in a triangle 30m apart, 3 mirror flashes). Aim a signal mirror by sighting the target through a center hole or V-fingers."
            ),
            exactFormulasOrRatios = "Ground-to-Air Giant Visual Symbols (Make 6m / 20ft tall in high contrast):\n• V = Require Assistance\n• X = Require Medical Evacuation\n• N = No / Negative\n• Y = Yes / Affirmative\n• ↑ = Proceeding in this direction"
        ),
        SurvivalGuideEntry(
            id = "disasters_wildfire_flood",
            category = "Disasters",
            title = "Wildfire, Flash Flood, Earthquake & Urban Evasion",
            summary = "Escape fast-moving wildfires, submerged vehicles, flash floods, and urban checkpoints.",
            criticalSteps = listOf(
                "1. Wildfire Escape: Fires race UPHILL and DOWNWIND up to 20+ km/h. Never try to outrun a wildfire uphill! Move downhill and perpendicular to the wind toward already-burned black ash ('the black'), rocky scree, or wide shallow water. Breathe low through a wet cotton cloth (never synthetic polyester).",
                "2. Submerged / Sinking Car (First 45 Seconds): Unbuckle seatbelts FIRST -> Roll or break SIDE tempered windows at the bottom corner (never the laminated front windshield) -> Children out first -> Yourself.",
                "3. Flash Floods: 15 cm (6 in) of fast-moving water knocks an adult down; 45 cm (18 in) sweeps an SUV away. Climb straight up to high ground; never cross flowing water above your ankles.",
                "4. Urban Unrest & Evasion (Gray Man): Wear plain, drab civilian work clothes (never camo or tactical vests). Move during heavy rain or 45 minutes before dawn when streets are empty. Avoid bridges, chokepoints, and crowd centers; keep 1-line paper backups of contacts and OTP codes."
            ),
            exactFormulasOrRatios = "Lightning 30-30 Rule: Count seconds between flash and thunder; divide by 3 for distance in km (divide by 5 for miles). If <= 30 seconds (10 km), crouch on the balls of your feet (heels touching, hands over ears, never lie flat on the ground) in a low depression away from tall isolated trees."
        )
    )
}
