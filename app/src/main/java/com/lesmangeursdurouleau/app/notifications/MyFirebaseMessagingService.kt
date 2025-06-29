// PRÊT À COLLER - Fichier complet avec la CORRECTION pour les notifications de message
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

        const val CHANNEL_ID_GENERAL = "general_notifications_channel"
        const val CHANNEL_NAME_GENERAL = "Notifications Générales"
        const val CHANNEL_DESC_GENERAL = "Notifications générales du club de lecture."

        const val CHANNEL_ID_PRIVATE_MESSAGES = "private_messages_channel" // Canal existant, juste déclaré ici
        const val CHANNEL_NAME_PRIVATE_MESSAGES = "Messages Privés"
        const val CHANNEL_DESC_PRIVATE_MESSAGES = "Notifications pour les nouveaux messages privés."

        const val CHANNEL_ID_REWARDS = "rewards_notifications_channel"
        const val CHANNEL_NAME_REWARDS = "Récompenses et Affinité"
        const val CHANNEL_DESC_REWARDS = "Notifications pour les nouveaux paliers d'affinité."

        const val NOTIFICATION_TYPE_KEY = "notificationType"
        const val TITLE_KEY = "title"
        const val BODY_KEY = "body"

        const val MONTHLY_READING_ID_KEY = "monthlyReadingId"

        const val CONVERSATION_ID_KEY = "conversationId"
        const val NEW_TIER_NAME_KEY = "newTierName"
        const val PARTNER_NAME_KEY = "partnerName"

        const val TYPE_NEW_MONTHLY_READING = "new_monthly_reading"
        const val TYPE_PHASE_REMINDER = "phase_reminder"
        const val TYPE_PHASE_STATUS_CHANGE = "phase_status_change"
        const val TYPE_MEETING_LINK_UPDATE = "meeting_link_update"
        const val TYPE_NEW_PRIVATE_MESSAGE = "new_private_message"
        const val TYPE_TIER_UPGRADE = "TIER_UPGRADE"
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

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Payload de données du message : ${remoteMessage.data}")

            val notificationType = remoteMessage.data[NOTIFICATION_TYPE_KEY] ?: ""
            val title = remoteMessage.data[TITLE_KEY] ?: remoteMessage.notification?.title ?: getString(R.string.app_name)
            val body = remoteMessage.data[BODY_KEY] ?: remoteMessage.notification?.body ?: ""

            when (notificationType) {
                TYPE_NEW_MONTHLY_READING,
                TYPE_PHASE_REMINDER,
                TYPE_PHASE_STATUS_CHANGE,
                TYPE_MEETING_LINK_UPDATE -> {
                    val monthlyReadingId = remoteMessage.data[MONTHLY_READING_ID_KEY]
                    sendGeneralNotification(title, body, monthlyReadingId, notificationType)
                }

                // === DÉBUT DE LA CORRECTION ===
                TYPE_NEW_PRIVATE_MESSAGE -> {
                    val conversationId = remoteMessage.data[CONVERSATION_ID_KEY]
                    Log.d(TAG, "Notification de nouveau message privé reçue pour la conversation $conversationId.")
                    // On recrée la notification manuellement pour être sûr qu'elle s'affiche
                    sendPrivateMessageNotification(title, body, conversationId)
                }
                // === FIN DE LA CORRECTION ===

                TYPE_TIER_UPGRADE -> {
                    val conversationId = remoteMessage.data[CONVERSATION_ID_KEY]
                    val newTierName = remoteMessage.data[NEW_TIER_NAME_KEY]

                    val intent = Intent(ACTION_TIER_UPGRADE).apply {
                        putExtra(CONVERSATION_ID_KEY, conversationId)
                        putExtra(NEW_TIER_NAME_KEY, newTierName)
                        putExtra(BODY_KEY, body)
                    }
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                    Log.d(TAG, "Broadcast local TIER_UPGRADE envoyé pour la conversation $conversationId")

                    sendRewardNotification(title, body, conversationId)
                }

                else -> {
                    sendGeneralNotification(title, body, null, null)
                }
            }
        } else {
            remoteMessage.notification?.let {
                Log.d(TAG, "Message de type 'notification' simple reçu : ${it.body}")
                sendGeneralNotification(it.title ?: getString(R.string.app_name), it.body ?: "", null, null)
            }
        }
    }

    private fun sendGeneralNotification(title: String, messageBody: String, monthlyReadingId: String?, notificationType: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MONTHLY_READING_ID_KEY, monthlyReadingId)
            putExtra(NOTIFICATION_TYPE_KEY, notificationType)
        }
        val pendingIntent = createPendingIntent(intent, monthlyReadingId.hashCode())
        val notificationBuilder = createNotificationBuilder(CHANNEL_ID_GENERAL, title, messageBody, pendingIntent)
        notify((System.currentTimeMillis() / 1000).toInt(), notificationBuilder)
    }

    // === NOUVELLE FONCTION POUR LES MESSAGES PRIVÉS ===
    private fun sendPrivateMessageNotification(title: String, messageBody: String, conversationId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(NOTIFICATION_TYPE_KEY, TYPE_NEW_PRIVATE_MESSAGE)
            putExtra(CONVERSATION_ID_KEY, conversationId)
        }
        val pendingIntent = createPendingIntent(intent, conversationId.hashCode())
        // On s'assure d'utiliser le bon canal, celui que vous utilisiez déjà
        val notificationBuilder = createNotificationBuilder(CHANNEL_ID_PRIVATE_MESSAGES, title, messageBody, pendingIntent)
        // On utilise l'ID de la conversation comme ID de notification pour que les messages d'une même conversation se regroupent/remplacent
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

    private fun createPendingIntent(intent: Intent, requestCode: Int): PendingIntent {
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
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
            // On déclare aussi le canal des messages privés ici pour être sûr qu'il existe
            val privateMessagesChannel = NotificationChannel(CHANNEL_ID_PRIVATE_MESSAGES, CHANNEL_NAME_PRIVATE_MESSAGES, NotificationManager.IMPORTANCE_HIGH).apply { description = CHANNEL_DESC_PRIVATE_MESSAGES }

            notificationManager.createNotificationChannel(generalChannel)
            notificationManager.createNotificationChannel(rewardsChannel)
            notificationManager.createNotificationChannel(privateMessagesChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}