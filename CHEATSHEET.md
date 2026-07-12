# CHEATSHEET — komande, tasteri, miš (za odbranu)

Za `_practice` (ima sve modove). Glavni projekat ima podskup.
U PowerShell-u OBAVEZNO navodnici oko `-D` argumenata: `java "-Dmod.x2=true" ...`.
Iz `cmd`/`.bat` bez navodnika. Uvek prvo `build.bat` pa pokretanje.

## Pokretanje

| Komanda | Šta radi |
|---|---|
| `build.bat` | kompajlira sve u `out\` |
| `run.bat` | normalno (svi modovi ugašeni = kao osnovni projekat) |
| `run-debug.bat` | + debug log + HUD + jump-to-time tasteri |
| `run-mods.bat` | svi modovi UKLJUČENI + debug |
| `selftest.bat` | headless test (bez GUI): 16 provera |
| `java "-Dmod.x2=true" -cp out app.Main` | jedan mod |
| `java "-Dmod.c2=true" "-Dmod.w2=true" -cp out app.Main` | više modova |
| `java "-Dmods=all" -cp out app.Main` | svi modovi |
| `java "-Dapp.debug=true" -cp out app.Main` | debug alati |

## Debug alati (samo uz -Dapp.debug=true)

| Alat | Kako |
|---|---|
| Debug HUD | automatski, gore desno: state / sim time / speed / u vazduhu / sleteli / zoom |
| Log klika | automatski u konzoli: `plane BEG-JFK [left]` / `airport FRA [ctrl left]` / `empty map` |
| Skok +1h | **PAGE_UP** (klikni mapu za fokus prvo) |
| Skok +6h | **END** |
| (samo unapred; za nazad koristi X23 rewind dugmad) | |

## Miš

| Radnja | Efekat | Mod |
|---|---|---|
| levi klik na avion | selekcija / info / linija / trepere krajevi... | C1/W1/X24-27 |
| levi klik na aerodrom | selekcija (trepće crveno) | osnovno |
| levi klik na prazno | naoruža pravougaonik | B2 |
| pomeranje miša (posle B2 klika) | preview pravougaonika | B2 |
| SHIFT + levi klik na avion | redirect preko DIJALOGA | W4 |
| CTRL + levi klik na avion | naoruža → sledeći klik na aerodrom = redirect | W4 |
| CTRL + levi klik na aerodrom | naoruža → klik na drugi = zatvori; na zatvoren = otvori | W3 |
| desni klik + DRŽI na prazno | oluja koja raste dok držiš | C2 |
| desni klik na oluju | briše oluju | C2 |
| SHIFT + desni klik | magla (krije avione, ne fiziku) | X15 |
| točkić miša | zum oko kursora | X21 |
| dvoklik na red u tabeli letova | prioritet leta (zvezdica) | X7 |

## Tastatura

| Taster | Efekat | Mod |
|---|---|---|
| E (sa selektovanim avionom) | emergency → najbliži otvoren aerodrom | X18 |
| strelice | pomeranje mape (pan) | X21 |
| HOME | reset zuma/pana | X21 |
| PAGE_UP / END | skok +1h / +6h (debug) | debug |

## Dugmad u traci

| Dugme | Mod |
|---|---|
| Start / Pause / Reset | osnovno |
| 0.5x / 1x / 2x / 5x | X8 (brzina) |
| -2x / -1x / +1x / +2x | X23 (rewind, ide i unazad) |
| Close airport... | W3 (selektuj aerodrom prvo) |
| Delay +60 min | X4 (selektuj aerodrom prvo) |
| Save report... | X19 (CSV izveštaj) |

## Klik-na-avion spektar (isti obrazac, biraj modovima)

C1 info kutija · W1 linija rute · X24 krajevi trepere · X25 fokus (ostali sivi) ·
X26 avion trepće · X27 isti-aerodrom zeleni · W4 redirect · X18 emergency (E)

## Klik-na-aerodrom spektar

selekcija+blink · W2 konektivnost (2 nivoa) · W3 zatvori · X4 delay · X17 bilans

## Puna lista mod flagova

Vidi MODS.md. Ukratko: b2 c1 c2 w1 w2 w3 w4 x1 x2 x3 x4 x5 x6 x7 x8 x10
x12 x13 x14 x15 x16 x17 x18 x19 x20 x21 x22 x23 x24 x25 x26 x27.
(x9 vetar i x11 gorivo NISU implementirani.)
