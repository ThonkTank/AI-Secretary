package entities;

import java.time.LocalDate;

/**
 * Bewertung eines Rezepts durch ein Haushaltsmitglied.
 * Ermöglicht personalisierte Rezeptauswahl basierend auf individuellen Vorlieben.
 */
public class RecipeRating {

    public Long id;
    public Long recipeId;                    // FK zu Recipe
    public Long memberId;                    // FK zu HouseholdMember
    public int rating;                       // 1-5 Sterne
    public LocalDate ratedAt;

    // Builder
    public static class Builder {
        private final RecipeRating r = new RecipeRating();

        public Builder(Long recipeId, Long memberId, int rating) {
            r.recipeId = recipeId;
            r.memberId = memberId;
            r.rating = Math.max(1, Math.min(5, rating));  // Clamp 1-5
            r.ratedAt = LocalDate.now();
        }

        public Builder ratedAt(LocalDate v) { r.ratedAt = v; return this; }

        public RecipeRating build() { return r; }
    }
}
