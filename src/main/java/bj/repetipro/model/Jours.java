package bj.repetipro.model;

import java.time.LocalTime;

/** Noms des jours en français (1 = lundi … 7 = dimanche) et affichage des heures. */
public final class Jours {
    private Jours() { }

    public static final String[] NOMS = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};

    public static String libelle(int jour) { return jour >= 1 && jour <= 7 ? NOMS[jour - 1] : "?"; }

    /** 17:00 -> "17h00" */
    public static String heure(LocalTime h) { return String.format("%02dh%02d", h.getHour(), h.getMinute()); }
}
