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
        "Food & Knots",
        "Nuclear & CBRN",
        "Navigation",
        "Power & Radio",
        "Disasters",
        "Biomes & Urban"
    )

    val entries: List<SurvivalGuideEntry> = listOf(
        SurvivalGuideEntry(
            id = "grid_down_60m",
            category = "First 60m",
            title = "1. Blackout Hour-0 Immediate Protocol",
            summary = "What to do in the first 60 minutes when power, cell towers, and water fail simultaneously.",
            criticalSteps = listOf(
                "1. Capture Gravity Water Immediately (Minutes 0–30): Municipal water pipes retain gravity tower pressure for 15–45 minutes after pumps fail. Scrub and fill bathtubs (150–300 L), sinks, pots, and every jug before pressure drops and back-siphoning contaminates lines.",
                "2. Drain Hidden Home Water (120–220 Liters): Turn off the breaker/gas to your hot water heater tank, open a hot faucet upstairs to break the vacuum, and drain clean potable water from the bottom brass valve. Also use upper toilet tank reservoirs (never the bowl, and only if free of blue chemical cleaners).",
                "3. Phone Battery Lockdown: A phone searching for dead cell towers drains battery 4x faster. Enable Airplane Mode, set brightness to 15%, turn on PHAROS Red Mode, and rely on passive 15-minute Firefly wake bursts (:00, :15, :30, :45).",
                "4. Freezer & Gas Discipline: Keep refrigerator/freezer doors shut (a full unopened freezer stays frozen 48 hours; drape blankets over the outside). Do NOT shut off your street gas valve unless you smell gas (rotten eggs) or hear hissing.",
                "5. Carbon Monoxide (CO) Prevention: Never run generators, charcoal grills, or camp stoves inside a home, basement, or garage—even with windows cracked."
            ),
            exactFormulasOrRatios = "Rule of 3s: 3 sec composure • 3 min air/bleeding • 3 hrs extreme weather shelter • 3 days water • 3 weeks food."
        ),
        SurvivalGuideEntry(
            id = "trauma_march",
            category = "Medical",
            title = "2. MARCH Trauma: Arterial Bleeding, Airway & Chest Seals",
            summary = "Stop life-threatening arterial hemorrhage, clear airways, and seal sucking chest wounds.",
            criticalSteps = listOf(
                "1. Massive Bleeding (M): Press hard with both hands directly on the bleeding vessel using gauze or clean cotton cloth. For deep groin, armpit, or neck wounds where a tourniquet cannot fit, pack cloth tightly finger-by-finger into the wound cavity and hold firm pressure for 3 full minutes.",
                "2. Windlass Tourniquet (Limbs): For bright-red spurting blood on an arm or leg, place a 5 cm (2-inch) wide cloth band 5–8 cm (2–3 inches) ABOVE the wound (never over a joint). Tie a half-knot, place a strong stick on top, tie a square knot over it, twist the stick until bleeding stops completely, secure the stick, and write 'TK + Exact Time' on the forehead.",
                "3. Airway (A): If unconscious and breathing, roll the person onto their side (Recovery Position—top leg bent 90°, head tilted slightly back/down) so blood or vomit drains out instead of suffocating them.",
                "4. Sucking Chest Wound (R): If a chest puncture bubbles or hisses, wipe the skin dry and press airtight plastic (bag/wrapper) over the hole at the end of an EXHALE. Tape 3 sides tightly, leaving the bottom corner untaped as a one-way flutter valve so trapped chest air escapes."
            ),
            exactFormulasOrRatios = "Never use thin wire, cord, or shoelaces as a tourniquet (cuts tissue without compressing deep arteries; band must be >= 4–5 cm wide)."
        ),
        SurvivalGuideEntry(
            id = "medical_solutions_pharmacy",
            category = "Medical",
            title = "3. Austere Field Pharmacy: Rehydration & Antiseptic Formulas",
            summary = "Exact chemical recipes for life-saving oral rehydration, sterile wound saline, and Dakin's antiseptic.",
            criticalSteps = listOf(
                "1. Prevent Dehydration / Burn / Diarrheal Shock: The gut requires glucose (sugar) and sodium (salt) in an exact 1:1 molar ratio to pull water into the bloodstream. Mix WHO Oral Rehydration Solution (ORS) and sip continuously.",
                "2. High-Pressure Wound Irrigation (Prevent Sepsis): Never close a dirty outdoor wound. Boil water for 1–3 minutes, add 1 level teaspoon of non-iodized salt per 500 mL, let cool to lukewarm, poke a 1.5 mm pinhole in a plastic bottle cap, and jet-flush all grit out of the wound.",
                "3. Dakin's Antiseptic Solution (For Infected Wounds): When antibiotics are unavailable, dilute unscented 5% household bleach with boiled water and baking soda (see exact ratio below) to kill MRSA, Pseudomonas, and anaerobic bacteria without destroying healthy tissue.",
                "4. Severe Dental Pain / Abscess: Rinse with warm hypertonic salt water (1 tsp salt in 250 mL water) every 2 hours; apply eugenol (clove oil) or crushed garlic paste on cotton directly to the cavity."
            ),
            exactFormulasOrRatios = "• WHO Oral Rehydration Solution (1 Liter): 1 L clean water + 6 level tsp (25g) sugar + 1/2 level tsp (3g) salt.\n• 0.9% Sterile Saline (500 mL): 500 mL boiled water + 1 level tsp (4.5g) salt.\n• Quarter-Strength Dakin's Antiseptic (1 Liter): 1 L boiled water + 25 mL (5 tsp) unscented 5% bleach + 1/2 tsp baking soda (keep away from light)."
        ),
        SurvivalGuideEntry(
            id = "hypothermia_burns_bites",
            category = "Medical",
            title = "4. Hypothermia, Heatstroke, Burns, Fractures & Envenomation",
            summary = "Treat extreme core temperature emergencies, extensive burns, broken bones, and venomous snakebites.",
            criticalSteps = listOf(
                "1. Severe Hypothermia: Strip wet clothing immediately. Insulate underneath from the ground first. Place warm water bottles in armpits and groin (never rub freezing limbs—causes cold blood to rush to the heart and trigger cardiac arrest).",
                "2. Frostbite: Rewarm in 37°C–39°C (98°F–102°F) water (comfortable to a healthy elbow) for 30 minutes—ONLY if there is zero risk of refreezing (thawing and refreezing destroys all tissue).",
                "3. Heatstroke (Core > 40°C / Confusion): Drench neck, scalp, armpits, and groin with water while fanning aggressively.",
                "4. Severe Burns: Cool with clean water for 15 minutes (no ice/butter). Remove rings/watches immediately. Wrap loosely in clean plastic wrap and give WHO ORS fluids.",
                "5. Fractures & Snakebites: For fractures, pad and splint BOTH the joint above and below the break. For venomous snakebites: DO NOT cut, suck, ice, or tourniquet. Keep the limb immobilized at heart level, remove jewelry, and carry the patient if possible."
            ),
            exactFormulasOrRatios = "Parkland Burn Fluid Rule (First 24h): 4 mL WHO ORS × Body Weight (kg) × % Body Surface Burned (give half in first 8 hours; patient's palm = 1% body area)."
        ),
        SurvivalGuideEntry(
            id = "water_procurement",
            category = "Water",
            title = "5. Finding Hidden Water & Saltwater Distillation",
            summary = "Extract clean water from dry riverbeds, green trees, morning dew, and seawater.",
            criticalSteps = listOf(
                "1. Dry Riverbed Seepage Well: Dig 30–60 cm deep in the lowest point of the OUTSIDE bend of a dry streambed (or at the base of green willow/cottonwood/sycamore trees). Let water seep in and settle.",
                "2. Tree Transpiration Bag (Zero-Fuel Pure Water): Tie a clear plastic bag tightly around a leafy, non-poisonous green tree branch in direct sunlight with a small stone in the bottom corner. Yields 200–500 mL of pure distilled water per bag per day.",
                "3. Morning Dew Harvesting: Before sunrise, tie clean cotton cloths around your ankles and walk through tall grass, then wring into a container (up to 0.5–1 L/hr).",
                "4. Seawater / Contaminated Water Distillation: Never drink seawater or urine. Fill a pot halfway with seawater, place a heavy cup upright in the center (mouth above water), invert the pot lid on top (handle pointing down over the cup), boil gently, and place cold water/wet cloth on top of the lid so pure steam condenses and drips into the center cup."
            ),
            exactFormulasOrRatios = "Never eat unmelted snow or ice directly when cold—the latent heat of fusion (334 J/g) drains core body temperature and causes hypothermia. Melt it inside a bottle tucked between your outer and mid jacket layers."
        ),
        SurvivalGuideEntry(
            id = "water_purification_chemistry",
            category = "Water",
            title = "6. Water Filtration & Exact Disinfection Chemistry",
            summary = "Build a 4-stage bio-charcoal filter and disinfect water with boiling, bleach, pool shock, or UV.",
            criticalSteps = listOf(
                "1. Two-Step Rule: Always FILTER first (to remove mud and chemical toxins), then DISINFECT (to kill microscopic bacteria, viruses, and cysts).",
                "2. 4-Stage Inverted Bottle Filter (Bottom Neck to Top): (1) 3 cm tight cotton cloth plug, (2) 6 cm crushed hardwood charcoal from a cooled campfire (adsorbs pesticides/chemicals), (3) 6 cm fine sand, (4) 6 cm coarse gravel + top cloth pre-filter.",
                "3. Rolling Boil (100% Effective): Bring filtered water to a rolling boil for 1 full minute (3 minutes above 2,000m / 6,500ft altitude).",
                "4. Chemical Chlorine Disinfection: Add unscented bleach or Calcium Hypochlorite stock solution (exact drops below), shake, and wait 30 minutes (60 minutes if water is <10°C). Water must have a slight chlorine odor.",
                "5. SODIS Solar UV Disinfection: Fill a clear PET plastic bottle (<= 2L), shake with air, and lay flat on a reflective roof in direct sun for 6 hours (48 hours if cloudy)."
            ),
            exactFormulasOrRatios = "• Household Bleach (5%–6% NaOCl): 2 drops/Liter (clear) or 4 drops/Liter (cloudy/cold). Per Gallon: 8 drops (clear) or 16 drops (cloudy).\n• Concentrated Bleach (8.25%): 1–2 drops/Liter (clear) or 3 drops/Liter (cloudy).\n• Calcium Hypochlorite (68% Pool Shock, no algaecide): Dissolve 5g (1 tsp) in 7.5 L (2 Gal) water to make Stock -> Add 10 mL (2 tsp) Stock per 1 Liter water.\n• 2% Iodine Tincture: 5 drops/Liter (clear) or 10 drops/Liter (cloudy)."
        ),
        SurvivalGuideEntry(
            id = "shelter_thermodynamics",
            category = "Fire & Shelter",
            title = "7. All-Weather Shelter Physics (Cold, Rain, Snow & Desert)",
            summary = "Stop conductive ground heat loss and build waterproof debris huts, snow caves, and urban micro-rooms.",
            criticalSteps = listOf(
                "1. The 50% Ground Bed Rule: Cold/wet ground steals body heat 25x faster than air. Spend half your time building a 15–20 cm (6–8 inch) compressed bed of dry leaves, pine needles, evergreen boughs, or cardboard UNDERNEATH you before building a roof.",
                "2. Waterproof Debris A-Frame: Prop a 2.5m ridgepole on a waist-high stump (~90 cm). Lean side ribs at a steep 45° angle (flatter angles leak rain). Pile 60 cm (2 ft) of leaves, bark, and moss on top. Keep interior barely wider than your shoulders so your 100W body heat warms the air.",
                "3. Snow Trench / Quinzhee (Sub-Zero): Dig the sleeping platform 30 cm HIGHER than the entrance floor (cold air sinks into the lower 'cold well' trench). Poke a 5 cm ventilation hole in the roof with a stick and check it hourly.",
                "4. Urban Freeze Blackout ('Room-in-a-Room'): Pitch a small tent or drape heavy blankets over a dining table on top of a mattress in an interior south-facing room. Four people inside raise temperature by +12°C using body heat alone."
            ),
            exactFormulasOrRatios = "Shelter Geometry: Roof angle >= 45° for rain runoff • Debris wall thickness >= 60 cm (24 in) • Ground insulation bed >= 15 cm (6 in) compressed."
        ),
        SurvivalGuideEntry(
            id = "firecraft_wet_weather",
            category = "Fire & Shelter",
            title = "8. Wet-Weather Firecraft & 6 No-Match Ignition Methods",
            summary = "Harvest bone-dry heartwood in a downpour and ignite fire using batteries, lenses, friction, or steel.",
            criticalSteps = listOf(
                "1. Finding Dry Wood in Pouring Rain: Ignore wood on the wet ground. Snap dead branches still attached to standing trees. Split wrist-thick branches lengthwise (batoning with a knife and wooden club or between rocks) to expose bone-dry inner heartwood.",
                "2. Feather Sticks & Waterproof Tinders: Shave paper-thin curls along dry inner heartwood sticks without cutting them off. Collect pine pitch ('fatwood' at the base of dead pine branches) or papery birch bark (burns even when wet).",
                "3. Ignition 1 — Battery + Foil Gum Wrapper / Steel Wool: Cut a foil-backed paper gum wrapper so the middle bridge is 2 mm narrow. Touch the foil ends to the + and - terminals of any AA, 9V, power-tool, or phone battery—the bridge instantly ignites.",
                "4. Ignition 2 — 12V Car Battery Spark: Touch jumper wires to steel wool or a cotton rag dabbed with 1 drop of motor oil/vaseline.",
                "5. Ignition 3 — Solar Lens / Water Bag: Focus bright sun through eyeglasses, a camera lens, or a clear plastic bag filled with water onto blackened punk wood or charcoal dust."
            ),
            exactFormulasOrRatios = "Dakota Smokeless Fire Pit (Stealth & High Heat): Dig two 30 cm deep holes 30 cm apart and connect their bottoms with a 15 cm tunnel. Build the fire in the downwind hole; the upwind hole acts as a bellows draft, burning fiercely with almost zero visible flame or smoke."
        ),
        SurvivalGuideEntry(
            id = "food_foraging_trapping",
            category = "Food & Knots",
            title = "9. Safe Wild Foraging, Poison Rules, Snares & Fish Traps",
            summary = "Identify universally safe wild calories, avoid lethal plant toxins, and build passive traps.",
            criticalSteps = listOf(
                "1. Lethal Plants to NEVER Touch: Avoid all wild mushrooms, any plant with umbrella-shaped white flower clusters (Poison Water Hemlock—fatal in minutes), milky or colored sap, 3-leaved vines (Poison Ivy/Oak), white/yellow berries, and shiny bitter almonds/beans.",
                "2. Four Safe High-Calorie Wild Staples: (a) Cattails (peel and boil/roast underground rhizome roots for starch; eat young shoots & yellow pollen), (b) Pine Inner Bark & Needles (boil spongy white inner cambium bark; steep green pine needles in hot water for 5x Vitamin C of lemons—avoid Yew), (c) Acorns (crush and soak in running water or change boiling water 4 times to leach bitter tannins), (d) Grasshoppers/Crickets/Grubs (remove legs/wings and boil/roast thoroughly).",
                "3. Passive Small-Game Snare: Form a 10 cm (4-inch) loop of brass/copper wire or inner paracord strand, suspend 3 fingers (5 cm) off the ground inside a narrow brush trail funnel, and anchor firmly to a sapling.",
                "4. Inverted Bottle / Rock Funnel Fish Trap: Cut the top third off a 2L plastic bottle, invert the cone neck-inward into the body, tie securely, bait with crushed worms/insects, and submerge weighted with stones in a quiet stream eddy."
            ),
            exactFormulasOrRatios = "Meat Preservation (Smoking / Pemmican): Slice lean meat into 6 mm (1/4-inch) strips with zero fat, soak 30 mins in 10% salt brine if available, and hang 120 cm above a low hardwood smolder (not cooking heat!) for 12–18 hours until brittle dry."
        ),
        SurvivalGuideEntry(
            id = "essential_knots",
            category = "Food & Knots",
            title = "10. Five Essential Survival Knots (Step-by-Step)",
            summary = "Tie a non-slip rescue loop, adjustable shelter guyline, ridgepole anchor, and load binder.",
            criticalSteps = listOf(
                "1. Bowline (Non-Slip Rescue Loop That Never Jams): Form a small overhand loop in the standing rope ('the rabbit hole'). Pass the working end up through the hole, around behind the standing rope ('around the tree'), and back down into the hole. Pull tight.",
                "2. Taut-Line Hitch (Adjustable Tarp / Tent Tensioner): Wrap the rope around a stake. Pass the working end INSIDE the loop twice around the standing line, then once OUTSIDE (above) those two wraps, and tuck through. Slides freely to tighten and locks under load.",
                "3. Clove Hitch (Fast Ridgepole Anchor): Make a loop around the pole, cross over the standing part to make a second loop in the same direction, and tuck the working end under the second wrap.",
                "4. Square (Reef) Knot (Joining Two Ropes of Equal Thickness): Right over left and tuck under; then left over right and tuck under (forms two interlocking U-loops).",
                "5. Prusik Hitch (Climbing / Hauling Grip on a Main Rope): Pass a tied cord loop around a thicker rope 3 times, feeding the loop through itself on each pass, then dress neatly. Slides when pushed by hand; locks solid when weighted."
            ),
            exactFormulasOrRatios = "Cordage Scavenging: Standard 550 Paracord contains 7–9 inner nylon core strands. Stripping the outer sheath multiplies 10 meters of paracord into 80 meters of high-strength fishing line, snare cord, and shelter lashing."
        ),
        SurvivalGuideEntry(
            id = "cbrn_nuclear_fallout",
            category = "Nuclear & CBRN",
            title = "11. Nuclear Detonation, 7-10 Fallout Rule & KI Dosing",
            summary = "Survive blast waves, calculate fallout decay, build shielding, and dose Potassium Iodide (KI).",
            criticalSteps = listOf(
                "1. Blast Wave (0–60 Seconds): Never look at the flash or stand near windows (glass shatters up to 15 km away; blast travels ~1 km every 3 seconds). Drop face-down behind hard cover, thumbs in ears, mouth slightly open to equalize lung/eardrum pressure.",
                "2. The 15-Minute Fallout Window: Radioactive sand/ash takes 10–20 minutes to begin falling. Get into an underground basement or the center hallway of the middle floors of a multi-story concrete building (equidistant from the roof and ground).",
                "3. Decontamination: If dust falls on you outside, strip outer clothing before entering shelter (removes 90% of radiation). Wash skin and hair with soap and lukewarm water—NEVER use hair conditioner (binds radioactive isotopes to hair proteins).",
                "4. Stay Sealed Indoors 48–72 Hours: Fallout decays exponentially by the 7-10 Rule (at 49 hours, outdoor radiation is only 1% of its 1-hour lethal peak)."
            ),
            exactFormulasOrRatios = "• 7-10 Fallout Decay Rule: 1 hr = 100% rate • 7 hrs = 10% rate • 49 hrs (2 days) = 1% rate • 343 hrs (2 weeks) = 0.1% rate.\n• Gamma Halving Thickness (cuts radiation 50%): 1 cm Lead = 2.5 cm Steel = 6 cm Concrete = 9 cm Packed Earth = 18 cm Water.\n• Potassium Iodide (KI) Daily Thyroid Block (take only when nuclear fallout is confirmed): Adults >18y: 130 mg • Ages 3–18y: 65 mg • Ages 1m–3y: 32 mg • Infants <1m: 16 mg."
        ),
        SurvivalGuideEntry(
            id = "cbrn_chemical_biological",
            category = "Nuclear & CBRN",
            title = "12. Chemical Gas Plumes & Biological Quarantine",
            summary = "Escape heavier-than-air toxic gas clouds and establish negative-pressure biological isolation.",
            criticalSteps = listOf(
                "1. Opposite of Nuclear (Go UP, Not Down!): Almost all industrial/chemical gases (Chlorine, Phosgene, Ammonia, Hydrogen Sulfide, Propane) are HEAVIER than air and pool in basements, valleys, and subways. Move immediately UPWIND, UPHILL, and to the TOP FLOORS of a building.",
                "2. Shelter-in-Place Gas Seal: Turn off all HVAC/fans, close fireplace dampers, stuff wet towels under doors, and seal window/door perimeters with duct tape and plastic sheeting.",
                "3. Improvised Acid-Gas Respirator Pad: For Chlorine, Phosgene, or tear gas/smoke, dissolve 1 tablespoon of baking soda (sodium bicarbonate) in 250 mL water, soak an 8-layer cotton cloth, wring damp, and breathe through it (neutralizes acidic gases). For Ammonia (alkaline), dampen cloth with diluted vinegar/lemon juice.",
                "4. Biological Outbreak Quarantine: Isolate sick individuals in a separate downwind room with a dedicated bucket toilet (add 100 mL bleach per use). Caregivers wear eye protection, N95/multi-layer cloth mask, and wash hands for 30s in 0.05% bleach solution."
            ),
            exactFormulasOrRatios = "• Surface Viral/Spore Disinfection Solution (0.5% NaOCl): 1 part household 5% bleach + 9 parts water (100 mL bleach per 1 Liter water; wait 10 minutes before wiping).\n• Handwash Disinfection Solution (0.05% NaOCl): 1 part 5% bleach + 99 parts water (10 mL / 2 tsp bleach per 1 Liter water)."
        ),
        SurvivalGuideEntry(
            id = "navigation_zero_tech",
            category = "Navigation",
            title = "13. Zero-Tech Sun, Shadow, Watch & Star Navigation",
            summary = "Determine True North, latitude, and distance traveled with zero electronics.",
            criticalSteps = listOf(
                "1. Universal Shadow-Tip Method (Works Anywhere on Earth): Plant a straight 1m stick vertically in flat sunny ground. Mark the shadow tip with Pebble 1 (ALWAYS WEST). Wait 15–20 minutes and mark the new shadow tip with Pebble 2 (ALWAYS EAST). Stand with Pebble 1 (West) on your left and Pebble 2 (East) on your right: you are facing True North.",
                "2. Analog Watch Compass (Northern Hemisphere): Hold watch flat and point the Hour Hand at the Sun. The halfway line between the Hour Hand and 12 o'clock points True South. (Southern Hemisphere: point 12 o'clock at the Sun; halfway to Hour Hand is True North).",
                "3. Night Stars (Northern): Follow the two outer pointer stars of the Big Dipper's bowl 5x their distance to Polaris (True North). Polaris altitude above the horizon in degrees = your exact Latitude (1 clenched fist held at arm's length = 10°).",
                "4. Night Stars (Southern): Extend the long bar of the Southern Cross (Crux) 4.5x its length and drop straight down to the horizon for True South."
            ),
            exactFormulasOrRatios = "• Pace Count Dead Reckoning: Average adult = 65–70 double-paces (every time right foot strikes) per 100 meters on flat ground.\n• Sunset Countdown: Hold hand horizontally at arm's length below the Sun (fingers parallel to horizon). Each finger width between Sun and horizon = 15 minutes of daylight remaining (1 full hand = 1 hour)."
        ),
        SurvivalGuideEntry(
            id = "power_radio_faraday",
            category = "Power & Radio",
            title = "14. Battery Scavenging, Faraday Shield & Antenna Physics",
            summary = "Charge 5V phones from 12V car/AA batteries, block EMPs, and build a long-range wire antenna.",
            criticalSteps = listOf(
                "1. Charge 5V Phones from 12V Car/Truck Batteries: Never connect 12V directly to a 5V phone! Use a car cigarette-lighter USB adapter: wire Car +12V to the spring-loaded center tip of the plug, and Car -GND to the side metal springs.",
                "2. Charge from AA / C / D Alkaline Cells: Tape 4 cells (1.5V each) in series (+ to -) for 6.0V open-circuit (drops to safe ~5.1V under phone charging load). Standard USB-A Socket Pinout (looking inside, plastic tongue down): Far Right Pin 1 = +5V (Red), Far Left Pin 4 = GND (Black).",
                "3. Improvised EMP / Solar Storm Faraday Cage: Wrap spare phones/radios in dry cardboard or cloth (insulator), then seal tightly inside 3 separate, nested layers of heavy-duty aluminum foil (or inside a metal ammo/trash can without touching the bare metal walls).",
                "4. Walkie-Talkie / Radio Range Multiplier ('Tiger Tail' Counterpoise): Cut a spare piece of insulated wire to 1/4 wavelength (see formula below), strip 1 cm at one end, and loop it around the base of your handheld radio's antenna ground collar to let it hang down. Doubles effective radiated signal!"
            ),
            exactFormulasOrRatios = "• Quarter-Wave Counterpoise Wire Length: Length (meters) = 71.25 / Frequency (MHz).\n  - FRS / PMR446 Walkie-Talkie (446–462 MHz): 16 cm (6.3 inches) wire.\n  - VHF Marine / 2m Ham (144–156 MHz): 48 cm (19 inches) wire.\n  - CB Radio (27 MHz): 2.64 meters (8 ft 8 in) wire."
        ),
        SurvivalGuideEntry(
            id = "rescue_signals_icao",
            category = "Power & Radio",
            title = "15. International Ground-to-Air & Acoustic Rescue Signals",
            summary = "Signal search aircraft up to 30 km away with aiming mirrors, smoke, and ICAO symbols.",
            criticalSteps = listOf(
                "1. Universal Rule of 3: Any signal repeated 3 times means SOS (3 whistle blasts, 3 fires in an equilateral triangle 30m apart, 3 mirror flashes). A whistle carries 1.5+ km (10x farther than shouting with 1% of the energy).",
                "2. Signal Mirror Aiming (Visible 30+ km to Aircraft): Hold a mirror (or polished phone screen/can bottom) near your eye. Extend your other hand toward the aircraft/horizon making a 'V' with two fingers. Reflect the sunbeam onto your V-fingers, then tilt the mirror slightly so the bright spot shifts from your fingers directly onto the aircraft.",
                "3. Day vs. Night Signal Fires: At night, build a dry-wood pyramid on a ridge for bright flame. During the day, throw green leaves, pine boughs, wet moss, or rubber/oil onto a hot fire to generate a dense vertical column of white or black smoke.",
                "4. Ground-to-Air Visual Symbols: Stomp trenches in snow or lay logs/rocks 6 meters (20 ft) tall in an open clearing (orient trenches East-West so sun casts deep shadows inside them)."
            ),
            exactFormulasOrRatios = "ICAO Ground-to-Air Code Table (6m / 20ft tall):\n• V = Require Assistance\n• X = Require Medical Evacuation\n• N = No / Negative\n• Y = Yes / Affirmative\n• ↑ = Proceeding in this direction\n• Morse SOS: · · ·  — — —  · · · (3 short, 3 long, 3 short)"
        ),
        SurvivalGuideEntry(
            id = "disasters_wildfire_flood_car",
            category = "Disasters",
            title = "16. Wildfire Physics, Submerged Cars, Floods & Avalanches",
            summary = "Escape uphill chimney wildfires, sinking vehicles, whitewater currents, and snow slides.",
            criticalSteps = listOf(
                "1. Wildfire Escape: Fires race UPHILL and DOWNWIND up to 20+ km/h (chimney effect preheats upslope brush; fire climbs 4x faster uphill). Never run uphill ahead of a fire! Move DOWNHILL and CROSSWIND into already-burned black ash ('the black'), rocky scree, or shallow rivers. Breathe near the ground through a wet 100% cotton cloth.",
                "2. Sinking / Submerged Vehicle (First 45 Seconds): Do NOT wait for the car to fill with water. (1) Unbuckle seatbelts FIRST, (2) Roll down or smash the SIDE tempered glass at its bottom corner (never the laminated front windshield), (3) Push children out first, (4) Exit immediately.",
                "3. Flash Flood & Rip Currents: 15 cm of fast water knocks an adult down; 45 cm floats an SUV. If swept into whitewater, float on your back with feet pointed downstream at 45° to fend off rocks (never stand up—foot entrapment drowns). In an ocean rip current, swim PARALLEL to the beach for 40m before angling to shore.",
                "4. Avalanche Burial: Swim hard uphill on your back to stay near the surface. Before the snow sets like concrete (in 3 seconds when it stops), cup one hand over your mouth/nose to create a breathing air pocket and punch the other arm straight up. If buried, spit to see which way gravity pulls saliva, then dig the opposite way."
            ),
            exactFormulasOrRatios = "Lightning 30-30 Rule: Seconds between flash and thunder ÷ 3 = distance in km. If <= 30s (10 km), assume the Lightning Crouch in a low depression away from tall trees: balance on the balls of your feet with HEELS TOUCHING (routes ground voltage from one foot to the other instead of through your heart) and hands over ears."
        ),
        SurvivalGuideEntry(
            id = "biomes_urban_evasion",
            category = "Biomes & Urban",
            title = "17. Desert, Arctic, Jungle, Ocean & Urban Evasion",
            summary = "Biome-specific survival rules and Gray Man urban blackout movement protocols.",
            criticalSteps = listOf(
                "1. Desert Survival (Conserve Sweat, Not Water): Rest completely in shade during the day (dig 30 cm down into cooler sand under a tarp) and travel only at night. Keep clothes ON and loose to trap humidity and block solar heat gain. Never eat dry food if you have no water (digestion consumes body water).",
                "2. Arctic / Alpine ('Be Bold, Start Cold'): Sweat is fatal in freezing weather—wet base layers freeze solid the moment you stop moving. Strip layers BEFORE hiking uphill so you stay slightly cool and dry; put warm layers back on the instant you stop.",
                "3. Open Ocean / Life Raft: Rig immediate shade first. Collect rainwater in rinsed tarps. Never drink seawater or urine, and never eat fish/protein unless you have excess fresh water to excrete the urea.",
                "4. Urban Collapse & Evasion (Gray Man Protocol): Wear drab, worn civilian clothes (never military camo or tactical packs). Carry essentials inside a plain grocery or laundry bag. Move during heavy rain or 45 minutes before dawn along secondary utility corridors, avoiding bridges, crowds, and main intersections."
            ),
            exactFormulasOrRatios = "Urban Water Scavenging (Silcock Key): Commercial building exteriors use recessed 4-way square stems (1/4 in, 9/32 in, 5/16 in, 11/32 in). If you lack a 4-way Silcock key, lock vice-grips or an adjustable wrench onto the recessed brass square stem to tap building water reserves."
        )
    )
}
