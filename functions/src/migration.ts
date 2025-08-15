// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier migration.ts
import * as functions from "firebase-functions/v2";
// === DÉBUT DE LA CORRECTION : Chemin d'importation corrigé ===
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
// === FIN DE LA CORRECTION ===
import * as admin from "firebase-admin";

// Assurez-vous que l'admin SDK est initialisé
try {
  admin.initializeApp();
} catch (e) {
  // Ignorer si déjà initialisé
}

const db = admin.firestore();
const functionOptions: functions.GlobalOptions = { region: "europe-west1" };

/**
 * Cloud Function de migration à usage unique.
 * Elle parcourt toutes les conversations et ajoute 'isArchived: false'
 * aux documents où ce champ est manquant.
 *
 * SÉCURITÉ : Cette fonction ne peut être appelée que par un administrateur
 * authentifié pour éviter toute exécution accidentelle ou malveillante.
 */
// === DÉBUT DE LA CORRECTION : Ajout du type explicite pour 'request' ===
export const migrateConversationsSchema = onCall(functionOptions, async (request: CallableRequest) => {
// === FIN DE LA CORRECTION ===
    // Vérification de sécurité : Seul un admin peut lancer cette migration.
    if (request.auth?.token?.admin !== true) {
        functions.logger.error(
            `Tentative non autorisée de migration par l'UID : ${request.auth?.uid}.`
        );
        throw new HttpsError(
            "permission-denied",
            "Action réservée aux administrateurs."
        );
    }

    functions.logger.log("Début de la migration du schéma des conversations.");

    const conversationsRef = db.collection("conversations");
    let documentsProcessed = 0;
    let documentsUpdated = 0;
    const BATCH_SIZE = 200; // Traiter 200 documents à la fois pour rester dans les limites

    try {
        const snapshot = await conversationsRef.get();
        if (snapshot.empty) {
            functions.logger.log("Aucune conversation à migrer.");
            return {
                status: "success",
                message: "Aucune conversation trouvée.",
                processed: 0,
                updated: 0
            };
        }

        let batch = db.batch();
        let batchCount = 0;

        for (const doc of snapshot.docs) {
            documentsProcessed++;
            const data = doc.data();

            // La condition clé : on ne met à jour que si le champ 'isArchived' n'existe pas.
            if (data.isArchived === undefined) {
                batch.update(doc.ref, { isArchived: false });
                documentsUpdated++;
                batchCount++;

                if (batchCount === BATCH_SIZE) {
                    // Le batch est plein, on l'exécute et on en crée un nouveau.
                    await batch.commit();
                    functions.logger.log(`Lot de ${batchCount} documents mis à jour exécuté.`);
                    batch = db.batch();
                    batchCount = 0;
                }
            }
        }

        // Exécuter le dernier batch s'il contient des opérations.
        if (batchCount > 0) {
            await batch.commit();
            functions.logger.log(`Dernier lot de ${batchCount} documents mis à jour exécuté.`);
        }

        const successMessage = `Migration terminée. ${documentsProcessed} documents traités, ${documentsUpdated} mis à jour.`;
        functions.logger.log(successMessage);

        return {
            status: "success",
            message: successMessage,
            processed: documentsProcessed,
            updated: documentsUpdated
        };

    } catch (error) {
        functions.logger.error("Erreur critique pendant la migration des conversations :", error);
        throw new HttpsError("internal", "Une erreur est survenue pendant la migration.");
    }
});