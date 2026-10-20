package bj.repetipro.model;

/** État de présence d'une séance. */
public enum Presence {
    NON_POINTEE("À pointer"),
    PRESENT("Présent"),
    EN_RETARD("En retard"),
    ABSENT("Absent");

    private final String libelle;

    Presence(String libelle) { this.libelle = libelle; }

    /** Texte affiché à l'écran et dans les messages. */
    public String getLibelle() { return libelle; }
}
