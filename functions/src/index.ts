// PRÊT À COLLER - Modifiez votre fichier `index.ts`

import * as admin from "firebase-admin";

// Initialisation globale de l'SDK Admin.
try {
  admin.initializeApp();
} catch (e) {
  // Ignorer si déjà initialisé
}

// Exporter les fonctions existantes
export * from "./monthlyReadings";
export * from "./privateMessaging";
export * from "./socialInteractions";
export * from "./migration";
export * from "./users";


// JUSTIFICATION: Nous exportons les fonctions du nouveau fichier `books.ts`.
// Cela rendra notre nouvelle fonction `createBookWithFiles` disponible et déployable.
export * from "./books";
