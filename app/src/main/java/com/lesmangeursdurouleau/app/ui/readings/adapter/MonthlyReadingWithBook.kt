// PRÊT À COLLER - Créez un nouveau fichier MonthlyReadingWithBook.kt
package com.lesmangeursdurouleau.app.ui.readings.adapter

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading

/**
 * JUSTIFICATION: Cette classe de données combine une lecture mensuelle avec son livre associé.
 * En la plaçant dans son propre fichier, elle devient une entité partagée et réutilisable
 * à travers l'application (par exemple, par le panel d'administration et la liste des utilisateurs),
 * ce qui corrige l'erreur de portée précédente.
 */
data class MonthlyReadingWithBook(
    val monthlyReading: MonthlyReading,
    val book: Book?
)