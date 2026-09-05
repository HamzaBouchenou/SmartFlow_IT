"""Génère le jeu de données d'exemples anonymisés/synthétiques pour la classification IA
(§12.2 - "Commencer par une base de données d'exemples anonymisés ou générés pour
entraîner et évaluer la classification"). Aucune donnée réelle : uniquement des phrases
composées par gabarit, en français, dans le vocabulaire d'un support IT/achats interne.

Sortie : data/training_data.csv (title,description,category,priority).
"""
import csv
import random
from pathlib import Path

random.seed(42)

# Chaque catégorie porte plusieurs sujets, chacun avec plusieurs formulations de titre et
# de description - le vocabulaire est ce qui doit permettre à un modèle d'apprendre un
# signal réel plutôt que de mémoriser une phrase unique.
SUBJECTS = {
    "MATERIEL": [
        ("Écran qui ne s'allume plus", "Mon écran externe ne s'allume plus depuis ce matin, l'ordinateur fonctionne normalement par ailleurs."),
        ("Clavier défectueux", "Plusieurs touches de mon clavier ne répondent plus, notamment la barre d'espace."),
        ("Souris qui ne répond plus", "La souris filaire de mon poste ne bouge plus le curseur, j'ai déjà changé de port USB."),
        ("Imprimante bloquée", "L'imprimante du service affiche un bourrage papier permanent malgré plusieurs tentatives de nettoyage."),
        ("Ordinateur portable très lent", "Mon ordinateur portable met plus de dix minutes à démarrer et rame sur toutes les applications."),
        ("Batterie d'ordinateur portable défaillante", "La batterie de mon portable ne tient plus que quelques minutes hors secteur."),
        ("Casque audio hors service", "Mon casque ne produit plus aucun son lors des appels visio."),
        ("Écran fissuré", "L'écran de mon ordinateur portable est fissuré suite à une chute, l'affichage reste partiellement lisible."),
    ],
    "LOGICIEL": [
        ("Impossible d'installer le logiciel comptable", "Le programme d'installation du logiciel comptable s'arrête avec une erreur inconnue."),
        ("Application métier qui plante", "L'application métier se ferme brutalement dès que j'ouvre un dossier client."),
        ("Mise à jour bloquée", "La mise à jour de l'outil bureautique reste bloquée à 40% depuis hier."),
        ("Besoin d'une licence logicielle supplémentaire", "Je souhaite obtenir une licence supplémentaire pour l'outil de conception graphique."),
        ("Bug d'affichage dans le logiciel RH", "Le logiciel RH affiche des données erronées sur l'onglet congés depuis la dernière mise à jour."),
        ("Logiciel de messagerie qui ne synchronise plus", "Le client de messagerie ne synchronise plus les nouveaux e-mails depuis ce matin."),
        ("Erreur au lancement d'une application", "L'application de gestion de projet affiche une erreur au démarrage et se ferme aussitôt."),
    ],
    "ACCES_COMPTE": [
        ("Mot de passe oublié", "J'ai oublié le mot de passe de ma session et je ne peux plus me connecter."),
        ("Compte bloqué après plusieurs essais", "Mon compte a été bloqué après plusieurs tentatives de connexion infructueuses."),
        ("Demande d'accès à un dossier partagé", "Je souhaite obtenir l'accès en lecture au dossier partagé du service comptabilité."),
        ("Accès VPN refusé", "La connexion VPN refuse mes identifiants alors qu'ils fonctionnaient la semaine dernière."),
        ("Droits insuffisants sur une application", "Je n'ai plus les droits nécessaires pour valider les commandes dans l'application achats."),
        ("Badge d'accès désactivé", "Mon badge d'accès aux locaux ne fonctionne plus depuis mon changement de service."),
        ("Nouveau compte utilisateur", "Merci de créer un compte pour le nouvel arrivant qui commence lundi prochain."),
    ],
    "RESEAU": [
        ("Wifi instable au bureau", "La connexion wifi se coupe régulièrement dans notre open space depuis deux jours."),
        ("Connexion internet très lente", "La connexion internet est anormalement lente ce matin sur tout l'étage."),
        ("VPN qui se déconnecte", "Ma connexion VPN se coupe toutes les dix minutes en télétravail."),
        ("Aucun accès réseau depuis ce matin", "Mon poste n'a plus aucun accès au réseau interne depuis ce matin."),
        ("Partage réseau inaccessible", "Le lecteur réseau partagé de l'équipe est inaccessible depuis hier soir."),
        ("Débit réseau très faible en visio", "Les visioconférences sont très saccadées à cause d'un débit réseau insuffisant."),
    ],
    "ACHAT": [
        ("Demande de devis fournisseur", "Merci de solliciter un devis auprès de notre fournisseur habituel pour du mobilier de bureau."),
        ("Commande de matériel informatique", "Je souhaite commander deux ordinateurs portables pour les nouveaux arrivants du service."),
        ("Renouvellement de licence logicielle", "La licence de notre outil de conception arrive à expiration le mois prochain, merci de la renouveler."),
        ("Achat de fournitures de bureau", "Le service manque de fournitures de bureau courantes, merci de passer commande."),
        ("Validation d'un budget d'achat", "Je soumets une demande de validation budgétaire pour l'achat de nouveaux serveurs."),
        ("Commande de mobilier ergonomique", "Plusieurs collaborateurs demandent un siège ergonomique suite à une recommandation médicale."),
    ],
    "AUTRE": [
        ("Question générale sur une procédure", "Je souhaite connaître la procédure à suivre pour signaler un incident de sécurité."),
        ("Demande d'information diverse", "Pourriez-vous me confirmer les horaires d'ouverture du support technique ?"),
        ("Suggestion d'amélioration", "Je propose d'ajouter une FAQ pour les questions les plus fréquentes du service."),
        ("Demande de formation", "Je souhaite m'inscrire à une formation sur les outils bureautiques."),
        ("Retour d'expérience", "Je souhaite faire un retour sur mon expérience récente avec le support informatique."),
    ],
}

URGENCY_PHRASES = {
    "CRITICAL": [
        "C'est totalement bloquant, toute mon équipe est à l'arrêt, besoin d'une intervention immédiate.",
        "Situation critique : la production est interrompue, merci d'intervenir en urgence absolue.",
        "Blocage total, aucune activité possible tant que ce n'est pas résolu, urgence maximale.",
    ],
    "HIGH": [
        "C'est assez urgent, cela m'empêche de travailler normalement aujourd'hui.",
        "Merci de traiter rapidement, cela ralentit fortement mon activité.",
        "Besoin d'une résolution rapide, l'impact sur mon travail est important.",
    ],
    "MEDIUM": [
        "Ce n'est pas bloquant mais j'aimerais une résolution dans les prochains jours.",
        "Merci de traiter cette demande quand vous aurez un moment, pas d'urgence particulière.",
        "Impact modéré sur mon activité, une prise en charge sous quelques jours conviendrait.",
    ],
    "LOW": [
        "Aucune urgence, vous pouvez traiter cela quand vous le pourrez.",
        "Ce n'est pas prioritaire de mon côté, prenez votre temps.",
        "Simple remarque sans caractère urgent.",
    ],
}

PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]


def build_rows():
    rows = []
    for category, subjects in SUBJECTS.items():
        for (title, description) in subjects:
            for priority in PRIORITIES:
                for urgency_phrase in URGENCY_PHRASES[priority]:
                    full_description = f"{description} {urgency_phrase}"
                    rows.append((title, full_description, category, priority))
    random.shuffle(rows)
    return rows


def main():
    rows = build_rows()
    out_path = Path(__file__).parent / "training_data.csv"
    with out_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["title", "description", "category", "priority"])
        writer.writerows(rows)
    print(f"Wrote {len(rows)} rows to {out_path}")


if __name__ == "__main__":
    main()
