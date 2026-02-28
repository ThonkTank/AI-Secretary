package com.autosecretary.features.meal.data.internal.repository;

import com.autosecretary.features.meal.data.internal.BaseCollectionDao;
import com.autosecretary.features.meal.data.internal.EntityIdHandler;
import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.ConsumptionLogRowMapper;
import com.autosecretary.features.meal.data.internal.mapper.CookingPreferencesRowMapper;
import com.autosecretary.features.meal.data.internal.mapper.HouseholdMemberRowMapper;
import com.autosecretary.features.meal.data.internal.mapper.MealPlanRowMapper;
import com.autosecretary.features.meal.data.internal.MealFieldKeys;
import com.autosecretary.features.meal.data.internal.mapper.WeeklyFoodTargetRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.ConsumptionLog;
import com.autosecretary.features.meal.domain.CookingPreferences;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Storage-backed implementation of {@link MealRepository}.
 * <p>
 * Adapts the untyped {@link MealStorage} API (which works with {@code Map<String, Object>}) to typed domain entities.
 * Each entity type (MealPlan, ConsumptionLog, HouseholdMember, etc.) gets its own {@link BaseCollectionDao}
 * instance, which handles serialization/deserialization via {@code RowMapper} instances and manages CRUD operations.
 */
public class StorageMealRepository implements MealRepository {

    // CookingPreferences is a singleton: only one row (id=1) is ever persisted.
    // This constraint is enforced in saveCookingPreferences() by always using this id.
    private static final long SINGLETON_PREFERENCES_ID = 1L;

    private final BaseCollectionDao<MealPlan> mealPlanDao;
    private final BaseCollectionDao<ConsumptionLog> consumptionLogDao;
    private final BaseCollectionDao<HouseholdMember> householdMemberDao;
    private final BaseCollectionDao<CookingPreferences> cookingPreferencesDao;
    private final BaseCollectionDao<WeeklyFoodTarget> weeklyFoodTargetDao;

    public StorageMealRepository(MealStorage storage) {
        this.mealPlanDao = new BaseCollectionDao<>(
            MealCollections.MEAL_PLANS,
            storage,
            new MealPlanRowMapper(),
            EntityIdHandler.of(p -> p.id, (p, id) -> p.id = id)
        );
        this.consumptionLogDao = new BaseCollectionDao<>(
            MealCollections.CONSUMPTION_LOGS,
            storage,
            new ConsumptionLogRowMapper(),
            EntityIdHandler.of(log -> log.id, (log, id) -> log.id = id)
        );
        this.householdMemberDao = new BaseCollectionDao<>(
            MealCollections.HOUSEHOLD_MEMBERS,
            storage,
            new HouseholdMemberRowMapper(),
            EntityIdHandler.of(m -> m.id, (m, id) -> m.id = id)
        );
        this.cookingPreferencesDao = new BaseCollectionDao<>(
            MealCollections.COOKING_PREFERENCES,
            storage,
            new CookingPreferencesRowMapper(),
            EntityIdHandler.of(prefs -> prefs.id, (prefs, id) -> prefs.id = id)
        );
        this.weeklyFoodTargetDao = new BaseCollectionDao<>(
            MealCollections.WEEKLY_FOOD_TARGETS,
            storage,
            new WeeklyFoodTargetRowMapper(),
            EntityIdHandler.of(t -> t.id, (t, id) -> t.id = id)
        );
    }

    @Override
    public List<MealPlan> getMealPlans(LocalDate fromInclusive, LocalDate toInclusive) {
        // MealStorage has no range-query API, so we load all meal plans and filter in Java.
        // This is acceptable while meal data volumes are small. If data grows significantly,
        // extend MealStorage with a range-query method to push filtering into the storage layer.
        return mealPlanDao.findAll(plan -> isDateInRange(plan.date, fromInclusive, toInclusive));
    }

    @Override
    public MealPlan findMealPlanById(long mealPlanId) {
        return mealPlanDao.findById(mealPlanId);
    }

    @Override
    public void saveMealPlan(MealPlan mealPlan) {
        mealPlanDao.save(mealPlan);
    }

    @Override
    public void deleteMealPlan(long mealPlanId) {
        mealPlanDao.deleteById(mealPlanId);
    }

    @Override
    public List<ConsumptionLog> getConsumptionLogs(LocalDate fromInclusive, LocalDate toInclusive) {
        // Like getMealPlans(), this loads all consumption logs and filters in Java
        // due to lack of range-query support in MealStorage. See getMealPlans() for details.
        return consumptionLogDao.findAll(log -> isDateInRange(log.date, fromInclusive, toInclusive));
    }

    @Override
    public void saveConsumptionLog(ConsumptionLog consumptionLog) {
        consumptionLogDao.save(consumptionLog);
    }

    @Override
    public List<HouseholdMember> getHouseholdMembers() {
        return householdMemberDao.findAll();
    }

    @Override
    public void saveHouseholdMember(HouseholdMember member) {
        householdMemberDao.save(member);
    }

    @Override
    public void deleteHouseholdMember(long memberId) {
        householdMemberDao.deleteById(memberId);
    }

    @Override
    public CookingPreferences getCookingPreferences() {
        CookingPreferences preferences = cookingPreferencesDao.findById(SINGLETON_PREFERENCES_ID);
        return Objects.requireNonNullElse(preferences, new CookingPreferences());
    }

    @Override
    public void saveCookingPreferences(CookingPreferences preferences) {
        preferences.id = SINGLETON_PREFERENCES_ID;
        cookingPreferencesDao.save(preferences);
    }

    /**
     * Finds the weekly food target for the given period.
     *
     * @param periodKey ISO-8601 date string identifying the period, produced by
     *                  {@link java.time.LocalDate#toString()} (e.g. {@code "2026-02-28"}).
     *                  Must match the value stored in {@link com.autosecretary.features.meal.domain.WeeklyFoodTarget#periodKey}.
     * @return the target for this period, or null if none has been saved yet
     */
    @Override
    public WeeklyFoodTarget findWeeklyFoodTarget(String periodKey) {
        return weeklyFoodTargetDao.findSingleByField(MealFieldKeys.PERIOD_KEY, periodKey);
    }

    @Override
    public void saveWeeklyFoodTarget(WeeklyFoodTarget weeklyFoodTarget) {
        weeklyFoodTargetDao.save(weeklyFoodTarget);
    }

    private static boolean isDateInRange(LocalDate date, LocalDate fromInclusive, LocalDate toInclusive) {
        return date != null && !date.isBefore(fromInclusive) && !date.isAfter(toInclusive);
    }
}
