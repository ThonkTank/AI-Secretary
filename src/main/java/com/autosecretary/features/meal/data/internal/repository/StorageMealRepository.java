package com.autosecretary.features.meal.data.internal.repository;

import com.autosecretary.features.meal.data.internal.dao.ConsumptionLogDao;
import com.autosecretary.features.meal.data.internal.dao.CookingPreferencesDao;
import com.autosecretary.features.meal.data.internal.dao.HouseholdMemberDao;
import com.autosecretary.features.meal.data.internal.dao.MealPlanDao;
import com.autosecretary.features.meal.data.internal.dao.WeeklyFoodTargetDao;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.ConsumptionLog;
import com.autosecretary.features.meal.domain.CookingPreferences;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;

import java.time.LocalDate;
import java.util.List;

public class StorageMealRepository implements MealRepository {

    private static final long SINGLETON_PREFERENCES_ID = 1L;

    private final MealPlanDao mealPlanDao;
    private final ConsumptionLogDao consumptionLogDao;
    private final HouseholdMemberDao householdMemberDao;
    private final CookingPreferencesDao cookingPreferencesDao;
    private final WeeklyFoodTargetDao weeklyFoodTargetDao;

    public StorageMealRepository(MealStorage storage) {
        this.mealPlanDao = new MealPlanDao(storage);
        this.consumptionLogDao = new ConsumptionLogDao(storage);
        this.householdMemberDao = new HouseholdMemberDao(storage);
        this.cookingPreferencesDao = new CookingPreferencesDao(storage);
        this.weeklyFoodTargetDao = new WeeklyFoodTargetDao(storage);
    }

    @Override
    public List<MealPlan> getMealPlans(LocalDate fromInclusive, LocalDate toInclusive) {
        return mealPlanDao.findInRange(fromInclusive, toInclusive);
    }

    @Override
    public void saveMealPlan(MealPlan mealPlan) {
        long id = mealPlanDao.save(mealPlan);
        if (mealPlan.id == null) {
            mealPlan.id = id;
        }
    }

    @Override
    public void deleteMealPlan(long mealPlanId) {
        mealPlanDao.deleteById(mealPlanId);
    }

    @Override
    public List<ConsumptionLog> getConsumptionLogs(LocalDate fromInclusive, LocalDate toInclusive) {
        return consumptionLogDao.findInRange(fromInclusive, toInclusive);
    }

    @Override
    public void saveConsumptionLog(ConsumptionLog consumptionLog) {
        long id = consumptionLogDao.save(consumptionLog);
        if (consumptionLog.id == null) {
            consumptionLog.id = id;
        }
    }

    @Override
    public List<HouseholdMember> getHouseholdMembers() {
        return householdMemberDao.findAll();
    }

    @Override
    public void saveHouseholdMember(HouseholdMember member) {
        long id = householdMemberDao.save(member);
        if (member.id == null) {
            member.id = id;
        }
    }

    @Override
    public void deleteHouseholdMember(long memberId) {
        householdMemberDao.deleteById(memberId);
    }

    @Override
    public CookingPreferences getCookingPreferences() {
        CookingPreferences preferences = cookingPreferencesDao.findById(SINGLETON_PREFERENCES_ID);
        return preferences == null ? new CookingPreferences() : preferences;
    }

    @Override
    public void saveCookingPreferences(CookingPreferences preferences) {
        if (preferences.id == null) {
            preferences.id = SINGLETON_PREFERENCES_ID;
        }
        cookingPreferencesDao.save(preferences);
    }

    @Override
    public WeeklyFoodTarget findWeeklyFoodTarget(String periodKey) {
        return weeklyFoodTargetDao.findByPeriodKey(periodKey);
    }

    @Override
    public void saveWeeklyFoodTarget(WeeklyFoodTarget weeklyFoodTarget) {
        long id = weeklyFoodTargetDao.save(weeklyFoodTarget);
        if (weeklyFoodTarget.id == null) {
            weeklyFoodTarget.id = id;
        }
    }
}
