// PRÊT À COLLER - Fichier index.ts mis à jour
import * as admin from "firebase-admin";

// Initialisation globale de l'SDK Admin.
try {
  admin.initializeApp();
} catch (e) {

}


export * from "./monthlyReadings";
export * from "./privateMessaging";
export * from "./socialInteractions";