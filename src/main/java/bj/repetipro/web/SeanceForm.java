package bj.repetipro.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/** Formulaire "planifier une séance". */
public class SeanceForm {

    @NotNull(message = "Choisissez un élève")
    private Long eleveId;

    @NotNull(message = "Date obligatoire")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)   // format du champ <input type="date">
    private LocalDate date;

    @NotNull(message = "Heure obligatoire")
    @DateTimeFormat(pattern = "HH:mm")               // format du champ <input type="time">
    private LocalTime heure;

    @NotNull
    @Min(value = 15, message = "15 minutes minimum")
    @Max(value = 480, message = "8 heures maximum")
    private Integer dureeMinutes = 120;

    public Long getEleveId() { return eleveId; }
    public void setEleveId(Long eleveId) { this.eleveId = eleveId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getHeure() { return heure; }
    public void setHeure(LocalTime heure) { this.heure = heure; }
    public Integer getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(Integer dureeMinutes) { this.dureeMinutes = dureeMinutes; }
}
