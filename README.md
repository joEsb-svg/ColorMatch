# ColorMatch

App Android per controllare il colore delle pulsantiere (Fenix, metalli, vetro…) in cabina luce,
confrontando il pezzo con un campione di riferimento e dando un esito **CONFORME / NON CONFORME**
basato sulla differenza colore ΔE00 (CIEDE2000), con soglia regolabile per materiale.

## Come si usa

1. **Calibra cabina** (una volta): luce accesa, cartoncino grigio neutro in cabina, salva il profilo.
   L'app memorizza esposizione, ISO e bilanciamento del bianco e li usa sempre uguali.
2. **Campioni di riferimento**: per ogni tipo di tasto + materiale + colore misura 3–5 volte un pezzo buono.
3. **Verifica pezzo**: scegli tipo, materiale, colore, inquadra il pezzo nel riquadro e premi MISURA.

## Come funziona la misura

- Analizza solo il riquadro centrale.
- Converte ogni pixel in CIE Lab e scarta i pixel bruciati e quelli troppo lontani dalla luminosità
  mediana (riflessi speculari, ombre, polvere): utile per Fenix e metalli.
- Prende la mediana su 24 fotogrammi consecutivi.
- Calcola ΔE00 rispetto al campione e indica se il pezzo è più chiaro/scuro, più rosso/verde, più giallo/blu.

I valori Lab sono relativi al telefono e alla cabina, non sono letture spettrofotometriche assolute:
campioni e verifiche vanno fatti con lo stesso telefono e la stessa calibrazione.

## Build

Ogni push su `main` compila l'APK con GitHub Actions e lo pubblica nelle **Releases**.
