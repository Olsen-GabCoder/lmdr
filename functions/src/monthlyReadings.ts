import * as functions from "firebase-functions";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";

const db = admin.firestore();

// VOS CONSTANTES EXISTANTES
const COLLECTIONS = {
    MONTHLY_READINGS: "monthly_readings",
    USERS: "users",
    BOOKS: "books",
};
const NOTIFICATION_TYPES = {
    NEW_MONTHLY_READING: "new_monthly_reading",
    PHASE_REMINDER: "phase_reminder",
    PHASE_STATUS_CHANGE: "phase_status_change",
    MEETING_LINK_UPDATE: "meeting_link_update",
};
const PHASE_STATUS = {
    PLANIFIED: "planified",
    IN_PROGRESS: "in_progress",
    COMPLETED: "completed",
};

// VOS FONCTIONS UTILITAIRES EXISTANTES
function normalizeStatus(status: string | undefined | null): string | null {
    if (!status || typeof status !== "string") return null;
    const normalizedStatus = status.toLowerCase().trim();
    switch (normalizedStatus) {
        case "planified":
        case "planned":
            return PHASE_STATUS.PLANIFIED;
        case "in_progress":
        case "in progress":
        case "inprogress":
            return PHASE_STATUS.IN_PROGRESS;
        case "completed":
        case "complete":
        case "finished":
            return PHASE_STATUS.COMPLETED;
        default:
            return normalizedStatus;
    }
}
async function getAllUserFcmTokens(): Promise<Array<{ uid: string; token: string }>> {
    const usersSnapshot = await db.collection(COLLECTIONS.USERS).get();
    const tokensWithUids: Array<{ uid: string; token: string }> = [];
    functions.logger.info("Début de la collecte des jetons FCM des utilisateurs.");
    usersSnapshot.forEach((doc) => {
        const userData = doc.data();
        const fcmToken = userData.fcmToken;
        if (fcmToken && typeof fcmToken === "string" && fcmToken.trim().length > 0) {
            tokensWithUids.push({ uid: doc.id, token: fcmToken.trim() });
            functions.logger.info(`  Collecté jeton pour utilisateur ${doc.id}: ${fcmToken.trim().substring(0, 10)}...`);
        } else {
            functions.logger.warn(`  Jeton FCM invalide ou absent pour l'utilisateur ${doc.id}: ${fcmToken}`);
        }
    });
    functions.logger.info(`Fin de la collecte. ${tokensWithUids.length} jetons valides trouvés.`);
    return tokensWithUids;
}
async function sendFCMNotification(
  recipients: Array<{ uid: string; token: string }>,
  title: string,
  body: string,
  data: { [key: string]: string }
): Promise<boolean> {
  functions.logger.info(`[sendFCMNotification] DÉBUT - Destinataires: ${recipients.length}`);
  if (recipients.length === 0) {
    functions.logger.warn("[sendFCMNotification] Aucun destinataire FCM valide fourni pour l'envoi.");
    return false;
  }
  const tokensToSend = recipients.map(r => r.token);
  functions.logger.info(`[sendFCMNotification] Jetons à envoyer: ${tokensToSend.length} jetons`);
  functions.logger.info(`[sendFCMNotification] Premier jeton (extrait): ${tokensToSend[0]?.substring(0, 20)}...`);
  const message: admin.messaging.MulticastMessage = {
    tokens: tokensToSend,
    notification: {
      title: title,
      body: body,
    },
    data: {
      ...data,
      title: title,
      body: body,
    },
    android: {
      priority: "high",
      notification: {
        channelId: "general_notifications_channel",
        priority: "high",
      },
    },
    apns: {
      headers: { "apns-priority": "10" },
      payload: {
        aps: {
          sound: "default",
          badge: 1,
        }
      },
    },
  };
  try {
    functions.logger.info("[sendFCMNotification] *** TENTATIVE D'ENVOI FCM ***");
    const response = await admin.messaging().sendEachForMulticast(message);
    functions.logger.info(`[sendFCMNotification] RÉSULTAT: ${response.successCount} succès, ${response.failureCount} échecs`);
    if (response.failureCount > 0) {
      const tokensToRemove: string[] = [];
      const batch = db.batch();
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          const failedRecipient = recipients[idx];
          const failedToken = failedRecipient?.token;
          const failedUid = failedRecipient?.uid;
          functions.logger.error(`[sendFCMNotification] Échec pour ${failedUid}: ${resp.error?.message} (Code: ${resp.error?.code})`);
          const isInvalidTokenError = resp.error?.code === "messaging/invalid-registration-token" ||
                                      resp.error?.code === "messaging/registration-token-not-registered" ||
                                      resp.error?.code === "messaging/not-found";
          if (failedToken && failedUid && isInvalidTokenError) {
            tokensToRemove.push(failedToken);
            const userRef = db.collection(COLLECTIONS.USERS).doc(failedUid);
            batch.update(userRef, { fcmToken: admin.firestore.FieldValue.delete() });
          }
        }
      });
      if (tokensToRemove.length > 0) {
        await batch.commit();
        functions.logger.info(`[sendFCMNotification] Supprimé ${tokensToRemove.length} jetons invalides de Firestore.`);
      }
    }
    return response.successCount > 0;
  } catch (error) {
    functions.logger.error("[sendFCMNotification] ❌ ERREUR lors de l'envoi FCM:", error);
    return false;
  }
}

// VOS CLOUD FUNCTIONS EXISTANTES
export const onNewMonthlyReadingCreated = onDocumentCreated(
  `${COLLECTIONS.MONTHLY_READINGS}/{monthlyReadingId}`,
  async (event) => {
    const newMonthlyReading = event.data?.data();
    const monthlyReadingId = event.params.monthlyReadingId as string;
    if (!newMonthlyReading) {
      functions.logger.warn(`Aucune donnée pour la lecture mensuelle ${monthlyReadingId}. Annulation.`);
      return null;
    }
    let bookTitle = "Nouvelle lecture";
    if (newMonthlyReading.bookId) {
      try {
        const bookDoc = await db.collection(COLLECTIONS.BOOKS).doc(newMonthlyReading.bookId).get();
        if (bookDoc.exists) {
          bookTitle = bookDoc.data()?.title || bookTitle;
        }
      } catch (error) {
        functions.logger.error("Erreur lors de la récupération du titre du livre :", error);
      }
    }
    const notificationTitle = `📖 Nouvelle lecture mensuelle !`;
    const notificationBody = `Le livre du mois est "${bookTitle}" ! Découvrez-le vite.`;
    const recipients = await getAllUserFcmTokens();
    await sendFCMNotification(recipients, notificationTitle, notificationBody, {
      notificationType: NOTIFICATION_TYPES.NEW_MONTHLY_READING,
      monthlyReadingId: monthlyReadingId,
    });
    return null;
  }
);
export const onMonthlyReadingUpdated = onDocumentUpdated(
  `${COLLECTIONS.MONTHLY_READINGS}/{monthlyReadingId}`,
  async (event) => {
    const beforeData = event.data?.before.data();
    const afterData = event.data?.after.data();
    const monthlyReadingId = event.params.monthlyReadingId as string;
    if (!beforeData || !afterData) {
      functions.logger.warn(`Données avant ou après manquantes pour la lecture mensuelle ${monthlyReadingId}. Annulation.`);
      return null;
    }
    const recipients = await getAllUserFcmTokens();
    if (recipients.length === 0) {
      functions.logger.warn("Aucun destinataire FCM valide trouvé pour les notifications de mise à jour.");
      return null;
    }
    let notificationsSent = false;
    const oldAnalysisStatus = normalizeStatus(beforeData.analysisPhase?.status);
    const newAnalysisStatus = normalizeStatus(afterData.analysisPhase?.status);
    const oldDebateStatus = normalizeStatus(beforeData.debatePhase?.status);
    const newDebateStatus = normalizeStatus(afterData.debatePhase?.status);
    let bookTitle = "La lecture du mois";
    if (afterData.bookId) {
      try {
        const bookDoc = await db.collection(COLLECTIONS.BOOKS).doc(afterData.bookId).get();
        if (bookDoc.exists) {
          bookTitle = bookDoc.data()?.title || bookTitle;
        }
      } catch (error) {
        functions.logger.error("Erreur lors de la récupération du titre du livre pour la mise à jour :", error);
      }
    }
    if (newAnalysisStatus && newAnalysisStatus !== oldAnalysisStatus) {
        if (newAnalysisStatus === PHASE_STATUS.IN_PROGRESS) {
            const title = `🔍 Début de l'analyse !`;
            const body = `La phase d'analyse de "${bookTitle}" est maintenant en cours. Participez !`;
            notificationsSent = await sendFCMNotification(recipients, title, body, { notificationType: NOTIFICATION_TYPES.PHASE_STATUS_CHANGE, monthlyReadingId: monthlyReadingId, phase: "analysis", status: PHASE_STATUS.IN_PROGRESS, }) || notificationsSent;
        } else if (newAnalysisStatus === PHASE_STATUS.COMPLETED) {
            const title = `✅ Analyse terminée !`;
            const body = `La phase d'analyse de "${bookTitle}" est à présent terminée.`;
            notificationsSent = await sendFCMNotification(recipients, title, body, { notificationType: NOTIFICATION_TYPES.PHASE_STATUS_CHANGE, monthlyReadingId: monthlyReadingId, phase: "analysis", status: PHASE_STATUS.COMPLETED, }) || notificationsSent;
        }
    }
    if (newDebateStatus && newDebateStatus !== oldDebateStatus) {
        if (newDebateStatus === PHASE_STATUS.IN_PROGRESS) {
            const title = `💬 Début du débat !`;
            const body = `La phase de débat de "${bookTitle}" est maintenant en cours. Rejoignez-nous !`;
            notificationsSent = await sendFCMNotification(recipients, title, body, { notificationType: NOTIFICATION_TYPES.PHASE_STATUS_CHANGE, monthlyReadingId: monthlyReadingId, phase: "debate", status: PHASE_STATUS.IN_PROGRESS, }) || notificationsSent;
        } else if (newDebateStatus === PHASE_STATUS.COMPLETED) {
            const title = `✅ Débat terminé !`;
            const body = `La phase de débat de "${bookTitle}" est à présent terminée.`;
            notificationsSent = await sendFCMNotification(recipients, title, body, { notificationType: NOTIFICATION_TYPES.PHASE_STATUS_CHANGE, monthlyReadingId: monthlyReadingId, phase: "debate", status: PHASE_STATUS.COMPLETED, }) || notificationsSent;
        }
    }
    const oldAnalysisLink = beforeData.analysisPhase?.meetingLink || "";
    const newAnalysisLink = afterData.analysisPhase?.meetingLink || "";
    const oldDebateLink = beforeData.debatePhase?.meetingLink || "";
    const newDebateLink = afterData.debatePhase?.meetingLink || "";
    if (newAnalysisLink && newAnalysisLink !== oldAnalysisLink) {
        const title = `🔗 Lien de réunion d'analyse mis à jour !`;
        const body = `Le lien pour la réunion d'analyse de "${bookTitle}" a été mis à jour.`;
        notificationsSent = await sendFCMNotification(recipients, title, body, { notificationType: NOTIFICATION_TYPES.MEETING_LINK_UPDATE, monthlyReadingId: monthlyReadingId, phase: "analysis", newLink: newAnalysisLink, }) || notificationsSent;
    }
    if (newDebateLink && newDebateLink !== oldDebateLink) {
        const title = `🔗 Lien de réunion de débat mis à jour !`;
        const body = `Le lien pour la réunion de débat de "${bookTitle}" a été mis à jour.`;
        notificationsSent = await sendFCMNotification(recipients, title, body, { notificationType: NOTIFICATION_TYPES.MEETING_LINK_UPDATE, monthlyReadingId: monthlyReadingId, phase: "debate", newLink: newDebateLink, }) || notificationsSent;
    }
    return null;
  }
);