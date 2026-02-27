package com.autosecretary.features.meal.data.internal.repository;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.dao.BaseCollectionDao;
import com.autosecretary.features.meal.data.internal.mapper.ConsumptionLogRowMapper;
import com.autosecretary.features.meal.data.internal.mapper.CookingPreferencesRowMapper;
import com.autosecretary.features.meal.data.internal.mapper.HouseholdMemberRowMapper;
import com.autosecretary.features.meal.data.internal.mapper.MealPlanRowMapper;
import com.autosecretary.features.meal.data.internal.mapper.MealFieldKeys;
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

public class StorageMealRepository implements MealRepository {

    private static final long SINGLETON_PREFERENCES_ID = 1L;

    private final BaseCollectionDao<MealPlan> mealPlanDao;
    private final BaseCollectionDao<ConsumptionLog> consumptionLogDao;
    private final BaseCollectionDao<HouseholdMember> householdMemberDao;
    private final BaseCollectionDao<CookingPreferences> cookingPreferencesDao;
    private final BaseCollectionDao<WeeklyFoodTarget> weeklyFoodTargetDao;

    public StorageMealRepository(MealStorage storage) {
        this.mealPlanDao = new BaseCollectionDao<>(MealCollections.MEAL_PLANS, storage, new MealPlanRowMapper(), mealPlan -> mealPlan.id, (mealPlan, id) -> mealPlan.id = id);
        this.consumptionLogDao = new BaseCollectionDao<>(MealCollections.CONSUMPTION_LOGS, storage, new ConsumptionLogRowMapper(), log -> log.id, (log, id) -> log.id = id);
        this.householdMemberDao = new BaseCollectionDao<>(MealCollections.HOUSEHOLD_MEMBERS, storage, new HouseholdMemberRowMapper(), m -> m.id, (m, id) -> m.id = id);
        this.cookingPreferencesDao = new BaseCollectionDao<>(MealCollections.COOKING_PREFERENCES, storage, new CookingPreferencesRowMapper(), p -> p.id, (p, id) -> p.id = id);
        this.weeklyFoodTargetDao = new BaseCollectionDao<>(MealCollections.WEEKLY_FOOD_TARGETS, storage, new WeeklyFoodTargetRowMapper(), target -> target.id, (target, id) -> target.id = id);
    }

    @Override
    public List<MealPlan> getMealPlans(LocalDate fromInclusive, LocalDate toInclusive) {
        return mealPlanDao.findAll(plan -> isDateInRange(plan.date, fromInclusive, toInclusive));
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

    @Override
    public WeeklyFoodTarget findWeeklyFoodTarget(String periodKey) {
        return weeklyFoodTargetDao.findAllByField(MealFieldKeys.PERIOD_KEY, periodKey).stream().findFirst().orElse(null);
    }

    @Override
    public void saveWeeklyFoodTarget(WeeklyFoodTarget weeklyFoodTarget) {
        weeklyFoodTargetDao.save(weeklyFoodTarget);
    }

    private static boolean isDateInRange(LocalDate date, LocalDate fromInclusive, LocalDate toInclusive) {
        return date != null && !date.isBefore(fromInclusive) && !date.isAfter(toInclusive);
    }
}
