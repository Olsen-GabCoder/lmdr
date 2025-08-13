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
export * from "./books";

// === DÉBUT DE L'AJOUT ===
// JUSTIFICATION : Nous exportons les fonctions du nouveau fichier `social.ts`.
// Cela rendra notre nouvelle fonction `getMutualContacts` disponible et déployable.
export * from "./social";
// === FIN DE L'AJOUT ===