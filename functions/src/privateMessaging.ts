// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier privateMessaging.ts
import * as functions from "firebase-functions/v2";
import { onDocumentCreated, onDocumentUpdated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as admin from "firebase-admin";
import { v4 as uuidv4 } from "uuid";

try {
  admin.initializeApp();
} catch (e) {
  // Ignorer l'erreur si admin est déjà initialisé
}

const db = admin.firestore();
const storage = admin.storage();
const functionOptions: functions.GlobalOptions = { region: "europe-west1" };

export const updateConversationOnNewMessage = onDocumentCreated(
  { ...functionOptions, document: "conversations/{conversationId}/messages/{messageId}" },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      functions.logger.error("Aucun snapshot de données pour l'événement on new message.");
      return;
    }

    const messageData = snapshot.data();
    const { conversationId } = event.params;
    const senderId = messageData.senderId;

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

      const activeParticipantIds = conversationData.activeParticipantIds as string[] || [];
      const isRecipientActive = activeParticipantIds.includes(recipientId);
      let messageStatus = "SENT";

      if (isRecipientActive) {
          await snapshot.ref.update({ status: "READ" });
          messageStatus = "READ";
      }

      const updatePayload: { [key: string]: any } = {
        lastMessage: lastMessagePreview,
        lastMessageTimestamp: snapshot.createTime,
        lastMessageSenderId: senderId,
        lastInteractionTimestamp: snapshot.createTime,
        totalMessageCount: admin.firestore.FieldValue.increment(1),
        isStreakActive: true,
        lastMessageStatus: messageStatus
      };

      if (!isRecipientActive) {
        updatePayload[`unreadCount.${recipientId}`] = admin.firestore.FieldValue.increment(1);
      } else {
        updatePayload[`unreadCount.${recipientId}`] = 0;
      }

      const messageTimestampMillis = snapshot.createTime.toMillis();
      let shouldIncrementScore = false;
      const lastMessageTimestamp = conversationData.lastMessageTimestamp as admin.firestore.Timestamp | undefined;
      const lastMessageSenderId = conversationData.lastMessageSenderId as string | undefined;

      if (!lastMessageTimestamp || !lastMessageSenderId || senderId !== lastMessageSenderId || (messageTimestampMillis - lastMessageTimestamp.toMillis() > 60 * 1000)) {
          shouldIncrementScore = true;
      }
      if (shouldIncrementScore) {
        updatePayload.affinityScore = admin.firestore.FieldValue.increment(1);
      }

      if (!conversationData.totalMessageCount || conversationData.totalMessageCount === 0) {
        updatePayload.firstMessageTimestamp = snapshot.createTime;
      }

      await conversationRef.update(updatePayload);

      if (!isRecipientActive) {
        await sendPushNotificationToRecipient(senderId, recipientId, lastMessagePreview, conversationId);
      }
    } catch (error) {
      functions.logger.error(`Erreur dans updateConversationOnNewMessage pour ${conversationId}:`, error);
    }
  }
);

// === DÉBUT DE LA CORRECTION : Logique de propagation du statut de lecture rendue robuste ===
export const propagateMessageStatusUpdate = onDocumentUpdated(
    { ...functionOptions, document: "conversations/{conversationId}/messages/{messageId}" },
    async (event) => {
        const afterData = event.data?.after.data();
        const beforeData = event.data?.before.data();

        // On ne s'exécute que si le statut a réellement changé (par exemple, de SENT à READ).
        if (!afterData || !beforeData || afterData.status === beforeData.status) {
            return;
        }

        const { conversationId } = event.params;
        const conversationRef = db.collection("conversations").doc(conversationId);

        try {
            // Approche robuste : On ignore les données de l'événement et on va chercher la vérité.
            // 1. On récupère le message le plus récent de la conversation.
            const lastMessageQuery = conversationRef.collection("messages").orderBy("timestamp", "desc").limit(1);
            const lastMessageSnapshot = await lastMessageQuery.get();

            if (lastMessageSnapshot.empty) {
                // S'il n'y a plus de messages, on nettoie le statut.
                await conversationRef.update({ lastMessageStatus: null });
                return;
            }

            // 2. On prend le statut de ce dernier message.
            const lastMessageData = lastMessageSnapshot.docs[0].data();
            const lastMessageStatus = lastMessageData.status;

            // 3. On synchronise le document de la conversation avec ce statut.
            // C'est la garantie que la liste des conversations affichera toujours le bon statut.
            await conversationRef.update({ lastMessageStatus: lastMessageStatus });

            functions.logger.log(`Statut de la conversation ${conversationId} synchronisé sur '${lastMessageStatus}'.`);
        } catch (error) {
            functions.logger.error(`Erreur dans propagateMessageStatusUpdate pour ${conversationId}:`, error);
        }
    }
);
// === FIN DE LA CORRECTION ===

export const onMessageDeleted = onDocumentDeleted(
    { ...functionOptions, document: "conversations/{conversationId}/messages/{messageId}" },
    async (event) => {
        const { conversationId } = event.params;
        const conversationRef = db.collection("conversations").doc(conversationId);
        try {
            const messagesQuery = conversationRef.collection("messages").orderBy("timestamp", "desc").limit(1);
            const lastMessageSnapshot = await messagesQuery.get();
            const updatePayload: { [key: string]: any } = { totalMessageCount: admin.firestore.FieldValue.increment(-1) };

            if (lastMessageSnapshot.empty) {
                updatePayload.lastMessage = null;
                updatePayload.lastMessageSenderId = null;
                updatePayload.lastMessageTimestamp = null;
                updatePayload.lastMessageStatus = null;
            } else {
                const newLastMessage = lastMessageSnapshot.docs[0].data();
                updatePayload.lastMessage = newLastMessage.text ? newLastMessage.text.substring(0, 50) : "📷 Image";
                updatePayload.lastMessageSenderId = newLastMessage.senderId;
                updatePayload.lastMessageTimestamp = newLastMessage.timestamp;
                updatePayload.lastMessageStatus = newLastMessage.status;
            }
            await conversationRef.update(updatePayload);
        } catch (error) {
            functions.logger.error(`Erreur dans onMessageDeleted pour la conversation ${conversationId}:`, error);
        }
    }
);

// ... (le reste du fichier reste inchangé) ...

export const onAffinityUpdate = onDocumentUpdated(
  { ...functionOptions, document: "conversations/{conversationId}" },
  async (event) => {
    const beforeData = event.data?.before.data();
    const afterData = event.data?.after.data();
    if (!beforeData || !afterData) { return; }
    const beforeScore = beforeData.affinityScore || 0;
    const afterScore = afterData.affinityScore || 0;
    if (afterScore <= beforeScore) { return; }

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
          }
        });
      } catch (error) {
        functions.logger.error(`Erreur transaction mise à jour profil pour ${userId}:`, error);
      }
    }

    if (beforeTier.name !== afterTier.name) {
      const userDocs = await db.collection("users").where(admin.firestore.FieldPath.documentId(), "in", participantIds).get();
      const userTokens: { [key: string]: string } = {};
      userDocs.forEach(doc => {
        const fcmToken = doc.data().fcmToken;
        if (fcmToken) { userTokens[doc.id] = fcmToken; }
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
            notification: { title: "Nouveau palier d'affinité !", body: notificationMessage },
            data: {
              notificationType: "TIER_UPGRADE", newTierName: afterTier.name, partnerName: partnerName,
              conversationId: conversationId, title: "Nouveau palier d'affinité !", body: notificationMessage,
            },
            android: { priority: "high", notification: { channelId: "private_messages_channel", tag: `tier_upgrade_${conversationId}` } },
          };
          notificationPromises.push(admin.messaging().send(payload));
        }
      }
      try {
        await Promise.all(notificationPromises);
      } catch (error) {
        functions.logger.error("Erreur lors de l'envoi des notifications de palier:", error);
      }
    }
  }
);

export const denormalizeMessageSenderInfo = onDocumentCreated(
    { ...functionOptions, document: "conversations/{conversationId}/messages/{messageId}" },
    async (event) => {
        const snapshot = event.data;
        if (!snapshot) { return; }
        const messageData = snapshot.data();
        const senderId = messageData.senderId;
        if (!senderId) { return; }
        try {
            const userDoc = await db.collection("users").doc(senderId).get();
            if (!userDoc.exists) {
                functions.logger.warn(`Utilisateur ${senderId} non trouvé. Impossible de dénormaliser.`);
                return;
            }
            const userData = userDoc.data()!;
            const updatePayload = {
                senderUsername: userData.username || "Utilisateur Inconnu",
                senderProfilePictureUrl: userData.profilePictureUrl || null,
            };
            await snapshot.ref.update(updatePayload);
        } catch (error) {
            functions.logger.error(`Erreur dénormalisation message ${event.params.messageId}:`, error);
        }
    }
);

export const propagateUserUpdatesToConversations = onDocumentUpdated(
  { ...functionOptions, document: "users/{userId}" },
  async (event) => {
    const beforeData = event.data?.before.data();
    const afterData = event.data?.after.data();
    const userId = event.params.userId;
    if (!beforeData || !afterData) { return; }
    const hasUsernameChanged = beforeData.username !== afterData.username;
    const hasPhotoChanged = beforeData.profilePictureUrl !== afterData.profilePictureUrl;
    if (!hasUsernameChanged && !hasPhotoChanged) { return; }

    const updatePayload: { [key: string]: any } = {};
    if (hasUsernameChanged) { updatePayload[`participantNames.${userId}`] = afterData.username; }
    if (hasPhotoChanged) { updatePayload[`participantPhotoUrls.${userId}`] = afterData.profilePictureUrl || null; }

    const conversationsQuery = db.collection("conversations").where("participantIds", "array-contains", userId);
    const conversationsSnapshot = await conversationsQuery.get();
    if (conversationsSnapshot.empty) { return; }

    const batch = db.batch();
    conversationsSnapshot.docs.forEach((doc) => {
      batch.update(doc.ref, updatePayload);
    });
    try {
      await batch.commit();
    } catch (error) {
      functions.logger.error(`Erreur propagation updates de ${userId}:`, error);
    }
  }
);

export const deleteConversations = onCall(functionOptions, async (request) => {
    const userId = request.auth?.uid;
    if (!userId) {
        throw new HttpsError("unauthenticated", "L'utilisateur doit être authentifié.");
    }

    const { conversationIds } = request.data;
    if (!Array.isArray(conversationIds) || conversationIds.length === 0) {
        throw new HttpsError("invalid-argument", "Le paramètre 'conversationIds' doit être un tableau non vide.");
    }

    const promises: Promise<any>[] = [];

    for (const id of conversationIds) {
        const convRef = db.collection("conversations").doc(id);
        const doc = await convRef.get();
        const participantIds = doc.data()?.participantIds as string[] | undefined;

        if (participantIds && participantIds.includes(userId)) {
            const deletePromise = db.recursiveDelete(convRef);
            promises.push(deletePromise);
        } else {
            functions.logger.warn(`L'utilisateur ${userId} a tenté de supprimer la conversation ${id} sans y être membre.`);
        }
    }

    try {
        await Promise.all(promises);
        functions.logger.log(`Suppression réussie pour les conversations demandées par ${userId}.`);
        return { success: true };
    } catch (error) {
        functions.logger.error(`Erreur lors de la suppression de conversations pour ${userId}:`, error);
        throw new HttpsError("internal", "Une erreur est survenue lors de la suppression.");
    }
});

export const sendChatMessageImage = onCall(functionOptions, async (request) => {
    const senderId = request.auth?.uid;
    if (!senderId) {
        throw new HttpsError("unauthenticated", "L'utilisateur doit être authentifié.");
    }
    const { conversationId, imageBase64, text } = request.data;
    if (!conversationId || typeof conversationId !== "string" || !imageBase64 || typeof imageBase64 !== "string") {
        throw new HttpsError("invalid-argument", "Arguments invalides.");
    }
    try {
        const conversationRef = db.collection("conversations").doc(conversationId);
        const conversationDoc = await conversationRef.get();
        if (!conversationDoc.exists) { throw new HttpsError("not-found", "Conversation non trouvée."); }
        const participantIds = conversationDoc.data()?.participantIds as string[] || [];
        if (!participantIds.includes(senderId)) { throw new HttpsError("permission-denied", "Non membre."); }
        const imageBuffer = Buffer.from(imageBase64, "base64");
        const fileName = `${uuidv4()}.jpg`;
        const filePath = `chat_images/${conversationId}/${fileName}`;
        const file = storage.bucket().file(filePath);
        await file.save(imageBuffer, { metadata: { contentType: "image/jpeg" } });
        await file.makePublic();
        const imageUrl = file.publicUrl();
        const messagePayload = {
            senderId: senderId, imageUrl: imageUrl, text: text || null,
            timestamp: admin.firestore.FieldValue.serverTimestamp(), status: "SENT",
            isEdited: false, reactions: {}, replyInfo: null,
        };
        await conversationRef.collection("messages").add(messagePayload);
        return { success: true, message: "Image envoyée." };
    } catch (error) {
        functions.logger.error(`Erreur envoi image pour ${conversationId}:`, error);
        if (error instanceof HttpsError) { throw error; }
        throw new HttpsError("internal", "Erreur interne.");
    }
});

export const markConversationAsRead = onCall(functionOptions, async (request) => {
    const userId = request.auth?.uid;
    if (!userId) { throw new HttpsError("unauthenticated", "Authentification requise."); }
    const { conversationId } = request.data;
    if (!conversationId || typeof conversationId !== "string") {
        throw new HttpsError("invalid-argument", "ID de conversation invalide.");
    }
    const conversationRef = db.collection("conversations").doc(conversationId);
    try {
        const conversationDoc = await conversationRef.get();
        if (!conversationDoc.exists) { throw new HttpsError("not-found", "Conversation non trouvée."); }
        const participantIds = conversationDoc.data()?.participantIds as string[] | undefined;
        const otherUserId = participantIds?.find((id) => id !== userId);
        if (!otherUserId) {
            await conversationRef.update({ [`unreadCount.${userId}`]: 0 });
            return { success: true };
        }
        const messagesToUpdateQuery = conversationRef.collection("messages").where("senderId", "==", otherUserId).where("status", "==", "SENT");
        const messagesToUpdateSnapshot = await messagesToUpdateQuery.get();
        const batch = db.batch();
        if (!messagesToUpdateSnapshot.empty) {
            messagesToUpdateSnapshot.forEach((doc) => { batch.update(doc.ref, { status: "READ" }); });
        }
        batch.update(conversationRef, { [`unreadCount.${userId}`]: 0 });
        await batch.commit();
        return { success: true };
    } catch (error) {
        functions.logger.error(`Erreur marquage comme lu pour ${conversationId}:`, error);
        if (error instanceof HttpsError) { throw error; }
        throw new HttpsError("internal", "Erreur interne.");
    }
});

export const decrementInactiveAffinityScores = onSchedule(
  { ...functionOptions, schedule: "every 24 hours" },
  async () => {
    const threeDaysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000);
    const inactiveQuery = db.collection("conversations").where("lastInteractionTimestamp", "<", threeDaysAgo).where("affinityScore", ">", 0);
    const snapshot = await inactiveQuery.get();
    if (snapshot.empty) { return; }
    const batch = db.batch();
    snapshot.forEach((doc) => {
      const newScore = Math.max(0, (doc.data().affinityScore || 0) - 2);
      if (newScore < doc.data().affinityScore) {
        batch.update(doc.ref, { affinityScore: newScore });
      }
    });
    await batch.commit();
  }
);

export const resetInactiveStreaks = onSchedule(
  { ...functionOptions, schedule: "every day 03:00", timeZone: "Europe/Paris" },
  async () => {
    const twentyFourHoursAgo = admin.firestore.Timestamp.fromMillis(Date.now() - 24 * 60 * 60 * 1000);
    const inactiveStreaksQuery = db.collection("conversations").where("isStreakActive", "==", true).where("lastMessageTimestamp", "<", twentyFourHoursAgo);
    const snapshot = await inactiveStreaksQuery.get();
    if (snapshot.empty) { return; }
    const batch = db.batch();
    snapshot.forEach(doc => { batch.update(doc.ref, { isStreakActive: false }); });
    await batch.commit();
  }
);

export const weeklyLeaderboardGenerator = onSchedule(
  { ...functionOptions, schedule: "every monday 10:00", timeZone: "Europe/Paris" },
  async () => {
    const conversationsQuery = db.collection("conversations").orderBy("affinityScore", "desc").limit(10);
    const snapshot = await conversationsQuery.get();
    if (snapshot.empty) { return; }
    const weeklyWinners: any[] = [];
    snapshot.forEach(doc => {
      const data = doc.data();
      weeklyWinners.push({
        conversationId: doc.id, score: data.affinityScore,
        participantNames: data.participantNames, participantIds: data.participantIds,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      });
    });
    const leaderboardRef = db.collection("weeklyAffinityWinners").doc(new Date().toISOString().split("T")[0]);
    await leaderboardRef.set({ createdAt: admin.firestore.FieldValue.serverTimestamp(), winners: weeklyWinners });
  }
);

export const generateWeeklyChallenges = onSchedule(
  { ...functionOptions, schedule: "every monday 05:00", timeZone: "Europe/Paris" },
  async () => {
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
      await challengesRef.set({ challenges: selectedChallenges, updatedAt: admin.firestore.FieldValue.serverTimestamp() });
      const conversationsRef = db.collection("conversations");
      const snapshot = await conversationsRef.where("completedChallengeIds", "!=", []).get();
      if (!snapshot.empty) {
        const batch = db.batch();
        snapshot.forEach(doc => { batch.update(doc.ref, { completedChallengeIds: [] }); });
        await batch.commit();
      }
    } catch (error) {
      functions.logger.error("Erreur génération défis:", error);
    }
  }
);

async function sendPushNotificationToRecipient(senderId: string, recipientId: string, messageBody: string, conversationId: string) {
    const senderDoc = await db.collection("users").doc(senderId).get();
    const recipientDoc = await db.collection("users").doc(recipientId).get();
    const senderName = senderDoc.data()?.username || "Quelqu'un";
    const recipientFcmToken = recipientDoc.data()?.fcmToken;
    if (!recipientFcmToken) { return; }
    const payload: admin.messaging.Message = {
        token: recipientFcmToken,
        notification: { title: senderName, body: messageBody },
        data: { notificationType: "new_private_message", conversationId: conversationId, title: senderName, body: messageBody },
        android: { priority: "high", notification: { channelId: "private_messages_channel" } },
    };
    try {
        await admin.messaging().send(payload);
    } catch (error) {
        functions.logger.error(`Erreur envoi notification à ${recipientId}:`, error);
    }
}