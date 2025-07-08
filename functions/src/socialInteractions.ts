// PRÊT À COLLER - Fichier socialInteractions.ts - VERSION CORRIGÉE (Linter)
import * as functions from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { logger } from "firebase-functions";

const db = admin.firestore();
const messaging = admin.messaging();

// ============================================================================
// FONCTION HELPER POUR L'ENVOI DE NOTIFICATIONS PUSH
// ============================================================================

/**
 * Envoie une notification push à un utilisateur spécifique.
 */
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
// DÉCLENCHEURS FIRESTORE POUR LES INTERACTIONS SOCIALES
// ============================================================================

/**
 * Déclenché lorsqu'un utilisateur en suit un autre. (Inchangé)
 */
export const onNewFollower = functions.onDocumentCreated(
  "users/{followedId}/followers/{followerId}",
  async (event) => {
    // ... (Logique existante de onNewFollower)
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
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        isRead: false,
      };

      await db.collection("users").doc(followedId).collection("notifications").add(notificationData);
      logger.log(`Document de notification NEW_FOLLOWER créé pour ${followedId}.`);

      const title = "Nouveau Complice Littéraire !";
      const body = `${notificationData.actorUsername} a commencé à suivre votre parcours.`;
      await sendSocialPushNotification(followedId, title, body, {
        notificationType: "NEW_FOLLOWER",
        actorId: followerId,
        entityId: followerId,
      });

    } catch (error) {
      logger.error(`Erreur dans onNewFollower pour ${followerId} -> ${followedId}:`, error);
    }
  }
);


// --- LOGIQUE DE LIKE/UNLIKE SUR UNE LECTURE (Inchangé) ---

export const onNewLikeOnReading = functions.onDocumentCreated(
  "users/{targetUserId}/user_readings/activeReading/likes/{likerId}",
  async (event) => {
    // ... (Logique existante de onNewLikeOnReading)
    const likeSnapshot = event.data;
    if (!likeSnapshot) {
      logger.error("Le snapshot du like est vide, la fonction ne peut pas s'exécuter.");
      return;
    }

    const { targetUserId, likerId } = event.params;
    const likeData = likeSnapshot.data();
    const bookId = likeData.bookId;

    logger.log(`Début de onNewLikeOnReading: ${likerId} a aimé la lecture de ${targetUserId} (livre: ${bookId})`);

    if (!bookId || typeof bookId !== "string") {
        logger.error("Le document de like ne contient pas de 'bookId' valide.", likeData);
        return;
    }

    if (targetUserId === likerId) {
      logger.log("L'utilisateur a aimé sa propre lecture. Mise à jour du compteur uniquement.");
    }

    try {
      const bookRef = db.collection("books").doc(bookId);
      await bookRef.update({ likesCount: admin.firestore.FieldValue.increment(1) });
      logger.log(`Compteur de likes incrémenté pour le livre ${bookId}.`);

      if (targetUserId !== likerId) {
        const likerDocPromise = db.collection("users").doc(likerId).get();
        const bookDocPromise = db.collection("books").doc(bookId).get();
        const [likerDoc, bookDoc] = await Promise.all([likerDocPromise, bookDocPromise]);

        if (!likerDoc.exists || !bookDoc.exists) {
            logger.error(`Document de l'acteur ${likerId} ou du livre ${bookId} non trouvé.`);
            return;
        }

        const likerData = likerDoc.data()!;
        const bookData = bookDoc.data()!;

        const notificationData = {
          recipientId: targetUserId,
          actorId: likerId,
          actorUsername: likerData.username || "Quelqu'un",
          actorProfilePictureUrl: likerData.profilePictureUrl || null,
          type: "LIKE_ON_READING",
          entityId: bookId,
          entityTitle: bookData.title || "votre lecture",
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          isRead: false,
        };
        await db.collection("users").doc(targetUserId).collection("notifications").add(notificationData);

        const title = "Votre lecture a été appréciée !";
        const body = `${notificationData.actorUsername} a aimé votre lecture de "${notificationData.entityTitle}".`;
        await sendSocialPushNotification(targetUserId, title, body, {
          notificationType: "LIKE_ON_READING",
          actorId: likerId,
          entityId: bookId,
        });
      }
    } catch (error) {
      logger.error(`Erreur dans onNewLikeOnReading pour ${likerId} -> ${targetUserId}:`, error);
    }
  }
);

export const onUnlikeOnReading = functions.onDocumentDeleted(
  "users/{targetUserId}/user_readings/activeReading/likes/{likerId}",
  async (event) => {
    // ... (Logique existante de onUnlikeOnReading)
    const likeSnapshot = event.data;
    if (!likeSnapshot) {
      logger.error("Le snapshot du like supprimé est vide.");
      return;
    }

    const { likerId } = event.params;
    const likeData = likeSnapshot.data();
    const bookId = likeData.bookId;

    logger.log(`Début de onUnlikeOnReading: ${likerId} a retiré son like (livre: ${bookId})`);

    if (!bookId || typeof bookId !== "string") {
        logger.error("Le document de like supprimé ne contenait pas de 'bookId' valide.", likeData);
        return;
    }

    try {
      const bookRef = db.collection("books").doc(bookId);
      await bookRef.update({ likesCount: admin.firestore.FieldValue.increment(-1) });
      logger.log(`Compteur de likes décrémenté pour le livre ${bookId}.`);
    } catch (error) {
      logger.error(`Erreur dans onUnlikeOnReading pour le livre ${bookId}:`, error);
    }
  }
);


// --- LOGIQUE DE LIKE/UNLIKE SUR UN COMMENTAIRE (Inchangé) ---

export const onNewLikeOnComment = functions.onDocumentCreated(
  "books/{bookId}/comments/{commentId}/likes/{likerId}",
  async (event) => {
    // ... (Logique existante de onNewLikeOnComment)
    const { bookId, commentId, likerId } = event.params;
    logger.log(`Début de onNewLikeOnComment: ${likerId} a aimé le commentaire ${commentId}`);

    const commentRef = db.collection("books").doc(bookId).collection("comments").doc(commentId);

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
        };
        await db.collection("users").doc(recipientId).collection("notifications").add(notificationData);

        const title = `Nouveau "J'aime" de ${notificationData.actorUsername}`;
        const body = `${notificationData.actorUsername} a aimé votre commentaire.`;
        await sendSocialPushNotification(recipientId, title, body, {
          notificationType: "LIKE_ON_COMMENT",
          actorId: likerId,
          entityId: bookId,
        });

    } catch (error) {
        logger.error(`Erreur dans onNewLikeOnComment pour le commentaire ${commentId}:`, error);
    }
  }
);

export const onUnlikeOnComment = functions.onDocumentDeleted(
  "books/{bookId}/comments/{commentId}/likes/{likerId}",
  async (event) => {
    // ... (Logique existante de onUnlikeOnComment)
    const { bookId, commentId, likerId } = event.params;
    logger.log(`Début de onUnlikeOnComment: ${likerId} a retiré son like du commentaire ${commentId}`);

    try {
        const commentRef = db.collection("books").doc(bookId).collection("comments").doc(commentId);
        await commentRef.update({ likesCount: admin.firestore.FieldValue.increment(-1) });
        logger.log(`Compteur de likes décrémenté pour le commentaire ${commentId}.`);
    } catch (error) {
        logger.error(`Erreur dans onUnlikeOnComment pour le commentaire ${commentId}:`, error);
    }
  }
);


// --- LOGIQUE DE CRÉATION/SUPPRESSION DE COMMENTAIRE (MODIFIÉE POUR GÉRER LES RÉPONSES) ---

/**
 * Déclenché lorsqu'un utilisateur crée un commentaire (ou une réponse).
 * Gère les notifications et la mise à jour du compteur de réponses.
 */
export const onNewCommentOnReading = functions.onDocumentCreated(
  "books/{bookId}/comments/{commentId}",
  async (event) => {
    const commentSnapshot = event.data;
    if (!commentSnapshot) return;

    // CORRIGÉ: On utilise event.id qui est toujours disponible
    const commentId = event.id;
    const { bookId } = event.params;
    const commentData = commentSnapshot.data();
    const actorId = commentData.userId;
    const postAuthorId = commentData.targetUserId;
    const parentCommentId = commentData.parentCommentId;

    logger.log(`Début de onNewCommentOnReading: ${actorId} a commenté (ID: ${commentId}) le livre ${bookId}. Parent: ${parentCommentId || 'aucun'}`);

    let recipientId = postAuthorId;
    let notificationType = "COMMENT_ON_READING";

    try {
        if (parentCommentId) {
            notificationType = "REPLY_TO_COMMENT";
            const parentCommentRef = db.collection("books").doc(bookId).collection("comments").doc(parentCommentId);

            await parentCommentRef.update({ replyCount: admin.firestore.FieldValue.increment(1) });
            logger.log(`replyCount incrémenté pour le commentaire parent ${parentCommentId}.`);

            const parentCommentDoc = await parentCommentRef.get();
            if (parentCommentDoc.exists) {
                recipientId = parentCommentDoc.data()!.userId;
            } else {
                logger.warn(`Commentaire parent ${parentCommentId} introuvable. La notification ira à l'auteur du post.`);
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
        };

        await db.collection("users").doc(recipientId).collection("notifications").add(notificationData);
        logger.log(`Document de notification ${notificationType} créé pour ${recipientId}.`);

        const title = parentCommentId ? `Nouvelle réponse de ${notificationData.actorUsername}` : `Nouveau commentaire de ${notificationData.actorUsername}`;
        const body = `"${commentData.commentText}"`;
        await sendSocialPushNotification(recipientId, title, body, {
          notificationType: notificationType,
          actorId: actorId,
          entityId: bookId,
        });

    } catch(error) {
        logger.error(`Erreur dans onNewCommentOnReading pour le livre ${bookId}:`, error);
    }
  }
);

/**
 * AJOUT : Déclenché lorsqu'un commentaire est supprimé.
 * Gère la mise à jour du compteur de réponses si nécessaire.
 */
export const onCommentDeleted = functions.onDocumentDeleted(
    "books/{bookId}/comments/{commentId}",
    async (event) => {
      const commentSnapshot = event.data;
      if (!commentSnapshot) return;

      // CORRIGÉ: On utilise event.id qui est toujours disponible et on ne déstructure que ce dont on a besoin.
      const { bookId } = event.params;
      const commentData = commentSnapshot.data();
      const parentCommentId = commentData.parentCommentId;

      logger.log(`Début de onCommentDeleted: Commentaire ${event.id} supprimé.`);

      if (parentCommentId) {
        try {
            const parentCommentRef = db.collection("books").doc(bookId).collection("comments").doc(parentCommentId);
            await parentCommentRef.update({ replyCount: admin.firestore.FieldValue.increment(-1) });
            logger.log(`replyCount décrémenté pour le commentaire parent ${parentCommentId}.`);
        } catch (error) {
            logger.error(`Erreur lors de la décrémentation de replyCount pour ${parentCommentId}:`, error);
        }
      }
    }
  );