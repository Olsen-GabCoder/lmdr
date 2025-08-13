// CRÉEZ UN NOUVEAU FICHIER : functions/src/social.ts
import * as functions from "firebase-functions/v2";
import { onCall, HttpsError } from "firebase-functions/v2/https";
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
 * Récupère la liste complète des contacts mutuels (utilisateurs qui se suivent réciproquement).
 * Cette fonction optimise drastiquement les lectures côté client en effectuant les calculs côté serveur.
 */
export const getMutualContacts = onCall(functionOptions, async (request) => {
    const currentUserId = request.auth?.uid;
    if (!currentUserId) {
        throw new HttpsError("unauthenticated", "L'utilisateur doit être authentifié pour effectuer cette action.");
    }

    try {
        functions.logger.log(`Recherche des contacts mutuels pour l'utilisateur: ${currentUserId}`);

        // Étape 1: Récupérer les IDs des utilisateurs que l'on suit (following)
        const followingSnapshot = await db.collection("users").doc(currentUserId).collection("following").get();
        const followingIds = new Set(followingSnapshot.docs.map(doc => doc.id));

        if (followingIds.size === 0) {
            functions.logger.log("L'utilisateur ne suit personne, aucun contact mutuel possible.");
            return { users: [] };
        }

        // Étape 2: Récupérer les IDs des utilisateurs qui nous suivent (followers)
        const followersSnapshot = await db.collection("users").doc(currentUserId).collection("followers").get();
        const followerIds = new Set(followersSnapshot.docs.map(doc => doc.id));

        if (followerIds.size === 0) {
            functions.logger.log("L'utilisateur n'a aucun follower, aucun contact mutuel possible.");
            return { users: [] };
        }

        // Étape 3: Calculer l'intersection des IDs pour trouver les contacts mutuels
        const mutualIds = [...followingIds].filter(id => followerIds.has(id));

        if (mutualIds.length === 0) {
            functions.logger.log("Aucun ID de contact mutuel trouvé.");
            return { users: [] };
        }

        // Étape 4: Récupérer les documents complets des utilisateurs mutuels
        // Firestore limite les requêtes `whereIn` à 30 éléments par requête.
        const userChunks = [];
        for (let i = 0; i < mutualIds.length; i += 30) {
            userChunks.push(mutualIds.slice(i, i + 30));
        }

        const userPromises = userChunks.map(chunk =>
            db.collection("users").where(admin.firestore.FieldPath.documentId(), "in", chunk).get()
        );

        const userSnapshots = await Promise.all(userPromises);

        const mutualUsers: any[] = [];
        userSnapshots.forEach(snapshot => {
            snapshot.forEach(doc => {
                const userData = doc.data();
                // On s'assure de renvoyer un objet propre et conforme au modèle User
                mutualUsers.push({
                    uid: doc.id,
                    username: userData.username || "",
                    profilePictureUrl: userData.profilePictureUrl || null,
                    isOnline: userData.isOnline || false,
                    // Ajoutez d'autres champs si nécessaire pour l'affichage
                });
            });
        });

        functions.logger.log(`Trouvé ${mutualUsers.length} contact(s) mutuel(s) pour ${currentUserId}.`);

        return { users: mutualUsers };

    } catch (error) {
        functions.logger.error(`Erreur dans getMutualContacts pour l'utilisateur ${currentUserId}:`, error);
        throw new HttpsError("internal", "Une erreur est survenue lors de la récupération des contacts.");
    }
});