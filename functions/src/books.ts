// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier functions/src/books.ts
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { logger } from "firebase-functions";
import { getStorage } from "firebase-admin/storage";

// Interfaces inchangées
interface CreateBookData {
    title: string;
    author: string;
    synopsis: string;
    totalPages: number;
    coverImageBase64?: string;
    pdfFileBase64?: string;
}

const db = admin.firestore();
const bucket = getStorage().bucket();

export const createBookWithFiles = onCall(
    { region: "europe-west1", memory: "512MiB" },
    async (request) => {
        // Étape 1 : Vérifier l'authentification
        if (!request.auth) {
            throw new HttpsError("unauthenticated", "L'utilisateur doit être authentifié.");
        }

        // === DÉBUT DE LA CORRECTION DÉFINITIVE ===
        // JUSTIFICATION: La vérification ne se fait plus sur le document Firestore,
        // mais directement sur les "claims" du jeton d'authentification de l'utilisateur.
        // `request.auth.token` contient les claims décodés. Nous vérifions si la propriété
        // `admin` existe et est à `true`, ce qui correspond exactement à ce que votre
        // script `setAdminClaim()` a configuré. C'est la méthode la plus sécurisée.
        const isAdmin = request.auth.token.admin === true;
        // === FIN DE LA CORRECTION DÉFINITIVE ===

        if (!isAdmin) {
            throw new HttpsError("permission-denied", "Seul un administrateur peut créer un livre.");
        }

        // Le reste de la fonction est parfaitement correct et n'a pas besoin d'être modifié.
        const data = request.data as CreateBookData;
        if (!data.title || !data.author || data.totalPages === undefined) {
            throw new HttpsError("invalid-argument", "Les champs titre, auteur et nombre de pages sont obligatoires.");
        }

        logger.info(`Début de la création du livre "${data.title}" par ${request.auth.uid} (Admin).`);

        let coverImageUrl: string | null = null;
        let pdfFileUrl: string | null = null;
        const bookId = db.collection("books").doc().id;

        try {
            if (data.coverImageBase64) {
                const coverPath = `book_covers/${bookId}/cover.jpg`;
                const imageBuffer = Buffer.from(data.coverImageBase64, "base64");
                const file = bucket.file(coverPath);
                await file.save(imageBuffer, { metadata: { contentType: "image/jpeg" } });
                coverImageUrl = await file.getSignedUrl({ action: 'read', expires: '03-09-2491' }).then(urls => urls[0]);
                logger.info(`Image de couverture uploadée pour le livre ${bookId}.`);
            }

            if (data.pdfFileBase64) {
                const pdfPath = `book_pdfs/${bookId}/content.pdf`;
                const pdfBuffer = Buffer.from(data.pdfFileBase64, "base64");
                const file = bucket.file(pdfPath);
                await file.save(pdfBuffer, { metadata: { contentType: "application/pdf" } });
                pdfFileUrl = await file.getSignedUrl({ action: 'read', expires: '03-09-2491' }).then(urls => urls[0]);
                logger.info(`Fichier PDF uploadé pour le livre ${bookId}.`);
            }

            const newBook = {
                title: data.title,
                author: data.author,
                synopsis: data.synopsis || null,
                totalPages: data.totalPages,
                coverImageUrl: coverImageUrl,
                contentUrl: pdfFileUrl,
                proposedAt: admin.firestore.FieldValue.serverTimestamp(),
                likesCount: 0,
                favoritesCount: 0,
                recommendationsCount: 0,
            };

            await db.collection("books").doc(bookId).set(newBook);
            logger.info(`Livre ${bookId} ("${data.title}") créé avec succès dans Firestore.`);

            return { success: true, bookId: bookId };

        } catch (error) {
            logger.error(`Erreur lors de la création du livre "${data.title}" :`, error);
            logger.warn(`Tentative de nettoyage pour le livre ${bookId} suite à une erreur.`);
            if (coverImageUrl) await bucket.file(`book_covers/${bookId}/cover.jpg`).delete().catch(e => logger.error("Échec de la suppression de l'image de couverture", e));
            if (pdfFileUrl) await bucket.file(`book_pdfs/${bookId}/content.pdf`).delete().catch(e => logger.error("Échec de la suppression du PDF", e));

            throw new HttpsError("internal", "Une erreur est survenue lors de la création du livre. L'opération a été annulée.", error);
        }
    }
);