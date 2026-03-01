package com.autosecretary.features.meal.data.repository;

import com.autosecretary.features.meal.data.dao.MealConsumptionLogDao;
import com.autosecretary.features.meal.data.dao.MealCookingPreferencesDao;
import com.autosecretary.features.meal.data.dao.MealHouseholdMemberDao;
import com.autosecretary.features.meal.data.dao.MealPlanDao;
import com.autosecretary.features.meal.data.dao.MealWeeklyFoodTargetDao;
import com.autosecretary.features.meal.data.entity.MealConsumptionLogEntity;
import com.autosecretary.features.meal.data.entity.MealCookingPreferencesEntity;
import com.autosecretary.features.meal.data.entity.MealHouseholdMemberEntity;
import com.autosecretary.features.meal.data.entity.MealPlanEntity;
import com.autosecretary.features.meal.data.entity.MealWeeklyFoodTargetEntity;
import com.autosecretary.features.meal.domain.ConsumptionLog;
import com.autosecretary.features.meal.domain.CookingPreferences;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import static com.autosecretary.features.meal.domain.Ingredient.FoodGroup.*;

public class MealRoomRepository implements MealRepository {

    private final MealPlanDao mealPlanDao;
    private final MealConsumptionLogDao consumptionLogDao;
    private final MealHouseholdMemberDao householdMemberDao;
    private final MealCookingPreferencesDao cookingPreferencesDao;
    private final MealWeeklyFoodTargetDao weeklyFoodTargetDao;

    public MealRoomRepository(MealPlanDao mealPlanDao,
                              MealConsumptionLogDao consumptionLogDao,
                              MealHouseholdMemberDao householdMemberDao,
                              MealCookingPreferencesDao cookingPreferencesDao,
                              MealWeeklyFoodTargetDao weeklyFoodTargetDao) {
        this.mealPlanDao = mealPlanDao;
        this.consumptionLogDao = consumptionLogDao;
        this.householdMemberDao = householdMemberDao;
        this.cookingPreferencesDao = cookingPreferencesDao;
        this.weeklyFoodTargetDao = weeklyFoodTargetDao;
    }

    // ---- MealPlan ----

    @Override
    public List<MealPlan> getMealPlans(LocalDate fromInclusive, LocalDate toInclusive) {
        return mealPlanDao.findByDateRange(fromInclusive, toInclusive).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public MealPlan findMealPlanById(String mealPlanId) {
        MealPlanEntity e = mealPlanDao.findById(mealPlanId);
        return e == null ? null : toDomain(e);
    }

    @Override
    public void saveMealPlan(MealPlan plan) {
        if (plan.id == null) plan.id = UUID.randomUUID().toString();
        mealPlanDao.insert(toEntity(plan));
    }

    @Override
    public void deleteMealPlan(String mealPlanId) {
        mealPlanDao.deleteById(mealPlanId);
    }

    private MealPlan toDomain(MealPlanEntity e) {
        MealPlan p = new MealPlan();
        p.id = e.id;
        p.date = e.date;
        p.mealType = e.mealType;
        p.recipeId = e.recipeId;
        p.plannedServings = e.plannedServings;
        p.isCompleted = e.isCompleted;
        p.actualServings = e.actualServings;
        p.completedAt = e.completedAt;
        p.itemId = e.itemId;
        p.recipeTitle = e.recipeTitle;
        p.estimatedCalories = e.estimatedCalories;
        return p;
    }

    private MealPlanEntity toEntity(MealPlan p) {
        MealPlanEntity e = new MealPlanEntity();
        e.id = p.id;
        e.date = p.date;
        e.mealType = p.mealType;
        e.recipeId = p.recipeId;
        e.plannedServings = p.plannedServings;
        e.isCompleted = p.isCompleted;
        e.actualServings = p.actualServings;
        e.completedAt = p.completedAt;
        e.itemId = p.itemId;
        e.recipeTitle = p.recipeTitle;
        e.estimatedCalories = p.estimatedCalories;
        return e;
    }

    // ---- ConsumptionLog ----

    @Override
    public List<ConsumptionLog> getConsumptionLogs(LocalDate fromInclusive, LocalDate toInclusive) {
        return consumptionLogDao.findConsumptionLogsByDateRange(fromInclusive, toInclusive).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void saveConsumptionLog(ConsumptionLog log) {
        if (log.id == null) log.id = UUID.randomUUID().toString();
        consumptionLogDao.insertConsumptionLog(toEntity(log));
    }

    private ConsumptionLog toDomain(MealConsumptionLogEntity e) {
        ConsumptionLog l = new ConsumptionLog();
        l.id = e.id;
        l.date = e.date;
        l.itemId = e.itemId;
        l.memberId = e.memberId;
        l.recipeId = e.recipeId;
        l.servingsConsumed = e.servingsConsumed;
        l.calories = e.calories;
        l.protein = e.protein;
        l.carbs = e.carbs;
        l.fat = e.fat;
        return l;
    }

    private MealConsumptionLogEntity toEntity(ConsumptionLog l) {
        MealConsumptionLogEntity e = new MealConsumptionLogEntity();
        e.id = l.id;
        e.date = l.date;
        e.itemId = l.itemId;
        e.memberId = l.memberId;
        e.recipeId = l.recipeId;
        e.servingsConsumed = l.servingsConsumed;
        e.calories = l.calories;
        e.protein = l.protein;
        e.carbs = l.carbs;
        e.fat = l.fat;
        return e;
    }

    // ---- HouseholdMember ----

    @Override
    public List<HouseholdMember> getHouseholdMembers() {
        return householdMemberDao.findAllHouseholdMembers().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void saveHouseholdMember(HouseholdMember member) {
        if (member.id == null) member.id = UUID.randomUUID().toString();
        householdMemberDao.insertHouseholdMember(toEntity(member));
    }

    @Override
    public void deleteHouseholdMember(String memberId) {
        householdMemberDao.deleteHouseholdMemberById(memberId);
    }

    private HouseholdMember toDomain(MealHouseholdMemberEntity e) {
        HouseholdMember m = new HouseholdMember();
        m.id = e.id;
        m.name = e.name;
        m.birthYear = e.birthYear;
        m.gender = e.gender;
        m.weightKg = e.weightKg;
        m.heightCm = e.heightCm;
        m.targetWeightKg = e.targetWeightKg;
        m.activityLevel = e.activityLevel;
        m.isActive = e.isActive;
        return m;
    }

    private MealHouseholdMemberEntity toEntity(HouseholdMember m) {
        MealHouseholdMemberEntity e = new MealHouseholdMemberEntity();
        e.id = m.id;
        e.name = m.name;
        e.birthYear = m.birthYear;
        e.gender = m.gender;
        e.weightKg = m.weightKg;
        e.heightCm = m.heightCm;
        e.targetWeightKg = m.targetWeightKg;
        e.activityLevel = m.activityLevel;
        e.isActive = m.isActive;
        return e;
    }

    // ---- CookingPreferences ----

    @Override
    public CookingPreferences getCookingPreferences() {
        MealCookingPreferencesEntity e = cookingPreferencesDao.findCookingPreferences();
        return e == null ? new CookingPreferences() : toDomain(e);
    }

    @Override
    public void saveCookingPreferences(CookingPreferences preferences) {
        preferences.id = "1";
        cookingPreferencesDao.insertCookingPreferences(toEntity(preferences));
    }

    private CookingPreferences toDomain(MealCookingPreferencesEntity e) {
        CookingPreferences p = new CookingPreferences();
        p.id = e.id;
        p.maxBreakfastCooking = e.maxBreakfastCooking;
        p.maxLunchCooking = e.maxLunchCooking;
        p.maxDinnerCooking = e.maxDinnerCooking;
        p.maxSnackCooking = e.maxSnackCooking;
        p.breakfastCookingDays = e.breakfastCookingDays;
        p.lunchCookingDays = e.lunchCookingDays;
        p.dinnerCookingDays = e.dinnerCookingDays;
        p.snackCookingDays = e.snackCookingDays;
        p.quickPrepMaxMinutes = e.quickPrepMaxMinutes;
        return p;
    }

    private MealCookingPreferencesEntity toEntity(CookingPreferences p) {
        MealCookingPreferencesEntity e = new MealCookingPreferencesEntity();
        e.id = p.id;
        e.maxBreakfastCooking = p.maxBreakfastCooking;
        e.maxLunchCooking = p.maxLunchCooking;
        e.maxDinnerCooking = p.maxDinnerCooking;
        e.maxSnackCooking = p.maxSnackCooking;
        e.breakfastCookingDays = p.breakfastCookingDays;
        e.lunchCookingDays = p.lunchCookingDays;
        e.dinnerCookingDays = p.dinnerCookingDays;
        e.snackCookingDays = p.snackCookingDays;
        e.quickPrepMaxMinutes = p.quickPrepMaxMinutes;
        return e;
    }

    // ---- WeeklyFoodTarget ----

    private record FgColumn(
            Ingredient.FoodGroup group,
            ToIntFunction<MealWeeklyFoodTargetEntity> target,
            ToIntFunction<MealWeeklyFoodTargetEntity> planned,
            ObjIntConsumer<MealWeeklyFoodTargetEntity> setTarget,
            ObjIntConsumer<MealWeeklyFoodTargetEntity> setPlanned) {}

    private static final List<FgColumn> FG_COLUMNS = List.of(
            new FgColumn(GRAIN,     e -> e.targetGrain,     e -> e.plannedGrain,     (e, v) -> e.targetGrain = v,     (e, v) -> e.plannedGrain = v),
            new FgColumn(POTATO,    e -> e.targetPotato,    e -> e.plannedPotato,    (e, v) -> e.targetPotato = v,    (e, v) -> e.plannedPotato = v),
            new FgColumn(VEGETABLE, e -> e.targetVegetable, e -> e.plannedVegetable, (e, v) -> e.targetVegetable = v, (e, v) -> e.plannedVegetable = v),
            new FgColumn(FRUIT,     e -> e.targetFruit,     e -> e.plannedFruit,     (e, v) -> e.targetFruit = v,     (e, v) -> e.plannedFruit = v),
            new FgColumn(DAIRY,     e -> e.targetDairy,     e -> e.plannedDairy,     (e, v) -> e.targetDairy = v,     (e, v) -> e.plannedDairy = v),
            new FgColumn(MEAT,      e -> e.targetMeat,      e -> e.plannedMeat,      (e, v) -> e.targetMeat = v,      (e, v) -> e.plannedMeat = v),
            new FgColumn(FISH,      e -> e.targetFish,      e -> e.plannedFish,      (e, v) -> e.targetFish = v,      (e, v) -> e.plannedFish = v),
            new FgColumn(EGG,       e -> e.targetEgg,       e -> e.plannedEgg,       (e, v) -> e.targetEgg = v,       (e, v) -> e.plannedEgg = v),
            new FgColumn(FAT,       e -> e.targetFat,       e -> e.plannedFat,       (e, v) -> e.targetFat = v,       (e, v) -> e.plannedFat = v),
            new FgColumn(LEGUME,    e -> e.targetLegume,    e -> e.plannedLegume,    (e, v) -> e.targetLegume = v,    (e, v) -> e.plannedLegume = v),
            new FgColumn(NUT,       e -> e.targetNut,       e -> e.plannedNut,       (e, v) -> e.targetNut = v,       (e, v) -> e.plannedNut = v));

    @Override
    public WeeklyFoodTarget findWeeklyFoodTarget(String periodKey) {
        MealWeeklyFoodTargetEntity e = weeklyFoodTargetDao.findWeeklyFoodTargetByPeriodKey(periodKey);
        return e == null ? null : toDomain(e);
    }

    @Override
    public void saveWeeklyFoodTarget(WeeklyFoodTarget target) {
        if (target.id == null) target.id = UUID.randomUUID().toString();
        weeklyFoodTargetDao.insertWeeklyFoodTarget(toEntity(target));
    }

    private WeeklyFoodTarget toDomain(MealWeeklyFoodTargetEntity e) {
        WeeklyFoodTarget t = new WeeklyFoodTarget();
        t.id = e.id;
        t.periodKey = e.periodKey;
        for (FgColumn col : FG_COLUMNS) {
            t.setTargetFor(col.group, col.target.applyAsInt(e));
            t.setPlannedFor(col.group, col.planned.applyAsInt(e));
        }
        return t;
    }

    private MealWeeklyFoodTargetEntity toEntity(WeeklyFoodTarget t) {
        MealWeeklyFoodTargetEntity e = new MealWeeklyFoodTargetEntity();
        e.id = t.id;
        e.periodKey = t.periodKey;
        for (FgColumn col : FG_COLUMNS) {
            col.setTarget.accept(e, t.getTargetFor(col.group));
            col.setPlanned.accept(e, t.getPlannedFor(col.group));
        }
        return e;
    }
}
