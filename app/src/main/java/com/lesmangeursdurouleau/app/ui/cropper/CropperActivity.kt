// PRÊT À COLLER - Fichier CropperActivity.kt
package com.lesmangeursdurouleau.app.ui.cropper

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.ActivityCropperBinding
import com.yalantis.ucrop.UCrop
import java.io.File

class CropperActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCropperBinding

    companion object {
        private const val TAG = "CropperActivity"
        const val EXTRA_INPUT_URI = "EXTRA_INPUT_URI"
        const val EXTRA_CROP_SHAPE = "EXTRA_CROP_SHAPE"
        const val EXTRA_ASPECT_RATIO_X = "EXTRA_ASPECT_RATIO_X"
        const val EXTRA_ASPECT_RATIO_Y = "EXTRA_ASPECT_RATIO_Y"

        const val SHAPE_CIRCLE = "SHAPE_CIRCLE"
        const val SHAPE_RECTANGLE = "SHAPE_RECTANGLE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropperBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        val inputUriString = intent.getStringExtra(EXTRA_INPUT_URI)
        if (inputUriString == null) {
            Log.e(TAG, "L'URI d'entrée est manquante. Fermeture de l'activité.")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val inputUri = Uri.parse(inputUriString)
        startCrop(inputUri)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun startCrop(sourceUri: Uri) {
        // Crée un fichier de destination unique dans le cache de l'application
        val destinationFileName = "${System.currentTimeMillis()}_${File(sourceUri.path ?: "cropped").name}.jpg"
        val destinationUri = Uri.fromFile(File(cacheDir, destinationFileName))

        val options = UCrop.Options().apply {
            // Personnalisation de l'apparence de uCrop
            setCompressionQuality(90)
            setToolbarColor(ContextCompat.getColor(this@CropperActivity, R.color.ucrop_color_toolbar))
            setStatusBarColor(ContextCompat.getColor(this@CropperActivity, R.color.ucrop_color_statusbar))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@CropperActivity, R.color.ucrop_color_widget_active))
            setToolbarWidgetColor(ContextCompat.getColor(this@CropperActivity, R.color.ucrop_color_toolbar_widget))
            setRootViewBackgroundColor(ContextCompat.getColor(this@CropperActivity, R.color.ucrop_color_root_view_background))

            // Récupération des options de recadrage depuis l'intent
            when (intent.getStringExtra(EXTRA_CROP_SHAPE)) {
                SHAPE_CIRCLE -> setCircleDimmedLayer(true)
                SHAPE_RECTANGLE -> setCircleDimmedLayer(false)
            }
            val ratioX = intent.getFloatExtra(EXTRA_ASPECT_RATIO_X, 0f)
            val ratioY = intent.getFloatExtra(EXTRA_ASPECT_RATIO_Y, 0f)
            if (ratioX > 0 && ratioY > 0) {
                withAspectRatio(ratioX, ratioY)
            }
        }

        // JUSTIFICATION : Configuration et lancement de l'activité uCrop.
        // C'est l'appel principal qui démarre l'interface de recadrage.
        // Il prend l'URI source, l'URI de destination, et les options de configuration.
        UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .start(this)
    }

    // JUSTIFICATION : uCrop renvoie son résultat via onActivityResult. Nous interceptons ce résultat ici.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UCrop.REQUEST_CROP) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // Le recadrage a réussi.
                val resultUri = UCrop.getOutput(data)
                Log.i(TAG, "Recadrage réussi. URI de sortie: $resultUri")
                if (resultUri != null) {
                    // Nous renvoyons l'URI de l'image recadrée à l'appelant (ProfileFragment).
                    setResult(Activity.RESULT_OK, Intent().setData(resultUri))
                } else {
                    Log.e(TAG, "L'URI de sortie de uCrop est nulle.")
                    setResult(Activity.RESULT_CANCELED)
                }
            } else if (resultCode == UCrop.RESULT_ERROR) {
                // Une erreur s'est produite pendant le recadrage.
                val cropError = data?.let { UCrop.getError(it) }
                Log.e(TAG, "Erreur de recadrage: ", cropError)
                setResult(Activity.RESULT_CANCELED)
            } else {
                // L'utilisateur a annulé l'opération.
                Log.d(TAG, "Recadrage annulé par l'utilisateur.")
                setResult(Activity.RESULT_CANCELED)
            }
            // Dans tous les cas, nous fermons cette activité hôte.
            finish()
        }
    }

    // JUSTIFICATION : La gestion du menu est laissée vide car uCrop gère sa propre barre d'actions.
    // Nous pourrions l'utiliser pour ajouter un bouton "Valider" si nécessaire, mais uCrop le fournit déjà.
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }
}