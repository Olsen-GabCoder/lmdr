package com.lesmangeursdurouleau.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.databinding.ActivityMainBinding
import com.lesmangeursdurouleau.app.notifications.MyFirebaseMessagingService
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    @Inject
    lateinit var userProfileRepository: UserProfileRepository
    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Permission de notification accordée")
        } else {
            Log.w("MainActivity", "Permission de notification refusée")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_main) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)

        askNotificationPermission()
        saveFCMToken()
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateUserStatus(true)
    }

    override fun onPause() {
        super.onPause()
        updateUserStatus(false)
    }

    private fun updateUserStatus(isOnline: Boolean) {
        firebaseAuth.currentUser?.uid?.let { userId ->
            lifecycleScope.launch(Dispatchers.IO) {
                userProfileRepository.updateUserPresence(userId, isOnline)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Il est crucial de gérer également l'intent ici pour les cas où l'activité est déjà ouverte.
        handleNotificationIntent(intent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("MainActivity", "Permission POST_NOTIFICATIONS déjà accordée.")
            } else if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                Log.d("MainActivity", "Explication nécessaire pour la permission POST_NOTIFICATIONS.")
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun saveFCMToken() {
        firebaseAuth.currentUser?.let { user ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("MainActivity", "La récupération du jeton FCM a échoué", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d("MainActivity", "Jeton FCM de l'appareil : $token")
                lifecycleScope.launch {
                    val result = userProfileRepository.updateUserFCMToken(user.uid, token)
                    if (result is Resource.Success) {
                        Log.d("MainActivity", "Jeton FCM sauvegardé avec succès pour l'utilisateur ${user.uid}")
                    } else if (result is Resource.Error){
                        Log.e("MainActivity", "Échec de la sauvegarde du jeton FCM: ${result.message}")
                    }
                }
            }
        } ?: run {
            Log.d("MainActivity", "Aucun utilisateur connecté, impossible de sauvegarder le jeton FCM.")
        }
    }

    // MODIFICATION (Chantier 3) : Logique de deep linking entièrement réécrite
    private fun handleNotificationIntent(intent: Intent?) {
        intent?.extras?.let { extras ->
            val notificationType = extras.getString(MyFirebaseMessagingService.NOTIFICATION_TYPE_KEY)
            Log.d("MainActivity", "Handling notification intent. Type: $notificationType")

            when (notificationType) {
                MyFirebaseMessagingService.TYPE_NEW_FOLLOWER,
                MyFirebaseMessagingService.TYPE_LIKE_ON_READING,
                MyFirebaseMessagingService.TYPE_COMMENT_ON_READING,
                MyFirebaseMessagingService.TYPE_REPLY_TO_COMMENT,
                MyFirebaseMessagingService.TYPE_LIKE_ON_COMMENT -> {

                    val destinationUserId = if (notificationType == MyFirebaseMessagingService.TYPE_NEW_FOLLOWER) {
                        extras.getString(MyFirebaseMessagingService.ACTOR_ID_KEY)
                    } else {
                        extras.getString(MyFirebaseMessagingService.TARGET_USER_ID_KEY)
                    }

                    if (!destinationUserId.isNullOrBlank()) {
                        val commentId = extras.getString(MyFirebaseMessagingService.COMMENT_ID_KEY)
                        val args = bundleOf(
                            "userId" to destinationUserId,
                            "scrollToCommentId" to commentId
                        )
                        navController.navigate(R.id.publicProfileFragmentDestination, args)
                    } else {
                        Log.w("MainActivity", "Social notification received without a valid destination user ID.")
                    }
                }

                MyFirebaseMessagingService.TYPE_NEW_PRIVATE_MESSAGE,
                MyFirebaseMessagingService.TYPE_TIER_UPGRADE -> {
                    // Pour les messages et récompenses, on navigue vers la liste des conversations
                    Log.d("MainActivity", "Navigating to conversations list.")
                    navController.navigate(R.id.conversationsListFragmentDestination)
                }

                MyFirebaseMessagingService.TYPE_NEW_MONTHLY_READING,
                MyFirebaseMessagingService.TYPE_PHASE_REMINDER,
                MyFirebaseMessagingService.TYPE_PHASE_STATUS_CHANGE,
                MyFirebaseMessagingService.TYPE_MEETING_LINK_UPDATE -> {
                    // Comportement existant pour les lectures
                    Log.d("MainActivity", "Navigating to readings screen.")
                    navController.navigate(R.id.navigation_readings)
                }

                else -> {
                    Log.w("MainActivity", "Notification intent with unhandled type: $notificationType")
                }
            }

            // Important: Consommer l'intent pour éviter une re-navigation lors des changements de configuration.
            setIntent(Intent())
        }
    }
}