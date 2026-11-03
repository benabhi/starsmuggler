# Generates tables.json, paragraphs.json, equipment.json and goods.json from the rule book
# (r233-r242, r300-r339), transcribed by hand below, plus their Spanish names. Run from the repo
# root: python tools/content/gen_tables.py, then python tools/content/check_tables.py (needs recursos/).
import json, sys

A = "content/src/main/assets/content/"

def entries(pairs):
    return [{"roll": r, "result": v} for r, v in pairs]

def row(key, pairs):
    d = {"entries": entries(pairs)}
    if key is not None:
        d = {"key": key, **d}
    return d

def by_die(values, start=1):
    return [(str(start + i), v) for i, v in enumerate(values)]

tables = []

# r233 entry and contact, 1d6 with -1 (entry) or +1 per day (contact): 0..5, 6+
R233 = [
    ("city", "e129 r337 r339 e011 e185 r295a SIST"),
    ("colony", "e019 e185 e137 r329 e198 r205b SIST"),
    ("craters", "e070 SIST e140 e196 e085 r205c e168"),
    ("gambling", "e006 r331 r334 e014 SIST r205d e071"),
    ("industrial", "e181 r333 e010 r335 e146 r205e SIST"),
    ("military", "e059 e077 e003 e086 SIST r205f e018"),
    ("mining", "e181 e005 e190 SIST e072 r205g e172"),
    ("palace", "e059 e199 e012 r336 SIST e131 e175"),
    ("prison", "e417 e008 e190 e087 SIST r205i e073"),
    ("rough_terrain", "e154 e015 SIST e168 e188 e196 e178"),
    ("ruins", "e150 SIST e004 e070 e088 r205k e084"),
    ("rural", "r338 e016 e005 SIST e188 r205l e180"),
    ("scientific", "e017 e009 e127 e158 r205m SIST e075"),
    ("slum", "e142 r332 r337 e076 e013 r205n SIST"),
    ("spaceport", "e019 e409 r330 e185 SIST e002 r205o"),
    ("space_station", "e019 SIST e002 e007 e125 r328 r205p"),
]
rolls = ["0", "1", "2", "3", "4", "5", "6+"]
tables.append({"id": "r233", "rule": "r233", "dice": "1d6",
               "rows": [row(k, list(zip(rolls, v.split()))) for k, v in R233]})

SIST = [("regari", "e122 e147 e192"), ("palatek", "e126 e151 e139"), ("byzantium", "e131 e155 e123"),
        ("imperia", "e136 e159 e424"), ("talitar", "e141 e134 e191"), ("cubro", "e145 e124 e135"),
        ("uruskop", "e149 e128 e188"), ("nipna", "e153 e133 e156"), ("mynkuria", "e157 e138 e152"),
        ("new_karma", "e089 e143 e148")]
tables.append({"id": "r233.system", "rule": "r233", "dice": "1d6",
               "rows": [row(k, list(zip(["1-2", "3-5", "6"], v.split()))) for k, v in SIST]})

# r235 tech level: printed as roll rows x tech code columns; stored as one row per tech code.
codes = [10, 15, 20, 25, 30, 40, 50, 60, 70, 100]
R235 = [
    "*1 1 1 *1 1 1 2 3 4 5",
    "1 1 *1 1 1 2 3 4 5 6",
    "1 *1 1 1 2 3 4 5 6 6",
    "*1 1 1 2 3 4 5 6 6 6",
    "1 1 2 3 4 5 6 6 6 6",
    "1 2 3 4 5 6 6 6 6 6",
]
grid = [r.split() for r in R235]
tables.append({"id": "r235", "rule": "r235", "dice": "1d6",
               "rows": [row(str(c), by_die([grid[roll][col] for roll in range(6)])) for col, c in enumerate(codes)]})

# r237 critical hits
tables.append({"id": "r237.starship", "rule": "r237", "dice": "1d6", "rows": [row(None, by_die(
    ["#shields_fail", "#ecm_fail", "#life_support_fail", "#comms_fail", "#engines_fail", "#compartment_breach"]))]})
tables.append({"id": "r237.compartment", "rule": "r237", "dice": "1d6", "rows": [row(None, by_die(
    ["#cockpit", "#crew_quarters", "#turret", "#engineering", "#cargo_hold", "#boat_bay"]))]})
tables.append({"id": "r237.vehicle", "rule": "r237", "dice": "1d6", "rows": [row(None, [
    ("1-3", "#vehicle_breakdown"), ("4", "#carried_equipment_breakdown"), ("5-6", "#cargo_hits")])]})

# r238 detection factor: looked up by the system tech code, no dice
brackets = ["24-", "25-39", "40-69", "70+"]
R238 = [("on_foot", "1 1 2 3"), ("commercial_vehicle", "1 1 3 4"), ("skimmer", "1 2 4 4"),
        ("ships_boat", "2 3 4 5"), ("orbital_shuttle", "2 3 5 6"), ("starship", "3 4 5 6")]
tables.append({"id": "r238", "rule": "r238", "rows": [row(k, list(zip(brackets, v.split()))) for k, v in R238]})

# r240 stasis recovery
tables.append({"id": "r240", "rule": "r240", "dice": "1d6", "rows": [row(None, by_die(
    ["#stasis_lost", "#stasis_culture_shock", "#stasis_1200_years", "#stasis_230_years", "#stasis_3_years", "#stasis_1_year"]))]})

# r241 base price multipliers, one row per wealth code
R241 = [(0, "1 1 1 1 1 1.5"), (10, "1 1 1 1 1 2"), (15, "1 1 1 1 1.5 2"), (20, "1 1 1 1 2 3"),
        (25, "1 1 1 1.5 2 3"), (30, "1 1 1 2 3 3"), (40, "1 1 1.5 2 3 5"), (50, "1 1 2 3 3 5"),
        (60, "1 1.5 2 3 5 10"), (70, "1 2 3 3 5 10"), (100, "2 3 5 5 10 20")]
tables.append({"id": "r241", "rule": "r241", "dice": "1d6",
               "rows": [row(str(w), by_die(["x" + m for m in v.split()])) for w, v in R241]})

# r242 cache time modifier, looked up by days since the cache was placed
tables.append({"id": "r242.time", "rule": "r242", "rows": [row(None, [
    ("9-", "0"), ("10-39", "1"), ("40-364", "2"), ("365-36499", "3"), ("36500-364999", "4"), ("365000+", "5")])]})

# r327 battle reference, 2d6 with modifiers
tables.append({"id": "r327", "rule": "r327", "dice": "2d6", "rows": [row(None, [
    ("2-", "r306"), ("3", "r304"), ("4", "r305"), ("5", "r316"), ("6", "r317"), ("7", "r313"),
    ("8", "r314"), ("9", "r315"), ("10", "r301"), ("11", "r312"), ("12+", "r300")])]})
tables.append({"id": "r328", "rule": "r328", "dice": "1d6", "rows": [row(None, [("1-3", "#rumor_false"), ("4-6", "#rumor_true")])]})
tables.append({"id": "r329", "rule": "r329", "dice": "1d6", "rows": [row(None, [
    ("1", "#map_excellent"), ("2", "#map_good"), ("3", "#map_fair"), ("4", "#map_trap"), ("5-6", "#map_inaccurate")])]})
tables.append({"id": "r331", "rule": "r331", "dice": "1d6", "rows": [row(None, by_die(
    ["#fine_liquor", "#robot_servos", "#small_arms_ammo", "#expensive_clothing", "#rare_birds", "#rare_wood"]))]})
tables.append({"id": "r332", "rule": "r332", "dice": "1d6", "rows": [row(None, by_die(
    ["#radioactive_materials", "#rare_spices", "#stolen_art", "#cut_gems", "#young_slaves", "#antique_small_arms"]))]})
tables.append({"id": "r333", "rule": "r333", "dice": "1d6", "rows": [row(None, by_die(
    ["#starship_papers", "#robot_plans", "#antique_ground_vehicle", "#radar_software", "#hypnogem", "#mining_mole"]))]})
tables.append({"id": "r333.antique_vehicle", "rule": "r333", "dice": "1d6", "rows": [row(None, [
    ("1-3", "#not_interested"), ("4-5", "x3"), ("6", "x5")])]})
tables.append({"id": "r333.radar_software", "rule": "r333", "dice": "1d6", "rows": [row(None, [
    ("1", "#not_interested"), ("2", "#taken_for_nothing"), ("3", "e130"), ("4", "1000"), ("5", "2000"), ("6+", "10000")])]})

factors = [
    {"id": "r238.area", "rule": "r238", "factors": {
        "prison": 2, "space_station": 2, "military": 1, "palace": 1, "rough_terrain": -1, "ruins": -1, "craters": -2}},
    {"id": "r242.base", "rule": "r242", "factors": {
        "asteroid": 0, "craters": 1, "ruins": 1, "mining": 2, "orbit": 2, "rough_terrain": 2, "rural": 2,
        "city": 3, "colony": 3, "industrial": 3, "scientific": 3, "military": 4, "slum": 4}, "otherwise": 5},
]

def para(n, kind, **kw):
    return {"rule": "r%d" % n, "kind": kind, **kw}

paragraphs = (
    [para(n, "surprise") for n in range(300, 304)]
    + [para(n, "surprised") for n in range(304, 307)]
    + [para(n, "escape") for n in range(307, 310)]
    + [para(n, "escape_vehicle") for n in range(310, 313)]
    + [para(n, "combat") for n in range(313, 318)]
    + [para(318 + i, "bribe_pass", basePrice=p) for i, p in enumerate([20, 25, 30, 40])]
    + [para(n, "pass") for n in range(322, 326)]
    + [para(326, "pass_unarmed"), para(327, "battle", table="r327"), para(328, "entry_rumor", table="r328"),
       para(329, "entry_map", table="r329"), para(330, "bribe_entry", basePrice=25),
       para(331, "buy", basePrice=50, table="r331"), para(332, "buy", basePrice=100, table="r332"),
       para(333, "buy", basePriceDice="2d6", table="r333"),
       para(334, "sell", basePrice=50, sells=["fine_liquor", "rare_birds", "heavy_hand_weapon"]),
       para(335, "sell", basePrice=100, sells=["utility_suit", "robot_servos", "robot_mg", "robot_u", "robot_sp", "young_slaves"]),
       para(336, "sell", basePrice=100, sells=["expensive_clothing", "stolen_art", "rare_spices"]),
       para(337, "sell", basePrice=100, sells=["hallucinogenic_drugs", "fine_liquor", "small_arms", "hypnogem"]),
       para(338, "sell", basePrice=50, sells=["small_arms_ammo", "small_arms_case", "heavy_hand_weapon", "skimmer_guns", "frag_grenades_pack"]),
       para(339, "sell", basePrice=100, sells=["rare_wood", "cut_gems", "antique_small_arms", "alien_artifact", "young_slaves"])]
)

# r236 equipment table: id, rule, UC (None = "—"), sized ("1+")
EQUIPMENT = [
    ("fuel_unit", "r211c", 1, "Unidad de combustible (COMB)"),
    ("life_support_unit", "r213", 1, "Unidad de soporte vital (USV)"),
    ("repair_unit", "r211b", 1, "Unidad de reparación (UR)"),
    ("utility_suit", "r213", 1, "Traje utilitario"),
    ("stasis_unit", "r212e", "1+", "Unidad de estasis"),
    ("small_arms", "r216d", 0, "Arma corta"),
    ("heavy_hand_weapon", "r216d", 1, "Arma pesada de mano"),
    ("skimmer_guns", "r216c", 2, "Cañones de deslizador"),
    ("boat_guns", "r216b", 5, "Cañones de la nave auxiliar"),
    ("starship_guns", "r216a", 6, "Cañones de nave estelar"),
    ("skimmer", "r215c", 10, "Deslizador"),
    ("ships_boat", "r214", 40, "Nave auxiliar"),
    ("commercial_vehicle", "r215b", None, "Vehículo comercial"),
    ("orbital_shuttle", "r215a", None, "Transbordador orbital"),
    ("starship", "r212", None, "Nave estelar"),
    ("robot_mg", "r222a", 1, "Robot MG"),
    ("robot_u", "r222b", 1, "Robot U"),
    ("robot_sp", "r222c", 1, "Robot SP"),
    ("hypercharge", "r212b", None, "Hipercarga"),
]
equipment = []
for id, rule, uc, _ in EQUIPMENT:
    e = {"id": id, "rule": rule}
    if uc == "1+":
        e.update(capacity=1, sized=True)
    elif uc is not None:
        e["capacity"] = uc
    equipment.append(e)

# Goods of r331-r339 that are not equipment: id, rule, UC per unit (None = not given)
GOODS = [
    ("fine_liquor", "r331", 1, "Caja de licores finos"),
    ("robot_servos", "r331", 2, "Caja de servomotores para robots"),
    ("small_arms_ammo", "r331", 1, "Caja de munición para armas cortas"),
    ("expensive_clothing", "r331", 2, "Caja de ropa costosa"),
    ("rare_birds", "r331", 1, "Jaula de aves raras"),
    ("rare_wood", "r331", 4, "Palé de maderas raras"),
    ("radioactive_materials", "r332", 4, "Contenedor de materiales radiactivos"),
    ("rare_spices", "r332", 1, "Tarro de especias raras"),
    ("stolen_art", "r332", 1, "Obra de arte robada"),
    ("cut_gems", "r332", 1, "Gemas talladas (20 por UC)"),
    ("young_slaves", "r332", 1, "Esclavos jóvenes"),
    ("antique_small_arms", "r332", 1, "Arma corta antigua"),
    ("starship_papers", "r333", None, "Documentos falsos de nave estelar y piloto"),
    ("robot_plans", "r333", 1, "Planos de un robot nuevo"),
    ("antique_ground_vehicle", "r333", 10, "Vehículo terrestre antiguo"),
    ("radar_software", "r333", 1, "Software de radar militar"),
    ("hypnogem", "r333", 1, "Hipnogema"),
    ("mining_mole", "r333", 12, "Topo minero Mark III"),
    ("hallucinogenic_drugs", "r337", None, "Ampollas de drogas alucinógenas"),
    ("small_arms_case", "r338", None, "Caja de 6 armas cortas"),
    ("frag_grenades_pack", "r338", None, "Paquete de 4 granadas de fragmentación"),
    ("alien_artifact", "r339", None, "Artefacto alienígena"),
]
goods = []
for id, rule, uc, _ in GOODS:
    g = {"id": id, "rule": rule}
    if uc is not None:
        g["capacity"] = uc
    goods.append(g)

RESULTS = {
    "shields_fail": "Fallan las pantallas defensivas",
    "ecm_fail": "Falla la CME",
    "life_support_fail": "Falla el soporte vital",
    "comms_fail": "Fallan las comunicaciones",
    "engines_fail": "Fallan los motores",
    "compartment_breach": "Compartimento perforado",
    "cockpit": "Cabina del piloto",
    "crew_quarters": "Camarotes de la tripulación",
    "turret": "Torreta",
    "engineering": "Ingeniería",
    "cargo_hold": "Bodega de carga",
    "boat_bay": "Compartimento de la nave auxiliar",
    "vehicle_breakdown": "Avería del vehículo",
    "carried_equipment_breakdown": "Avería del equipo transportado",
    "cargo_hits": "Impactos en la carga",
    "stasis_lost": "Perdido para siempre",
    "stasis_culture_shock": "Conmoción cultural",
    "stasis_1200_years": "1.200 años después",
    "stasis_230_years": "230 años después",
    "stasis_3_years": "3 años después",
    "stasis_1_year": "1 año después",
    "rumor_false": "El rumor es falso",
    "rumor_true": "El rumor es cierto",
    "map_excellent": "Información excelente",
    "map_good": "Información buena",
    "map_fair": "Información regular",
    "map_trap": "Trampa sin marcar",
    "map_inaccurate": "Mapa muy inexacto",
    "not_interested": "No les interesa",
    "taken_for_nothing": "Lo aceptan sin pagar",
}

def write(name, key, data, extra=None):
    doc = {"formatVersion": 1, key: data}
    if extra:
        doc.update(extra)
    with open(A + name, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")

write("tables.json", "tables", tables, {"factors": factors})
write("paragraphs.json", "paragraphs", paragraphs)
write("equipment.json", "equipment", equipment)
write("goods.json", "goods", goods)

names_path = "content/src/main/assets/text/es/names.json"
names = json.load(open(names_path, encoding="utf-8"))
names["equipment"] = {id: n for id, _, _, n in EQUIPMENT}
names["goods"] = {id: n for id, _, _, n in GOODS}
names["travelModes"] = {"on_foot": "A pie", "commercial_vehicle": "Vehículo comercial", "skimmer": "Deslizador",
                        "ships_boat": "Nave auxiliar", "orbital_shuttle": "Transbordador orbital", "starship": "Nave estelar"}
names["results"] = RESULTS
with open(names_path, "w", encoding="utf-8", newline="\n") as f:
    json.dump(names, f, ensure_ascii=False, indent=2)
    f.write("\n")

print(len(tables), "tables,", len(factors), "factor tables,", len(paragraphs), "paragraphs,", len(equipment), "equipment,", len(goods), "goods")
