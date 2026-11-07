# Aplikacija za telefon – uputstvo

## U uredu (program Evidencija radnog vremena)
1. Otvori bazu i idi u karticu **Korisnici i lozinka**, dio **Telefoni (aplikacija za Android)**.
2. Upiši **mail za prijem podataka**, zatim **ime i prezime osobe** i klikni **Napravi paket za telefon**.
3. Program snimi datoteku `Paket_Ime_Prezime.evp` i pokaže **aktivacijski kod** (npr. `K7Q2-M9PL`).
4. Osobi pošalji (WhatsApp ili mail) **APK aplikacije** i **paket**. **Kod javi odvojeno**, npr. telefonom ili SMS-om.
5. Novi spiskovi (novi radnici ili projekti): u tabeli klikni **Novi paket (osvježeni spiskovi)** i pošalji ga osobi s novim kodom.
6. Ako se telefon izgubi ili osoba više ne radi: klikni **Opozovi**. Podaci s tog telefona se više ne uvoze.

## Na telefonu
1. **Instalacija:** otvori APK iz WhatsAppa ili maila. Android pita za dozvolu „Instaliraj nepoznate aplikacije“ za WhatsApp ili Gmail; dozvoli jednom.
2. **Aktivacija:**
   - Otvori aplikaciju **Evidencija** i dodirni **Učitaj paket (.evp)**. Paket možeš i podijeliti iz WhatsAppa u aplikaciju.
   - Upiši aktivacijski kod i postavi **PIN** (4–8 cifara).
3. **Upis:**
   - Izaberi projekat; firma se popuni sama.
   - Označi jednog ili više radnika i upiši datum, početak, kraj i pauzu.
   - Dodirni **Snimi upis**: za svakog označenog radnika nastaje jedan upis.
4. **Spiskovi:** ovdje dodaješ nove radnike, firme i projekte. Oni idu u ured s upisima.
5. **Upisi:** pregled neposlanih upisa (Uredi, Obriši) i poslanih upisa.
6. **Pošalji:**
   - Po želji dodaj **sliku dnevnog izvještaja** (📷 Slikaj ili 🖼 Iz galerije).
   - Dodirni **Pošalji mailom**. Otvori se Gmail s adresom, naslovom i prilozima; pritisni **Pošalji**.
   - Vrati se u aplikaciju i potvrdi **Da, poslan je**.
7. **Izlaz:** ako ima neposlanih podataka, aplikacija prvo nudi slanje.

U mailu je datoteka `.evt`, a ako je dodana slika, i `.jpg`. Datoteka `.evt` je šifrovana: otvara je samo program u uredu, a ne Excel ni neko drugi.

## Uvoz u uredu
1. Kartica **Radno vrijeme** › **Uvoz s telefona** › izaberi jednu ili više `.evt` datoteka iz maila.
2. Program pokaže ko je unio podatke (potpis), upise i nove radnike, firme i projekte. Klikni **Uvezi u bazu**.
3. Upisi koji već postoje se preskaču, pa se ista datoteka može uvesti ponovo bez dupliranja.

## Važno
- **Ključ je u bazi.** Podatke s telefona otvara samo baza iz koje je napravljen paket. Ne prelazi na drugu (praznu) bazu bez ključa.
- **Zaboravljen PIN:** u aplikaciji izaberi „Nova aktivacija“ i učitaj novi paket. Neposlani upisi na tom telefonu se tada gube.
- **Potpis APK-a:** `keystore\evidencija.jks` i `keystore\LOZINKA_KEYSTORE.txt` čuvaj. Bez njih nova verzija aplikacije ne može zamijeniti staru na telefonima.
