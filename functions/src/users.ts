// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier users.ts
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";

/**
 * Interface pour typer les données attendues par la fonction setUserRole.
 */
interface SetUserRoleData {
  email: string;
  isAdmin: boolean;
}

/**
 * Cloud Function appelable (v2) pour définir le rôle d'un utilisateur.
 *
 * Sécurisée : requiert que l'appelant soit un administrateur.
 *
 * @param request - L'objet de la requête contenant les données (`data`) et le contexte d'authentification (`auth`).
 * @returns Un objet indiquant le succès ou l'échec de l'opération.
 */
export const setUserRole = onCall({ region: "europe-west1" }, async (request) => {
  // Vérification de sécurité n°1 : L'utilisateur qui appelle la fonction est-il un admin ?
  if (request.auth?.token?.admin !== true) {
    logger.error(
      `Tentative non autorisée de modification de rôle par l'UID : ${request.auth?.uid}.`
    );
    throw new HttpsError(
      "permission-denied",
      "Action réservée aux administrateurs."
    );
  }

  // Utiliser le type que nous avons défini pour les données
  const data: SetUserRoleData = request.data;
  const targetEmail = data.email;
  const isAdmin = data.isAdmin === true;

  if (!targetEmail || typeof targetEmail !== "string") {
    throw new HttpsError(
      "invalid-argument",
      "L'adresse e-mail de l'utilisateur cible est requise."
    );
  }

  try {
    const userRecord = await admin.auth().getUserByEmail(targetEmail);

    await admin.auth().setCustomUserClaims(userRecord.uid, { admin: isAdmin });

    logger.info(
      `Rôle de ${targetEmail} (UID: ${userRecord.uid}) mis à jour. isAdmin: ${isAdmin}. Action par: ${request.auth?.uid}`
    );

    return {
      status: "success",
      message: `Le rôle de l'utilisateur ${targetEmail} a été mis à jour avec succès.`,
    };
  } catch (error: any) { // Typage explicite de l'erreur
    logger.error(
      `Erreur lors de la mise à jour du rôle pour ${targetEmail}:`,
      error
    );
    if (error.code === "auth/user-not-found") {
        throw new HttpsError("not-found", "Aucun utilisateur trouvé avec cette adresse e-mail.");
    }
    throw new HttpsError(
      "internal",
      "Une erreur interne est survenue lors de la mise à jour du rôle."
    );
  }
});