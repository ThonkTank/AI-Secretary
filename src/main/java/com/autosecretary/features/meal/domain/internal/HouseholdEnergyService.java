package com.autosecretary.features.meal.domain.internal;

import com.autosecretary.features.meal.domain.HouseholdMember;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Energy requirement calculations for household members.
 *
 * <p>Provides calorie/energy calculations needed for meal planning:</p>
 * <ul>
 *   <li><strong>BMR (Basal Metabolic Rate):</strong> minimum daily calories burned at rest, calculated
 *       using the Mifflin-St Jeor formula. Universal and widely published across nutritional sciences.</li>
 *   <li><strong>TDEE (Total Daily Energy Expenditure):</strong> BMR adjusted by activity level; represents
 *       total daily calorie burn for a given lifestyle.</li>
 *   <li><strong>DGE Food Factor:</strong> ratio of personal TDEE to the official DGE (Deutsche Gesellschaft
 *       für Ernährung / German Nutrition Society) reference baseline (2000 kcal/day), used to scale
 *       recommended daily food group portions to individual needs.</li>
 * </ul>
 *
 * <p>Reference: Mifflin-St Jeor formula is documented at
 * <a href="https://en.wikipedia.org/wiki/Basal_metabolic_rate">Wikipedia BMR</a>.
 * DGE reference values: <a href="https://www.dge.de">Deutsche Gesellschaft für Ernährung</a>.</p>
 */
public class HouseholdEnergyService {

    // Official DGE reference baseline for adult energy requirements (kcal/day)
    private static final double DGE_REFERENCE_KCAL = 2000.0;

    // Mifflin-St Jeor formula coefficients (BMR calculation)
    private static final double MIFFLIN_WEIGHT_COEFF = 10.0;
    private static final double MIFFLIN_HEIGHT_COEFF = 6.25;
    private static final double MIFFLIN_AGE_COEFF = 5.0;
    private static final double FEMALE_BMR_INTERCEPT = -161.0;
    private static final double MALE_BMR_INTERCEPT = 5.0;
    // Gender.OTHER uses the average of male and female intercepts (reasonable approximation)
    private static final double OTHER_BMR_INTERCEPT = (FEMALE_BMR_INTERCEPT + MALE_BMR_INTERCEPT) / 2.0;

    /**
     * Calculates age as of a given reference date.
     *
     * <p>Takes a reference date (rather than using today's date) to ensure deterministic behavior
     * in energy calculations — important for testing and for scenarios where a user wants to know
     * meal requirements for a future or past date without affecting their stored profile.</p>
     *
     * @param member the household member (must not be null)
     * @param referenceDate the date as of which to calculate age (must not be null)
     * @return age in complete years, clamped to 0 (negative ages are not valid)
     */
    public static int calculateAge(HouseholdMember member, LocalDate referenceDate) {
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(referenceDate, "referenceDate must not be null");
        return Math.max(0, referenceDate.getYear() - member.birthYear);
    }

    /**
     * Calculates Basal Metabolic Rate (BMR) using the Mifflin-St Jeor formula.
     *
     * <p>BMR is the minimum number of calories your body needs to function at rest — breathing,
     * maintaining body temperature, cell repair, etc. It is the foundation for calculating total
     * daily energy needs (TDEE). The Mifflin-St Jeor formula is one of the most widely studied and
     * accepted methods in nutritional science.</p>
     *
     * <p>Formula: BMR = 10×weight(kg) + 6.25×height(cm) − 5×age(years) + gender-specific constant</p>
     *
     * @param member the household member (must not be null; all numeric fields must be valid/non-negative)
     * @param referenceDate the date as of which age is calculated (must not be null)
     * @return BMR in kcal/day (rounded to nearest integer)
     */
    public static int calculateBmr(HouseholdMember member, LocalDate referenceDate) {
        Objects.requireNonNull(member, "member must not be null");
        int age = calculateAge(member, referenceDate);
        double genderIntercept = switch (member.gender) {
            case FEMALE -> FEMALE_BMR_INTERCEPT;
            case MALE -> MALE_BMR_INTERCEPT;
            case OTHER -> OTHER_BMR_INTERCEPT;
        };
        double weightTerm = MIFFLIN_WEIGHT_COEFF * member.weightKg;
        double heightTerm = MIFFLIN_HEIGHT_COEFF * member.heightCm;
        double ageTerm = MIFFLIN_AGE_COEFF * age;
        return (int) Math.round(weightTerm + heightTerm - ageTerm + genderIntercept);
    }

    /**
     * Calculates Total Daily Energy Expenditure (TDEE) by adjusting BMR for activity level.
     *
     * <p>TDEE represents the total number of calories burned in a day given the member's lifestyle.
     * It is BMR multiplied by an activity factor (e.g., sedentary = 1.2, very active = 1.9).
     * Use TDEE to determine daily calorie targets for meal planning.</p>
     *
     * @param member the household member (must have a non-null activityLevel with a valid factor)
     * @param referenceDate the date as of which age is calculated for BMR (must not be null)
     * @return TDEE in kcal/day (rounded to nearest integer)
     */
    public static int calculateTdee(HouseholdMember member, LocalDate referenceDate) {
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(member.activityLevel, "member.activityLevel must not be null");
        return (int) (calculateBmr(member, referenceDate) * member.activityLevel.factor);
    }

    /**
     * Calculates a scaling factor for DGE (Deutsche Gesellschaft für Ernährung) food group recommendations.
     *
     * <p>DGE publishes recommended daily portions for each food group (grains, vegetables, fruits, dairy, etc.)
     * based on a standard 2000 kcal/day diet. To personalize these recommendations for a household member,
     * multiply each DGE portion by this factor. For example, a member with TDEE 2500 kcal will have a factor
     * of 1.25 (2500 / 2000), so all recommended portions scale up by 25%.</p>
     *
     * @param member the household member (must not be null; activityLevel required for TDEE calculation)
     * @param referenceDate the date as of which to calculate TDEE (must not be null)
     * @return multiplier for DGE reference portions (e.g., 1.0 = standard, 1.25 = 25% more)
     */
    public static double calculateDgeFoodFactor(HouseholdMember member, LocalDate referenceDate) {
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(referenceDate, "referenceDate must not be null");
        return calculateTdee(member, referenceDate) / DGE_REFERENCE_KCAL;
    }

    private HouseholdEnergyService() {}
}
