package bj.repetipro.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Un réglage de l'application modifiable par le SuperAdmin (clé → valeur). Sans valeur, on garde le défaut de l'environnement. */
@Entity
@Table(name = "parametre_app")
public class ParametreApp {

    @Id
    @Column(length = 60)
    private String cle;

    @Column(nullable = false, length = 255)
    private String valeur;

    private LocalDateTime dateMaj = LocalDateTime.now();

    protected ParametreApp() { }

    public ParametreApp(String cle, String valeur) {
        this.cle = cle;
        this.valeur = valeur;
    }

    public String getCle() { return cle; }
    public String getValeur() { return valeur; }
    public void setValeur(String valeur) { this.valeur = valeur; this.dateMaj = LocalDateTime.now(); }
    public LocalDateTime getDateMaj() { return dateMaj; }
}
