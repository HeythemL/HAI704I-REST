package hai704.tp.demo.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record OffreRequestDTO(
        @NotBlank(message = "La ville est obligatoire")
        @Size(min = 2, max = 100, message = "La ville doit contenir entre 2 et 100 caractères")
        String ville,

        @NotNull(message = "La date de début est obligatoire")
        LocalDate dateDebut,

        @NotNull(message = "La date de fin est obligatoire")
        LocalDate dateFin,

        @NotNull(message = "Le nombre de personnes est obligatoire")
        @Min(value = 1, message = "Au moins 1 personne")
        @Max(value = 10, message = "Maximum 10 personnes")
        Integer nbPersonnes
) {
    // Validation croisée : dateDebut <= dateFin
    public boolean isDateRangeValid() {
        return dateDebut != null && dateFin != null && !dateDebut.isAfter(dateFin);
    }
}