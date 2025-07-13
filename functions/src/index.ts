// Fichier Modifié : functions/src/index.ts

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

// JUSTIFICATION DE L'AJOUT : La nouvelle fonction de migration est exportée
// ici pour qu'elle soit reconnue et déployée par Firebase.
export * from "./migration";