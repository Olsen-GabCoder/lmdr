// PRÊT À COLLER - Fichier 100% COMPLET avec la fonction de synchronisation ajoutée.
import * as functions from "firebase-functions/v2";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as admin from "firebase-admin";

// Initialisation de l'instance Admin pour pouvoir interagir avec Firestore
try {
  admin.initializeApp();
} catch (e) {
  // Ignorer l'erreur si admin est déjà initialisé
}

const db = admin.firestore();
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
        functions.logger.log(`Destinataire ${recipientId} est actif. Pas d'incrémentation du compteur.`);
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

      const userTokens: { [userId: string]: string } = {};
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


// =================================================================================
// SECTION 2 : FONCTIONS CALLABLES ET TÂCHES PLANIFIÉES
// =================================================================================

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
        const otherUserId = participantIds?.find(id => id !== userId);

        if (!otherUserId) {
            functions.logger.warn(`Aucun autre participant trouvé dans la conversation ${conversationId}. L'utilisateur est peut-être seul.`);
            await conversationRef.update({ [`unreadCount.${userId}`]: 0 });
            return { success: true, message: "Compteur de non-lus réinitialisé. Aucun autre participant." };
        }

        const messagesToUpdateQuery = messagesRef
            .where("senderId", "==", otherUserId)
            .where("status", "==", "SENT");

        const messagesToUpdateSnapshot = await messagesToUpdateQuery.get();

        const batch = db.batch();
        batch.update(conversationRef, {
            [`unreadCount.${userId}`]: 0,
        });

        if (!messagesToUpdateSnapshot.empty) {
            functions.logger.log(`Trouvé ${messagesToUpdateSnapshot.size} message(s) de ${otherUserId} à marquer comme 'READ'.`);
            messagesToUpdateSnapshot.forEach(doc => {
                batch.update(doc.ref, { status: "READ" });
            });
        } else {
            functions.logger.log(`Aucun message de ${otherUserId} avec le statut 'SENT' à marquer comme 'READ'.`);
        }

        await batch.commit();

        functions.logger.log(`Conversation ${conversationId} et messages pertinents marqués comme lus avec succès.`);
        return { success: true, message: "Conversation marquée comme lue." };

    } catch (error) {
        functions.logger.error(`Erreur lors du marquage de la conversation ${conversationId} comme lue:`, error);
        if (error instanceof HttpsError) {
            throw error;
        }
        throw new HttpsError("internal", "Une erreur interne est survenue lors de la mise à jour de la conversation.");
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
// SECTION 3 : SYNCHRONISATION DES DONNÉES UTILISATEUR (NOUVELLE FONCTION AJOUTÉE)
// =================================================================================

/**
 * Se déclenche lors de la mise à jour d'un document utilisateur.
 * Si le pseudonyme ou l'URL de la photo de profil a changé, cette fonction propage
 * les modifications à tous les documents de conversation où l'utilisateur est un participant.
 */
export const propagateUserUpdatesToConversations = onDocumentUpdated(
  { ...functionOptions, document: "users/{userId}" },
  async (event) => {
    const beforeData = event.data?.before.data();
    const afterData = event.data?.after.data();
    const userId = event.params.userId;

    // Vérification que les données existent
    if (!beforeData || !afterData) {
      functions.logger.info(`Données manquantes pour l'utilisateur ${userId}, annulation.`);
      return;
    }

    // Condition de garde : ne rien faire si le pseudo et la photo n'ont pas changé.
    const hasUsernameChanged = beforeData.username !== afterData.username;
    const hasPhotoChanged = beforeData.profilePictureUrl !== afterData.profilePictureUrl;

    if (!hasUsernameChanged && !hasPhotoChanged) {
      functions.logger.info(
        `Mise à jour pour l'utilisateur ${userId}, mais sans changement de nom ou de photo. Fin de la fonction.`
      );
      return; // Arrêter l'exécution
    }

    functions.logger.info(
      `Le profil de l'utilisateur ${userId} a changé. Propagation des mises à jour...`
    );

    // Préparation de l'objet de mise à jour.
    const updatePayload: { [key: string]: any } = {};
    if (hasUsernameChanged) {
      updatePayload[`participantNames.${userId}`] = afterData.username;
    }
    if (hasPhotoChanged) {
      updatePayload[`participantPhotoUrls.${userId}`] = afterData.profilePictureUrl || null;
    }

    // 1. Trouver toutes les conversations où l'utilisateur est un participant.
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

    // 2. Utiliser une écriture par lot (batch) pour mettre à jour tous les documents efficacement.
    const batch = db.batch();
    conversationsSnapshot.docs.forEach((doc) => {
      functions.logger.log(`Planification de la mise à jour pour la conversation ${doc.id}`);
      const conversationRef = doc.ref;
      batch.update(conversationRef, updatePayload);
    });

    // 3. Exécuter le lot d'écritures.
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