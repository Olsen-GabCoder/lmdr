// PRÊT À COLLER - Fichier MyFirebaseMessagingService.kt complet
package com.lesmangeursdurouleau.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lesmangeursdurouleau.app.MainActivity
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userProfileRepository: UserProfileRepository

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    companion object {
        private const val TAG = "MyFCMService"

        const val ACTION_TIER_UPGRADE = "com.lesmangeursdurouleau.app.TIER_UPGRADE"

        // --- Canaux de Notification ---
        const val CHANNEL_ID_GENERAL = "general_notifications_channel"
        const val CHANNEL_NAME_GENERAL = "Notifications Générales"
        const val CHANNEL_DESC_GENERAL = "Notifications générales du club de lecture."

        const val CHANNEL_ID_PRIVATE_MESSAGES = "private_messages_channel"
        const val CHANNEL_NAME_PRIVATE_MESSAGES = "Messages Privés"
        const val CHANNEL_DESC_PRIVATE_MESSAGES = "Notifications pour les nouveaux messages privés."

        const val CHANNEL_ID_REWARDS = "rewards_notifications_channel"
        const val CHANNEL_NAME_REWARDS = "Récompenses et Affinité"
        const val CHANNEL_DESC_REWARDS = "Notifications pour les nouveaux paliers d'affinité."

        const val CHANNEL_ID_SOCIAL = "social_interactions_channel"
        const val CHANNEL_NAME_SOCIAL = "Interactions Sociales"
        const val CHANNEL_DESC_SOCIAL = "Notifications pour les 'J'aime', commentaires et nouveaux complices."

        // --- Clés de Données ---
        const val NOTIFICATION_TYPE_KEY = "notificationType"
        const val TITLE_KEY = "title"
        const val BODY_KEY = "body"

        // -- Clés de navigation --
        const val ENTITY_ID_KEY = "entityId"
        const val ACTOR_ID_KEY = "actorId"
        const val MONTHLY_READING_ID_KEY = "monthlyReadingId"
        const val CONVERSATION_ID_KEY = "conversationId"
        // NOUVELLES CLÉS POUR LE DEEP LINKING (Chantier 3)
        const val TARGET_USER_ID_KEY = "targetUserId"
        const val COMMENT_ID_KEY = "commentId"

        // --- Clés Spécifiques (existantes) ---
        const val NEW_TIER_NAME_KEY = "newTierName"
        const val PARTNER_NAME_KEY = "partnerName"

        // --- Types de Notification (existants) ---
        const val TYPE_NEW_MONTHLY_READING = "new_monthly_reading"
        const val TYPE_PHASE_REMINDER = "phase_reminder"
        const val TYPE_PHASE_STATUS_CHANGE = "phase_status_change"
        const val TYPE_MEETING_LINK_UPDATE = "meeting_link_update"
        const val TYPE_NEW_PRIVATE_MESSAGE = "new_private_message"
        const val TYPE_TIER_UPGRADE = "TIER_UPGRADE"

        // NOUVEAUX TYPES DE NOTIFICATION SOCIALE
        const val TYPE_NEW_FOLLOWER = "NEW_FOLLOWER"
        const val TYPE_LIKE_ON_READING = "LIKE_ON_READING"
        const val TYPE_COMMENT_ON_READING = "COMMENT_ON_READING"
        const val TYPE_REPLY_TO_COMMENT = "REPLY_TO_COMMENT"
        const val TYPE_LIKE_ON_COMMENT = "LIKE_ON_COMMENT"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Nouveau jeton FCM : $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String?) {
        token?.let {
            val userId = firebaseAuth.currentUser?.uid
            if (userId != null) {
                serviceScope.launch {
                    try {
                        userProfileRepository.updateUserFCMToken(userId, it)
                        Log.d(TAG, "Jeton FCM mis à jour avec succès pour l'utilisateur $userId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Échec de la mise à jour du jeton FCM pour l'utilisateur $userId: ${e.message}", e)
                    }
                }
            } else {
                Log.w(TAG, "Aucun utilisateur connecté, impossible de sauvegarder le jeton FCM.")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Message reçu de : ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            Log.d(TAG, "Payload de données du message : $data")

            val title = remoteMessage.notification?.title ?: getString(R.string.app_name)
            val body = remoteMessage.notification?.body ?: ""

            when (val notificationType = data[NOTIFICATION_TYPE_KEY]) {
                TYPE_NEW_MONTHLY_READING,
                TYPE_PHASE_REMINDER,
                TYPE_PHASE_STATUS_CHANGE,
                TYPE_MEETING_LINK_UPDATE -> {
                    sendGeneralNotification(title, body, data[MONTHLY_READING_ID_KEY], notificationType)
                }

                TYPE_NEW_PRIVATE_MESSAGE -> {
                    sendPrivateMessageNotification(title, body, data[CONVERSATION_ID_KEY])
                }

                TYPE_TIER_UPGRADE -> {
                    val intent = Intent(ACTION_TIER_UPGRADE).apply {
                        putExtra(CONVERSATION_ID_KEY, data[CONVERSATION_ID_KEY])
                        putExtra(NEW_TIER_NAME_KEY, data[NEW_TIER_NAME_KEY])
                        putExtra(BODY_KEY, body)
                    }
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                    sendRewardNotification(title, body, data[CONVERSATION_ID_KEY])
                }

                TYPE_NEW_FOLLOWER,
                TYPE_LIKE_ON_READING,
                TYPE_COMMENT_ON_READING,
                TYPE_REPLY_TO_COMMENT,
                TYPE_LIKE_ON_COMMENT -> {
                    sendSocialNotification(
                        title = title,
                        messageBody = body,
                        notificationType = notificationType,
                        actorId = data[ACTOR_ID_KEY],
                        targetUserId = data[TARGET_USER_ID_KEY],
                        commentId = data[COMMENT_ID_KEY]
                    )
                }

                else -> {
                    sendGeneralNotification(title, body, null, null)
                }
            }
        }
    }

    private fun sendGeneralNotification(title: String, messageBody: String, monthlyReadingId: String?, notificationType: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(NOTIFICATION_TYPE_KEY, notificationType)
            putExtra(MONTHLY_READING_ID_KEY, monthlyReadingId)
        }
        val pendingIntent = createPendingIntent(intent, monthlyReadingId.hashCode())
        val notificationBuilder = createNotificationBuilder(CHANNEL_ID_GENERAL, title, messageBody, pendingIntent)
        notify((System.currentTimeMillis() / 1000).toInt(), notificationBuilder)
    }

    private fun sendPrivateMessageNotification(title: String, messageBody: String, conversationId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(NOTIFICATION_TYPE_KEY, TYPE_NEW_PRIVATE_MESSAGE)
            putExtra(CONVERSATION_ID_KEY, conversationId)
        }
        val pendingIntent = createPendingIntent(intent, conversationId.hashCode())
        val notificationBuilder = createNotificationBuilder(CHANNEL_ID_PRIVATE_MESSAGES, title, messageBody, pendingIntent)
        notify(conversationId.hashCode(), notificationBuilder)
    }

    private fun sendRewardNotification(title: String, messageBody: String, conversationId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(NOTIFICATION_TYPE_KEY, TYPE_TIER_UPGRADE)
            putExtra(CONVERSATION_ID_KEY, conversationId)
        }
        val pendingIntent = createPendingIntent(intent, conversationId.hashCode())
        val notificationBuilder = createNotificationBuilder(CHANNEL_ID_REWARDS, title, messageBody, pendingIntent)
        notify(conversationId.hashCode(), notificationBuilder)
    }

    private fun sendSocialNotification(title: String, messageBody: String, notificationType: String?, actorId: String?, targetUserId: String?, commentId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(NOTIFICATION_TYPE_KEY, notificationType)
            putExtra(ACTOR_ID_KEY, actorId)
            putExtra(TARGET_USER_ID_KEY, targetUserId)
            putExtra(COMMENT_ID_KEY, commentId)
        }
        val requestCode = (notificationType.hashCode() + (targetUserId?.hashCode() ?: 0) + (commentId?.hashCode() ?: 0))
        val pendingIntent = createPendingIntent(intent, requestCode)
        val notificationBuilder = createNotificationBuilder(CHANNEL_ID_SOCIAL, title, messageBody, pendingIntent)
        notify((System.currentTimeMillis() / 1000).toInt(), notificationBuilder)
    }

    private fun createPendingIntent(intent: Intent, requestCode: Int): PendingIntent {
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationBuilder(channelId: String, title: String, body: String, pendingIntent: PendingIntent): NotificationCompat.Builder {
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
    }

    private fun notify(notificationId: Int, builder: NotificationCompat.Builder) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels(notificationManager)
        notificationManager.notify(notificationId, builder.build())
    }

    private fun createNotificationChannels(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val generalChannel = NotificationChannel(CHANNEL_ID_GENERAL, CHANNEL_NAME_GENERAL, NotificationManager.IMPORTANCE_HIGH).apply { description = CHANNEL_DESC_GENERAL }
            val rewardsChannel = NotificationChannel(CHANNEL_ID_REWARDS, CHANNEL_NAME_REWARDS, NotificationManager.IMPORTANCE_HIGH).apply { description = CHANNEL_DESC_REWARDS }
            val privateMessagesChannel = NotificationChannel(CHANNEL_ID_PRIVATE_MESSAGES, CHANNEL_NAME_PRIVATE_MESSAGES, NotificationManager.IMPORTANCE_HIGH).apply { description = CHANNEL_DESC_PRIVATE_MESSAGES }
            val socialChannel = NotificationChannel(CHANNEL_ID_SOCIAL, CHANNEL_NAME_SOCIAL, NotificationManager.IMPORTANCE_DEFAULT).apply { description = CHANNEL_DESC_SOCIAL }


            notificationManager.createNotificationChannel(generalChannel)
            notificationManager.createNotificationChannel(rewardsChannel)
            notificationManager.createNotificationChannel(privateMessagesChannel)
            // LIGNE CORRIGÉE
            notificationManager.createNotificationChannel(socialChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}