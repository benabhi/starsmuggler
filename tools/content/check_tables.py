# Cross-checks the generated tables against the rule book translation. Run from the repo root.
import json

md = open('recursos/traduccion/starsmuggler_eh_rules_11/starsmuggler_eh_rules_11.md', encoding='utf-8').read()
T = {t['id']: t for t in json.load(open('content/src/main/assets/content/tables.json', encoding='utf-8'))['tables']}
names = json.load(open('content/src/main/assets/text/es/names.json', encoding='utf-8'))
inv = {v: k for k, v in names['areaTypes'].items()}


def section(h):
    i = md.index(h)
    j = md.index('\n## ', i + 5)
    return md[i:j]


def mdrows(sec):
    return [[c.strip() for c in l.strip().strip('|').split('|')] for l in sec.splitlines()
            if l.startswith('|') and not l.startswith('|---')]


def results(table, key):
    return [e['result'] for e in next(x for x in T[table]['rows'] if x.get('key') == key)['entries']]


rows = mdrows(section('## r233 '))
main = [r for r in rows if r[0] in inv]
for r in main:
    assert results('r233', inv[r[0]]) == r[1:], r
systems = {'Regari': 'regari', 'Palatek': 'palatek', 'Bizancio': 'byzantium', 'Imperia': 'imperia', 'Talitar': 'talitar',
           'Cubro': 'cubro', 'Uruskop': 'uruskop', 'Nipna': 'nipna', 'Mynkuria': 'mynkuria', 'Nueva Karma': 'new_karma'}
sist = [r for r in rows if r[0] in systems]
for r in sist:
    assert results('r233.system', systems[r[0]]) == r[1:], r
print('r233 ok:', len(main), 'area rows,', len(sist), 'system rows')

codes = [10, 15, 20, 25, 30, 40, 50, 60, 70, 100]
for r in mdrows(section('## r235 '))[1:]:
    roll = int(r[0])
    for c, v in zip(codes, r[1:]):
        assert results('r235', str(c))[roll - 1] == v.replace(chr(92), ''), (roll, c, v)
print('r235 ok')

means = {'A pie': 'on_foot', 'Comercial': 'commercial_vehicle', 'Deslizador': 'skimmer', 'Nave auxiliar': 'ships_boat',
         'Transbordador orbital': 'orbital_shuttle', 'Nave estelar': 'starship'}
n = 0
for r in mdrows(section('## r238 ')):
    if r[0] in means:
        assert results('r238', means[r[0]]) == r[1:], r
        n += 1
print('r238 ok:', n, 'rows')

n = 0
for r in mdrows(section('## r241 ')):
    if r[0].isdigit():
        assert results('r241', r[0]) == ['x' + v[1:].replace(' 1/2', '.5') for v in r[1:]], r
        n += 1
print('r241 ok:', n, 'rows')
