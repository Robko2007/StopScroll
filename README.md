# StopScroll

Android aplikácia, ktorá ťa vyhodí z **Instagram Reels** a **YouTube Shorts**, keď ich pozeráš dlhšie ako 5 sekúnd (limit si môžeš zmeniť na 1 až 30 s).

Funguje ako **služba prístupnosti**: vidí, aká obrazovka je otvorená v Instagrame alebo YouTube. Keď rozpozná prehrávač Reels alebo Shorts a ty si tam nepretržite dlhšie ako limit, appka stlačí **Späť** (aby si sa po návrate neocitol znova v Reels) a potom **Domov**. Iné aplikácie nemôže úplne „zabiť“ (to Android bez rootu nedovolí), ale vyhodenie na plochu má rovnaký efekt. Bežný Instagram (feed, správy, stories) a bežný YouTube (normálne videá) fungujú ďalej.

Nič neodosiela na internet a nepotrebuje žiadne iné povolenia.

---

## Ako z toho dostať APK (vyber si jednu cestu)

### Cesta A: Android Studio (na počítači)
1. Nainštaluj si **Android Studio** z developer.android.com/studio (je zadarmo).
2. Rozbaľ tento ZIP a v Android Studiu daj **File → Open** a vyber priečinok `StopScroll`.
3. Počkaj, kým sa dole dokončí „Gradle sync“ (prvýkrát to môže trvať niekoľko minút, sťahujú sa nástroje).
4. **Najjednoduchšie:** pripoj mobil cez USB (s povoleným *USB ladením*) a klikni na zelené ▶ (Run). Appka sa nainštaluje rovno do mobilu.
   **Alebo:** v menu daj **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. Súbor nájdeš v `app/build/outputs/apk/debug/app-debug.apk`. Pošli si ho do mobilu a nainštaluj.

### Cesta B: GitHub (bez inštalácie čohokoľvek na počítač)
1. Na github.com si vytvor nový repozitár a nahraj doň obsah priečinka `StopScroll` (aj skrytý priečinok `.github`).
2. Otvor kartu **Actions**. Zostavenie sa spustí samo, prípadne ho spustíš tlačidlom „Run workflow“.
3. Po pár minútach klikni na hotový beh a dole v časti **Artifacts** si stiahni `StopScroll-apk`. Je to ZIP, v ktorom je `app-debug.apk`.
4. APK si pošli do mobilu a nainštaluj (mobil sa môže opýtať na povolenie inštalácie z neznámych zdrojov).

---

## Zapnutie v mobile

1. Otvor appku **StopScroll** a klikni na **„Zapnúť v nastaveniach prístupnosti“**.
2. Nájdi tam **StopScroll** (často v časti *Stiahnuté aplikácie* alebo *Nainštalované služby*) a zapni ho.
3. **Ak je prepínač sivý** (Android 13 a novší pri appkách nainštalovaných mimo Obchodu Play):
   choď do **Nastavenia → Aplikácie → StopScroll**, vpravo hore klikni na **⋮** a zvoľ **„Povoliť obmedzené nastavenia“**. Potom sa vráť a zapni službu.
4. Pri telefónoch Xiaomi, Huawei, Oppo, Samsung a podobne je dobré pre StopScroll vypnúť **optimalizáciu batérie**, aby ho systém nevypínal.

Ak je v appke zelené **„✅ Služba je zapnutá“**, je hotovo.

---

## Keď to Reels alebo Shorts nerozpozná

Instagram a YouTube pri aktualizáciách občas premenujú vnútorné časti obrazovky. Appka spoznáva Reels a Shorts podľa týchto názvov (ID).

1. V StopScroll zapni **Režim ladenia**.
2. Otvor Reels alebo Shorts aspoň na 3 sekundy.
3. Vráť sa do StopScroll a klikni na **Kopírovať**.
4. Medzi ID nájdi niečo, čo vyzerá ako prehrávač (napr. obsahuje `clips`, `reel` alebo `shorts`) a doplň to do zoznamu `IG_IDS` alebo `YT_IDS` v súbore
   `app/src/main/java/sk/stopscroll/BlockerService.kt`. Potom appku znova zostav.

## Štruktúra projektu

| Súbor | Čo robí |
|---|---|
| `BlockerService.kt` | Srdce appky: sleduje obrazovku, odpočítava čas a blokuje |
| `MainActivity.kt` + `activity_main.xml` | Obrazovka s nastaveniami, stavom a štatistikou |
| `Prefs.kt` | Ukladanie nastavení |
| `res/xml/accessibility_config.xml` | Nastavenie služby prístupnosti (sleduje len Instagram a YouTube) |
