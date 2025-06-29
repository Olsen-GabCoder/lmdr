import * as admin from "firebase-admin";

// Initialisation globale de l'SDK Admin. À ne faire qu'une fois.
admin.initializeApp();

// Importe et ré-exporte toutes les fonctions depuis leurs fichiers respectifs.
// L'opérateur "..." fusionne les objets exportés.
export * from "./monthlyReadings";
export * from "./privateMessasging"