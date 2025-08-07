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

// JUSTIFICATION DE L'AJOUT : Nous exportons les fonctions du nouveau fichier `users.ts`.
// Cela rendra notre nouvelle fonction `setUserRole` disponible et déployable sur Firebase.
export * from "./users";