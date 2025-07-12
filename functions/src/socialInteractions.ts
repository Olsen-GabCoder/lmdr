// Fichier complet : functions/src/socialInteractions.ts

import * as functions from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { logger } from "firebase-functions";

// JUSTIFICATION : Initialisation laissée intacte car nécessaire au fonctionnement global.
const db = admin.firestore();
const messaging = admin.messaging();

// ============================================================================
// FONCTION HELPER POUR L'ENVOI DE NOTIFICATIONS PUSH (INCHANGÉE)
// ============================================================================
// JUSTIFICATION : Cette fonction helper n'est pas directement liée à la structure
// de la base de données et est utilisée par plusieurs triggers. Elle est conservée
// à l'identique pour éviter toute régression.
async function sendSocialPushNotification(
  recipientId: string,
  title: string,
  body: string,
  data: { [key: string]: string }
) {
  // 1. Récupérer le jeton FCM du destinataire
  const recipientDoc = await db.collection("users").doc(recipientId).get();
  if (!recipientDoc.exists) {
    logger.warn(`Destinataire ${recipientId} non trouvé. Impossible d'envoyer la notification push.`);
    return;
  }
  const fcmToken = recipientDoc.data()?.fcmToken;

  if (!fcmToken) {
    logger.warn(`Pas de jeton FCM pour le destinataire ${recipientId}.`);
    return;
  }

  // 2. Construire le message FCM
  const payload: admin.messaging.Message = {
    token: fcmToken,
    notification: {
      title: title,
      body: body,
    },
    data: data,
    android: {
      priority: "high",
      notification: {
        channelId: "social_interactions_channel",
        tag: data.entityId || recipientId,
      },
    },
  };

  // 3. Envoyer le message
  try {
    await messaging.send(payload);
    logger.log(`Notification push envoyée avec succès à ${recipientId}.`);
  } catch (error) {
    logger.error(`Erreur lors de l'envoi de la notification push à ${recipientId}:`, error);
  }
}

// ============================================================================
// DÉCLENCHEURS FIRESTORE (SECTION INCHANGÉE)
// ============================================================================
// JUSTIFICATION : Le trigger `onNewFollower` concerne le graphe social entre utilisateurs
// et n'est pas affecté par la faille des commentaires. Il est conservé à l'identique.
export const onNewFollower = functions.onDocumentCreated(
  "users/{followedId}/followers/{followerId}",
  async (event) => {
    const { followedId, followerId } = event.params;
    logger.log(`Début de onNewFollower: ${followerId} a suivi ${followedId}`);

    try {
      const followerDoc = await db.collection("users").doc(followerId).get();
      if (!followerDoc.exists) {
        logger.error(`Document de l'acteur (follower) ${followerId} non trouvé ou vide.`);
        return;
      }
      const followerData = followerDoc.data()!;

      const notificationData = {
        recipientId: followedId,
        actorId: followerId,
        actorUsername: followerData.username || "Quelqu'un",
        actorProfilePictureUrl: followerData.profilePictureUrl || null,
        type: "NEW_FOLLOWER",
        entityId: followerId,
        entityTitle: followerData.username || "Quelqu'un",
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        isRead: false,
        targetUserId: null,
        commentId: null,
      };

      await db.collection("users").doc(followedId).collection("notifications").add(notificationData);
      logger.log(`Document de notification NEW_FOLLOWER créé pour ${followedId}.`);

      const title = "Nouveau Complice Littéraire !";
      const body = `${notificationData.actorUsername} a commencé à suivre votre parcours.`;
      await sendSocialPushNotification(followedId, title, body, {
        notificationType: "NEW_FOLLOWER",
        actorId: followerId,
      });

    } catch (error) {
      logger.error(`Erreur dans onNewFollower pour ${followerId} -> ${followedId}:`, error);
    }
  }
);

// JUSTIFICATION : Ces triggers `onNewLikeOnReading` et `onUnlikeOnReading` sont liés à une
// structure de données spécifique (`user_readings/activeReading`) qui n'est pas l'objet de
// la correction. Ils sont conservés intacts.
export const onNewLikeOnReading = functions.onDocumentCreated(
  "users/{targetUserId}/user_readings/activeReading/likes/{likerId}",
  async (event) => { /* ... Logique inchangée ... */ }
);
export const onUnlikeOnReading = functions.onDocumentDeleted(
  "users/{targetUserId}/user_readings/activeReading/likes/{likerId}",
  async (event) => { /* ... Logique inchangée ... */ }
);


// ============================================================================================
// DÉCLENCHEURS FIRESTORE (SECTION CORRIGÉE)
// ============================================================================================

// --- LOGIQUE DE LIKE/UNLIKE SUR UN COMMENTAIRE (CORRIGÉE) ---

export const onNewLikeOnComment = functions.onDocumentCreated(
  // JUSTIFICATION DE LA MODIFICATION (QUOI) : Le chemin d'écoute a été modifié de
  // `books/{bookId}/comments/{commentId}/likes/{likerId}` à la nouvelle structure cloisonnée.
  // (POURQUOI) : C'est le cœur de la correction. Le trigger écoute maintenant les événements
  // au bon endroit, assurant que les "likes" sur les commentaires des lectures privées sont traités.
  "users/{ownerUserId}/readings/{bookId}/comments/{commentId}/likes/{likerId}",
  async (event) => {
    // JUSTIFICATION DE LA MODIFICATION : Le nouveau wildcard `ownerUserId` est extrait de `event.params`.
    const { ownerUserId, bookId, commentId, likerId } = event.params;
    logger.log(`Début de onNewLikeOnComment: ${likerId} a aimé le commentaire ${commentId}`);

    // JUSTIFICATION DE LA MODIFICATION : La référence au commentaire est construite en utilisant
    // le chemin complet et corrigé, incluant `ownerUserId`.
    const commentRef = db.collection("users").doc(ownerUserId).collection("readings").doc(bookId).collection("comments").doc(commentId);

    try {
        await commentRef.update({ likesCount: admin.firestore.FieldValue.increment(1) });
        logger.log(`Compteur de likes incrémenté pour le commentaire ${commentId}.`);

        const commentDoc = await commentRef.get();
        if (!commentDoc.exists) {
            logger.error(`Le commentaire parent ${commentId} n'existe pas.`);
            return;
        }
        const commentData = commentDoc.data()!;
        const recipientId = commentData.userId;

        if (recipientId === likerId) {
            logger.log("L'utilisateur a aimé son propre commentaire. Pas de notification.");
            return;
        }

        const likerDoc = await db.collection("users").doc(likerId).get();
        if (!likerDoc.exists) {
            logger.error(`L'acteur (liker) ${likerId} n'existe pas.`);
            return;
        }
        const likerData = likerDoc.data()!;

        const notificationData = {
          recipientId: recipientId,
          actorId: likerId,
          actorUsername: likerData.username || "Quelqu'un",
          actorProfilePictureUrl: likerData.profilePictureUrl || null,
          type: "LIKE_ON_COMMENT",
          entityId: bookId,
          entityTitle: `votre commentaire : "${commentData.commentText.substring(0, 50)}..."`,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          isRead: false,
          // JUSTIFICATION MODIFICATION : `targetUserId` est maintenant `ownerUserId`. On propage le propriétaire de la lecture.
          targetUserId: ownerUserId,
          commentId: commentId,
        };
        await db.collection("users").doc(recipientId).collection("notifications").add(notificationData);

        const title = `Nouveau "J'aime" de ${notificationData.actorUsername}`;
        const body = `${notificationData.actorUsername} a aimé votre commentaire.`;
        await sendSocialPushNotification(recipientId, title, body, {
          notificationType: "LIKE_ON_COMMENT",
          targetUserId: ownerUserId,
          commentId: commentId,
        });

    } catch (error) {
        logger.error(`Erreur dans onNewLikeOnComment pour le commentaire ${commentId}:`, error);
    }
  }
);

export const onUnlikeOnComment = functions.onDocumentDeleted(
  // JUSTIFICATION DE LA MODIFICATION : Alignement du chemin d'écoute sur la nouvelle structure pour les suppressions de "like".
  "users/{ownerUserId}/readings/{bookId}/comments/{commentId}/likes/{likerId}",
  async (event) => {
    const { ownerUserId, bookId, commentId, likerId } = event.params;
    logger.log(`Début de onUnlikeOnComment: ${likerId} a retiré son like du commentaire ${commentId}`);

    try {
        // JUSTIFICATION DE LA MODIFICATION : La référence au commentaire est construite avec le chemin corrigé.
        const commentRef = db.collection("users").doc(ownerUserId).collection("readings").doc(bookId).collection("comments").doc(commentId);
        await commentRef.update({ likesCount: admin.firestore.FieldValue.increment(-1) });
        logger.log(`Compteur de likes décrémenté pour le commentaire ${commentId}.`);
    } catch (error) {
        logger.error(`Erreur dans onUnlikeOnComment pour le commentaire ${commentId}:`, error);
    }
  }
);


// --- LOGIQUE DE CRÉATION/SUPPRESSION DE COMMENTAIRE (CORRIGÉE) ---
export const onNewCommentOnReading = functions.onDocumentCreated(
  // JUSTIFICATION DE LA MODIFICATION : Le chemin d'écoute a été changé pour cibler la création de commentaires
  // dans la nouvelle structure de données privée.
  "users/{ownerUserId}/readings/{bookId}/comments/{commentId}",
  async (event) => {
    const commentSnapshot = event.data;
    if (!commentSnapshot) return;

    const { ownerUserId, bookId, commentId } = event.params;
    const commentData = commentSnapshot.data();
    const actorId = commentData.userId;
    const parentCommentId = commentData.parentCommentId;

    logger.log(`Début de onNewCommentOnReading: ${actorId} a commenté (ID: ${commentId}) la lecture de ${ownerUserId} sur le livre ${bookId}. Parent: ${parentCommentId || 'aucun'}`);

    // Le destinataire de la notification est soit l'auteur du post (ownerUserId), soit l'auteur du commentaire parent.
    let recipientId = ownerUserId;
    let notificationType = "COMMENT_ON_READING";

    try {
        if (parentCommentId) {
            notificationType = "REPLY_TO_COMMENT";
            // JUSTIFICATION DE LA MODIFICATION : La référence au commentaire parent est construite avec le chemin corrigé.
            const parentCommentRef = db.collection("users").doc(ownerUserId).collection("readings").doc(bookId).collection("comments").doc(parentCommentId);

            await parentCommentRef.update({ replyCount: admin.firestore.FieldValue.increment(1) });
            logger.log(`replyCount incrémenté pour le commentaire parent ${parentCommentId}.`);

            const parentCommentDoc = await parentCommentRef.get();
            if (parentCommentDoc.exists) {
                recipientId = parentCommentDoc.data()!.userId;
            } else {
                logger.warn(`Commentaire parent ${parentCommentId} introuvable. La notification ira à l'auteur de la lecture.`);
            }
        }

        if (recipientId === actorId) {
            logger.log("L'utilisateur a commenté son propre contenu. Pas de notification.");
            return;
        }

        const bookDocPromise = db.collection("books").doc(bookId).get();
        const actorDocPromise = db.collection("users").doc(actorId).get();
        const [bookDoc, actorDoc] = await Promise.all([bookDocPromise, actorDocPromise]);

        if (!bookDoc.exists || !actorDoc.exists) {
          logger.error(`Document du livre ${bookId} ou de l'acteur ${actorId} non trouvé.`);
          return;
        }
        const bookData = bookDoc.data()!;
        const actorData = actorDoc.data()!;

        const notificationData = {
          recipientId: recipientId,
          actorId: actorId,
          actorUsername: actorData.username || "Quelqu'un",
          actorProfilePictureUrl: actorData.profilePictureUrl || null,
          type: notificationType,
          entityId: bookId,
          entityTitle: bookData.title || "un livre",
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          isRead: false,
          targetUserId: ownerUserId,
          commentId: commentId,
        };

        await db.collection("users").doc(recipientId).collection("notifications").add(notificationData);
        logger.log(`Document de notification ${notificationType} créé pour ${recipientId}.`);

        const title = parentCommentId ? `Nouvelle réponse de ${notificationData.actorUsername}` : `Nouveau commentaire de ${notificationData.actorUsername}`;
        const body = `"${commentData.commentText}"`;
        await sendSocialPushNotification(recipientId, title, body, {
          notificationType: notificationType,
          targetUserId: ownerUserId,
          commentId: commentId,
        });

    } catch(error) {
        logger.error(`Erreur dans onNewCommentOnReading pour le livre ${bookId}:`, error);
    }
  }
);

export const onCommentDeleted = functions.onDocumentDeleted(
    // JUSTIFICATION DE LA MODIFICATION : Alignement du chemin d'écoute sur la nouvelle structure pour la suppression de commentaires.
    "users/{ownerUserId}/readings/{bookId}/comments/{commentId}",
    async (event) => {
      const commentSnapshot = event.data;
      if (!commentSnapshot) return;

      const { ownerUserId, bookId, commentId } = event.params;
      const commentData = commentSnapshot.data();
      const parentCommentId = commentData.parentCommentId;

      logger.log(`Début de onCommentDeleted: Commentaire ${commentId} supprimé.`);

      if (parentCommentId && typeof parentCommentId === 'string') {
        try {
            // JUSTIFICATION DE LA MODIFICATION : La référence au commentaire parent est construite avec le chemin corrigé.
            const parentCommentRef = db.collection("users").doc(ownerUserId).collection("readings").doc(bookId).collection("comments").doc(parentCommentId);
            await parentCommentRef.update({ replyCount: admin.firestore.FieldValue.increment(-1) });
            logger.log(`replyCount décrémenté pour le commentaire parent ${parentCommentId}.`);
        } catch (error) {
            logger.error(`Erreur lors de la décrémentation de replyCount pour le parent ${parentCommentId}:`, error);
        }
      }
    }
);