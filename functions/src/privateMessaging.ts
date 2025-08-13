// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier privateMessaging.ts
import * as functions from "firebase-functions/v2";
import { onDocumentCreated, onDocumentUpdated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as admin from "firebase-admin";
import { v4 as uuidv4 } from "uuid";

// Initialisation de l'instance Admin pour pouvoir interagir avec Firestore
try {
  admin.initializeApp();
} catch (e) {
  // Ignorer l'erreur si admin est déjà initialisé
}

const db = admin.firestore();
const storage = admin.storage();
const functionOptions: functions.GlobalOptions = { region: "europe-west1" };

// =================================================================================
// SECTION 1 : LOGIQUE DE MISE À JOUR EN TEMPS RÉEL
// =================================================================================

export const updateConversationOnNewMessage = onDocumentCreated(
  { ...functionOptions, document: "conversations/{conversationId}/messages/{messageId}" },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      functions.logger.error("Aucun snapshot de données pour l'événement.");
      return;
    }

    const messageData = snapshot.data();
    const conversationId = event.params.conversationId;
    const senderId = messageData.senderId;
    const messageTimestampMillis = snapshot.createTime.toMillis();

    functions.logger.log(`Nouveau message dans ${conversationId}. Mise à jour conversation...`);
    const lastMessagePreview = messageData.text ? messageData.text.substring(0, 50) : "📷 Image";

    const conversationRef = db.collection("conversations").doc(conversationId);
    try {
      const conversationDoc = await conversationRef.get();
      if (!conversationDoc.exists) {
        functions.logger.error(`Conversation ${conversationId} non trouvée.`);
        return;
      }
      const conversationData = conversationDoc.data()!;
      const recipientId = conversationData.participantIds.find((id: string) => id !== senderId);
      if (!recipientId) {
        functions.logger.error(`Destinataire non trouvé pour ${conversationId}.`);
        return;
      }

      let shouldIncrementScore = false;
      const lastMessageTimestamp = conversationData.lastMessageTimestamp as admin.firestore.Timestamp | undefined;
      const lastMessageSenderId = conversationData.lastMessageSenderId as string | undefined;

      if (!lastMessageTimestamp || !lastMessageSenderId) {
          shouldIncrementScore = true;
      } else {
          if (senderId !== lastMessageSenderId) {
              shouldIncrementScore = true;
          } else {
              const timeDifference = messageTimestampMillis - lastMessageTimestamp.toMillis();
              const oneMinuteInMillis = 60 * 1000;
              if (timeDifference > oneMinuteInMillis) {
                  shouldIncrementScore = true;
              }
          }
      }

      const activeParticipantIds = conversationData.activeParticipantIds as string[] || [];
      const isRecipientActive = activeParticipantIds.includes(recipientId);

      // Si le destinataire est actif, on met à jour le statut du message à READ immédiatement.
      if (isRecipientActive) {
          await snapshot.ref.update({ status: "READ" });
          functions.logger.log(`Destinataire ${recipientId} est actif. Message ${event.params.messageId} marqué comme READ.`);
      }

      const updatePayload: { [key: string]: any } = {
        lastMessage: lastMessagePreview,
        lastMessageTimestamp: snapshot.createTime,
        lastMessageSenderId: senderId,
        lastInteractionTimestamp: snapshot.createTime,
        totalMessageCount: admin.firestore.FieldValue.increment(1),
        isStreakActive: true,
      };

      if (shouldIncrementScore) {
        updatePayload.affinityScore = admin.firestore.FieldValue.increment(1);
        functions.logger.log(`Condition remplie. Incrémentation du score pour ${conversationId}.`);
      } else {
        functions.logger.log(`Condition non remplie. Pas d'incrémentation du score pour ${conversationId}.`);
      }

      if (!isRecipientActive) {
        updatePayload[`unreadCount.${recipientId}`] = admin.firestore.FieldValue.increment(1);
        functions.logger.log(`Destinataire ${recipientId} non actif. Incrémentation du compteur.`);
      } else {
        // Si le destinataire est actif, on s'assure que son compteur est à 0.
        updatePayload[`unreadCount.${recipientId}`] = 0;
        functions.logger.log(`Destinataire ${recipientId} est actif. Compteur de non-lus mis à 0.`);
      }

      if (!conversationData.totalMessageCount || conversationData.totalMessageCount === 0) {
        updatePayload.firstMessageTimestamp = snapshot.createTime;
      }

      await conversationRef.update(updatePayload);
      functions.logger.log(`Conversation ${conversationId} mise à jour avec succès.`);

      if (!isRecipientActive) {
        await sendPushNotificationToRecipient(senderId, recipientId, lastMessagePreview, conversationId);
      } else {
        functions.logger.log(`Destinataire ${recipientId} est actif. Pas d'envoi de notification.`);
      }

    } catch (error) {
      functions.logger.error(`Erreur mise à jour conversation ${conversationId}:`, error);
    }
  }
);

export const onAffinityUpdate = onDocumentUpdated(
  { ...functionOptions, document: "conversations/{conversationId}" },
  async (event) => {
    const beforeData = event.data?.before.data();
    const afterData = event.data?.after.data();

    if (!beforeData || !afterData) {
      functions.logger.log("Données manquantes, impossible de comparer.");
      return;
    }

    const beforeScore = beforeData.affinityScore || 0;
    const afterScore = afterData.affinityScore || 0;

    if (afterScore <= beforeScore) {
      return;
    }

    const getTier = (score: number) => {
      if (score >= 101) return { name: "Ame sœur littéraire" };
      if (score >= 51) return { name: "Proche" };
      if (score >= 21) return { name: "Bon contact" };
      return { name: "Connaissance" };
    };

    const beforeTier = getTier(beforeScore);
    const afterTier = getTier(afterScore);
    const conversationId = event.params.conversationId;
    const participantIds = afterData.participantIds as string[];
    const participantNames = afterData.participantNames as { [key: string]: string };

    for (const userId of participantIds) {
      const userRef = db.collection("users").doc(userId);
      const partnerId = participantIds.find(id => id !== userId)!;
      const partnerUsername = participantNames[partnerId];

      try {
        await db.runTransaction(async (transaction) => {
          const userDoc = await transaction.get(userRef);
          if (!userDoc.exists) return;
          const userData = userDoc.data()!;
          const currentHighestScore = userData.highestAffinityScore || 0;

          if (afterScore > currentHighestScore) {
            transaction.update(userRef, {
              highestAffinityScore: afterScore,
              highestAffinityPartnerId: partnerId,
              highestAffinityPartnerUsername: partnerUsername,
              highestAffinityTierName: afterTier.name,
            });
            functions.logger.log(`Nouveau record d'affinité pour ${userId}: ${afterScore} avec ${partnerUsername}`);
          }
        });
      } catch (error) {
        functions.logger.error(`Erreur transaction mise à jour profil pour ${userId}:`, error);
      }
    }

    if (beforeTier.name !== afterTier.name) {
      functions.logger.log(`Nouveau palier atteint pour ${conversationId}: ${afterTier.name}. Envoi des notifications.`);

      const userDocs = await db.collection("users").where(admin.firestore.FieldPath.documentId(), "in", participantIds).get();

      const userTokens: { [key: string]: string } = {};
      userDocs.forEach(doc => {
        const fcmToken = doc.data().fcmToken;
        if (fcmToken) {
          userTokens[doc.id] = fcmToken;
        }
      });

      const notificationPromises: Promise<any>[] = [];

      for (const userId of participantIds) {
        const token = userTokens[userId];
        if (token) {
          const partnerId = participantIds.find(id => id !== userId)!;
          const partnerName = participantNames[partnerId];
          const notificationMessage = `🎉 Félicitations ! Vous et ${partnerName} êtes maintenant « ${afterTier.name} » !`;

          const payload: admin.messaging.Message = {
            token: token,
            notification: {
              title: "Nouveau palier d'affinité !",
              body: notificationMessage,
            },
            data: {
              notificationType: "TIER_UPGRADE",
              newTierName: afterTier.name,
              partnerName: partnerName,
              conversationId: conversationId,
              title: "Nouveau palier d'affinité !",
              body: notificationMessage,
            },
            android: {
              priority: "high",
              notification: {
                channelId: "private_messages_channel",
                tag: `tier_upgrade_${conversationId}`,
              },
            },
          };
          notificationPromises.push(admin.messaging().send(payload));
          functions.logger.log(`Notification de palier préparée pour ${userId} avec le token ${token.substring(0, 10)}...`);
        }
      }

      try {
        await Promise.all(notificationPromises);
        functions.logger.log(`Toutes les notifications de palier pour ${conversationId} ont été envoyées.`);
      } catch (error) {
        functions.logger.error("Erreur lors de l'envoi des notifications de palier:", error);
      }
    }
  }
);

// === DÉBUT DE LA MODIFICATION ===
/**
 * Se déclenche à la création d'un message pour dénormaliser les informations de l'expéditeur (nom, photo)
 * directement dans le document du message. Cela optimise les lectures côté client.
 * La fonction est rendue robuste pour gérer le cas où le document de l'utilisateur n'existerait pas.
 */
export const denormalizeMessageSenderInfo = onDocumentCreated(
    { ...functionOptions, document: "conversations/{conversationId}/messages/{messageId}" },
    async (event) => {
        const snapshot = event.data;
        if (!snapshot) {
            functions.logger.error("Aucun snapshot de données pour l'événement on message created.");
            return;
        }

        const messageData = snapshot.data();
        const senderId = messageData.senderId;

        if (!senderId) {
            functions.logger.error(`Message ${event.params.messageId} sans senderId. Annulation.`);
            return;
        }

        try {
            const userDoc = await db.collection("users").doc(senderId).get();

            // GARDE DE SÉCURITÉ : Vérifie si le document de l'utilisateur existe.
            if (!userDoc.exists) {
                // Log un avertissement utile pour le débogage et arrête l'exécution pour éviter un crash.
                functions.logger.warn(`Utilisateur ${senderId} non trouvé (potentiellement supprimé). Impossible de dénormaliser le message ${event.params.messageId}.`);
                return;
            }

            // Si le document existe, on continue normalement.
            const userData = userDoc.data()!;
            const updatePayload = {
                senderUsername: userData.username || "Utilisateur Inconnu",
                senderProfilePictureUrl: userData.profilePictureUrl || null,
            };

            await snapshot.ref.update(updatePayload);

            functions.logger.log(`Message ${event.params.messageId} dénormalisé avec les informations de ${senderId}.`);

        } catch (error) {
            functions.logger.error(`Erreur lors de la dénormalisation du message ${event.params.messageId}:`, error);
        }
    }
);
// === FIN DE LA MODIFICATION ===

export const onMessageDeleted = onDocumentDeleted(
    { ...functionOptions, document: "conversations/{conversationId}/messages/{messageId}" },
    async (event) => {
        const { conversationId, messageId } = event.params;
        functions.logger.log(`Message ${messageId} supprimé dans la conversation ${conversationId}. Début du recalcul de l'aperçu.`);

        const conversationRef = db.collection("conversations").doc(conversationId);

        try {
            // Requête pour trouver le dernier message restant
            const messagesQuery = db.collection("conversations").doc(conversationId).collection("messages")
                .orderBy("timestamp", "desc")
                .limit(1);

            const lastMessageSnapshot = await messagesQuery.get();

            const updatePayload: { [key: string]: any } = {
                totalMessageCount: admin.firestore.FieldValue.increment(-1),
            };

            if (lastMessageSnapshot.empty) {
                // S'il n'y a plus de messages, on réinitialise l'aperçu.
                updatePayload.lastMessage = null;
                updatePayload.lastMessageSenderId = null;
                updatePayload.lastMessageTimestamp = null;
                functions.logger.log(`Conversation ${conversationId} est maintenant vide. Aperçu réinitialisé.`);
            } else {
                // S'il reste des messages, on met à jour l'aperçu avec le nouveau dernier message.
                const newLastMessage = lastMessageSnapshot.docs[0].data();
                const newLastMessagePreview = newLastMessage.text ? newLastMessage.text.substring(0, 50) : "📷 Image";

                updatePayload.lastMessage = newLastMessagePreview;
                updatePayload.lastMessageSenderId = newLastMessage.senderId;
                updatePayload.lastMessageTimestamp = newLastMessage.timestamp;

                functions.logger.log(`Aperçu de la conversation ${conversationId} mis à jour. Le unreadCount n'est pas modifié.`);
            }

            await conversationRef.update(updatePayload);

        } catch (error) {
            functions.logger.error(`Erreur dans onMessageDeleted pour la conversation ${conversationId}:`, error);
        }
    }
);

// =================================================================================
// SECTION 2 : FONCTIONS CALLABLES ET TÂCHES PLANIFIÉES
// =================================================================================

export const sendChatMessageImage = onCall(functionOptions, async (request) => {
    const senderId = request.auth?.uid;
    if (!senderId) {
        throw new HttpsError("unauthenticated", "L'utilisateur doit être authentifié pour envoyer une image.");
    }

    const { conversationId, imageBase64, text } = request.data;
    if (!conversationId || typeof conversationId !== "string" || !imageBase64 || typeof imageBase64 !== "string") {
        throw new HttpsError("invalid-argument", "Les paramètres 'conversationId' et 'imageBase64' sont requis et doivent être des chaînes.");
    }

    try {
        // Étape 1: Valider les permissions
        const conversationRef = db.collection("conversations").doc(conversationId);
        const conversationDoc = await conversationRef.get();
        if (!conversationDoc.exists) {
            throw new HttpsError("not-found", `Conversation ${conversationId} non trouvée.`);
        }
        const participantIds = conversationDoc.data()?.participantIds as string[] || [];
        if (!participantIds.includes(senderId)) {
            throw new HttpsError("permission-denied", "Vous n'êtes pas membre de cette conversation.");
        }

        // Étape 2: Uploader l'image sur Firebase Storage
        const imageBuffer = Buffer.from(imageBase64, "base64");
        const fileName = `${uuidv4()}.jpg`;
        const filePath = `chat_images/${conversationId}/${fileName}`;
        const file = storage.bucket().file(filePath);

        await file.save(imageBuffer, {
            metadata: { contentType: "image/jpeg" },
        });

        await file.makePublic();
        const imageUrl = file.publicUrl();

        // Étape 3: Créer le document message dans Firestore (opération finale)
        const messagePayload = {
            senderId: senderId,
            imageUrl: imageUrl,
            text: text || null, // Le texte est optionnel
            timestamp: admin.firestore.FieldValue.serverTimestamp(),
            status: "SENT",
            isEdited: false,
            reactions: {},
            replyInfo: null,
        };
        await conversationRef.collection("messages").add(messagePayload);

        functions.logger.log(`Image envoyée avec succès par ${senderId} dans la conversation ${conversationId}.`);
        return { success: true, message: "Image envoyée avec succès." };

    } catch (error) {
        functions.logger.error(`Erreur lors de l'envoi de l'image pour la conversation ${conversationId}:`, error);
        if (error instanceof HttpsError) {
            // Si c'est déjà une HttpsError (permission, etc.), on la relance telle quelle.
            throw error;
        }
        // Pour toute autre erreur (ex: upload Storage), on renvoie une erreur interne générique.
        throw new HttpsError("internal", "Une erreur interne est survenue lors de l'envoi de l'image.");
    }
});

export const markConversationAsRead = onCall(functionOptions, async (request) => {
    const userId = request.auth?.uid;
    if (!userId) {
        throw new HttpsError("unauthenticated", "L'utilisateur doit être authentifié pour effectuer cette action.");
    }

    const { conversationId } = request.data;
    if (!conversationId || typeof conversationId !== "string") {
        throw new HttpsError("invalid-argument", "L'ID de la conversation (conversationId) est manquant ou invalide.");
    }

    functions.logger.log(`Début du marquage comme lu pour la conversation ${conversationId} par l'utilisateur ${userId}.`);

    const conversationRef = db.collection("conversations").doc(conversationId);
    const messagesRef = conversationRef.collection("messages");

    try {
        const conversationDoc = await conversationRef.get();
        if (!conversationDoc.exists) {
            throw new HttpsError("not-found", `Conversation ${conversationId} non trouvée.`);
        }

        const participantIds = conversationDoc.data()?.participantIds as string[] | undefined;
        const otherUserId = participantIds?.find((id) => id !== userId);

        if (!otherUserId) {
            await conversationRef.update({ [`unreadCount.${userId}`]: 0 });
            return { success: true, message: "Compteur de non-lus réinitialisé." };
        }

        const messagesToUpdateQuery = messagesRef
            .where("senderId", "==", otherUserId)
            .where("status", "==", "SENT");

        const messagesToUpdateSnapshot = await messagesToUpdateQuery.get();

        const batch = db.batch();

        if (!messagesToUpdateSnapshot.empty) {
            functions.logger.log(`Trouvé ${messagesToUpdateSnapshot.size} message(s) à marquer comme 'READ'.`);
            messagesToUpdateSnapshot.forEach((doc) => {
                batch.update(doc.ref, { status: "READ" });
            });
        }

        batch.update(conversationRef, {
            [`unreadCount.${userId}`]: 0,
        });

        await batch.commit();

        functions.logger.log(`Conversation ${conversationId} et ${messagesToUpdateSnapshot.size} message(s) marqués comme lus.`);
        return { success: true, message: "Conversation marquée comme lue." };

    } catch (error) {
        functions.logger.error(`Erreur lors du marquage de la conversation ${conversationId} comme lue:`, error);
        if (error instanceof HttpsError) {
            throw error;
        }
        throw new HttpsError("internal", "Une erreur interne est survenue.");
    }
});

export const decrementInactiveAffinityScores = onSchedule(
  { ...functionOptions, schedule: "every 24 hours" },
  async (event) => {
    functions.logger.log("Démarrage tâche de décrémentation des scores d'affinité.");

    const threeDaysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000);
    const inactiveQuery = db.collection("conversations")
      .where("lastInteractionTimestamp", "<", threeDaysAgo)
      .where("affinityScore", ">", 0);

    const snapshot = await inactiveQuery.get();
    if (snapshot.empty) {
      functions.logger.log("Aucune conversation inactive à décrémenter.");
      return;
    }

    const batch = db.batch();
    snapshot.forEach((doc) => {
      const newScore = Math.max(0, (doc.data().affinityScore || 0) - 2);
      if (newScore < doc.data().affinityScore) {
        batch.update(doc.ref, { affinityScore: newScore });
      }
    });

    await batch.commit();
    functions.logger.log(`${snapshot.size} conversations inactives mises à jour.`);
  }
);

export const resetInactiveStreaks = onSchedule(
  { ...functionOptions, schedule: "every day 03:00", timeZone: "Europe/Paris" },
  async (event) => {
    functions.logger.log("Démarrage de la tâche de réinitialisation des streaks.");
    const twentyFourHoursAgo = admin.firestore.Timestamp.fromMillis(Date.now() - 24 * 60 * 60 * 1000);

    const inactiveStreaksQuery = db.collection("conversations")
      .where("isStreakActive", "==", true)
      .where("lastMessageTimestamp", "<", twentyFourHoursAgo);

    const snapshot = await inactiveStreaksQuery.get();
    if (snapshot.empty) {
      functions.logger.log("Aucune streak inactive à réinitialiser.");
      return;
    }

    const batch = db.batch();
    snapshot.forEach(doc => {
      batch.update(doc.ref, { isStreakActive: false });
    });

    await batch.commit();
    functions.logger.log(`${snapshot.size} streaks inactives ont été réinitialisées.`);
  }
);

export const weeklyLeaderboardGenerator = onSchedule(
  { ...functionOptions, schedule: "every monday 10:00", timeZone: "Europe/Paris" },
  async (event) => {
    functions.logger.log("Génération du classement hebdomadaire de l'affinité.");

    const conversationsQuery = db.collection("conversations")
      .orderBy("affinityScore", "desc")
      .limit(10);

    const snapshot = await conversationsQuery.get();
    if (snapshot.empty) {
      functions.logger.log("Aucune conversation trouvée pour le classement.");
      return;
    }

    const weeklyWinners: any[] = [];
    snapshot.forEach(doc => {
      const data = doc.data();
      weeklyWinners.push({
        conversationId: doc.id,
        score: data.affinityScore,
        participantNames: data.participantNames,
        participantIds: data.participantIds,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      });
    });

    const leaderboardRef = db.collection("weeklyAffinityWinners").doc(new Date().toISOString().split("T")[0]);
    await leaderboardRef.set({
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      winners: weeklyWinners,
    });

    functions.logger.log(`Classement hebdomadaire stocké avec ${weeklyWinners.length} binômes.`);
  }
);

export const generateWeeklyChallenges = onSchedule(
  { ...functionOptions, schedule: "every monday 05:00", timeZone: "Europe/Paris" },
  async (event) => {
    functions.logger.log("Génération des défis hebdomadaires.");

    const allChallenges = [
      { id: "challenge_01", title: "Souhaitez-lui/elle une bonne lecture 📖", bonusPoints: 5 },
      { id: "challenge_02", title: "Partagez une citation inspirante", bonusPoints: 10 },
      { id: "challenge_03", title: "Envoyez un emoji cœur ❤️", bonusPoints: 3 },
      { id: "challenge_04", title: "Demandez-lui son personnage préféré", bonusPoints: 5 },
      { id: "challenge_05", title: "Parlez de la couverture du livre", bonusPoints: 5 },
      { id: "challenge_06", title: "Partagez votre progression de lecture", bonusPoints: 8 },
    ];

    const shuffled = allChallenges.sort(() => 0.5 - Math.random());
    const selectedChallenges = shuffled.slice(0, 3);

    const challengesRef = db.collection("challenges").doc("weekly");

    try {
      await challengesRef.set({
        challenges: selectedChallenges,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      functions.logger.log("Défis hebdomadaires mis à jour avec succès :", selectedChallenges.map(c => c.id));

      const conversationsRef = db.collection("conversations");
      const snapshot = await conversationsRef.where("completedChallengeIds", "!=", []).get();

      if (!snapshot.empty) {
        const batch = db.batch();
        snapshot.forEach(doc => {
          batch.update(doc.ref, { completedChallengeIds: [] });
        });
        await batch.commit();
        functions.logger.log(`${snapshot.size} conversations ont vu leurs défis réinitialisés.`);
      }

    } catch (error) {
      functions.logger.error("Erreur lors de la génération des défis hebdomadaires:", error);
    }
  }
);

// =================================================================================
// SECTION 3 : SYNCHRONISATION DES DONNÉES UTILISATEUR
// =================================================================================

export const propagateUserUpdatesToConversations = onDocumentUpdated(
  { ...functionOptions, document: "users/{userId}" },
  async (event) => {
    const beforeData = event.data?.before.data();
    const afterData = event.data?.after.data();
    const userId = event.params.userId;

    if (!beforeData || !afterData) {
      functions.logger.info(`Données manquantes pour l'utilisateur ${userId}, annulation.`);
      return;
    }

    const hasUsernameChanged = beforeData.username !== afterData.username;
    const hasPhotoChanged = beforeData.profilePictureUrl !== afterData.profilePictureUrl;

    if (!hasUsernameChanged && !hasPhotoChanged) {
      functions.logger.info(
        `Mise à jour pour l'utilisateur ${userId}, mais sans changement de nom ou de photo. Fin de la fonction.`
      );
      return;
    }

    functions.logger.info(
      `Le profil de l'utilisateur ${userId} a changé. Propagation des mises à jour...`
    );

    const updatePayload: { [key: string]: any } = {};
    if (hasUsernameChanged) {
      updatePayload[`participantNames.${userId}`] = afterData.username;
    }
    if (hasPhotoChanged) {
      updatePayload[`participantPhotoUrls.${userId}`] = afterData.profilePictureUrl || null;
    }

    const conversationsQuery = db
      .collection("conversations")
      .where("participantIds", "array-contains", userId);

    const conversationsSnapshot = await conversationsQuery.get();

    if (conversationsSnapshot.empty) {
      functions.logger.info(
        `Aucune conversation trouvée pour l'utilisateur ${userId}. Rien à mettre à jour.`
      );
      return;
    }

    const batch = db.batch();
    conversationsSnapshot.docs.forEach((doc) => {
      functions.logger.log(`Planification de la mise à jour pour la conversation ${doc.id}`);
      const conversationRef = doc.ref;
      batch.update(conversationRef, updatePayload);
    });

    try {
      await batch.commit();
      functions.logger.info(
        `Succès : Les mises à jour du profil de ${userId} ont été propagées à ${conversationsSnapshot.size} conversation(s).`
      );
    } catch (error) {
      functions.logger.error(
        `Erreur lors de l'exécution du batch pour la propagation des mises à jour de ${userId}:`,
        error
      );
      throw new HttpsError("internal", "Échec de la propagation des mises à jour utilisateur.");
    }
  }
);

// =================================================================================
// SECTION 4 : FONCTIONS HELPER
// =================================================================================

async function sendPushNotificationToRecipient(senderId: string, recipientId: string, messageBody: string, conversationId: string) {
    const senderDoc = await db.collection("users").doc(senderId).get();
    const recipientDoc = await db.collection("users").doc(recipientId).get();

    const senderName = senderDoc.data()?.username || "Quelqu'un";
    functions.logger.log(`Nom de l'expéditeur récupéré: ${senderName}`);

    const recipientFcmToken = recipientDoc.data()?.fcmToken;

    if (!recipientFcmToken) {
        functions.logger.warn(`Pas de token FCM pour le destinataire ${recipientId}. Pas de notification.`);
        return;
    }

    const payload: admin.messaging.Message = {
        token: recipientFcmToken,
        notification: { title: senderName, body: messageBody },
        data: {
            notificationType: "new_private_message",
            conversationId: conversationId,
            title: senderName,
            body: messageBody
        },
        android: {
            priority: "high",
            notification: { channelId: "private_messages_channel" }
        },
    };

    try {
        await admin.messaging().send(payload);
        functions.logger.log(`Notification envoyée avec succès à ${recipientId}.`);
    } catch (error) {
        functions.logger.error(`Erreur envoi notification à ${recipientId}:`, error);
    }
}