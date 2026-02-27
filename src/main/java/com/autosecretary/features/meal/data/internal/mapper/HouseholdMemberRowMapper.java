package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.HouseholdMember;

import java.util.HashMap;
import java.util.Map;

public class HouseholdMemberRowMapper implements RowMapper<HouseholdMember> {
    @Override
    public Map<String, Object> toRow(HouseholdMember member) {
        Map<String, Object> row = new HashMap<>();
        row.put(MealFieldKeys.HouseholdMember.ID, member.id);
        row.put(MealFieldKeys.HouseholdMember.NAME, member.name);
        row.put(MealFieldKeys.HouseholdMember.BIRTH_YEAR, member.birthYear);
        row.put(MealFieldKeys.HouseholdMember.GENDER, MapperSupport.enumNameOrNull(member.gender));
        row.put(MealFieldKeys.HouseholdMember.WEIGHT_KG, member.weightKg);
        row.put(MealFieldKeys.HouseholdMember.HEIGHT_CM, member.heightCm);
        row.put(MealFieldKeys.HouseholdMember.TARGET_WEIGHT_KG, member.targetWeightKg);
        row.put(MealFieldKeys.HouseholdMember.ACTIVITY_LEVEL, MapperSupport.enumNameOrNull(member.activityLevel));
        row.put(MealFieldKeys.HouseholdMember.IS_ACTIVE, member.isActive ? 1 : 0);
        return row;
    }

    @Override
    public HouseholdMember fromRow(Map<String, Object> row) {
        HouseholdMember member = new HouseholdMember();
        member.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.HouseholdMember.ID));
        member.name = (String) row.get(MealFieldKeys.HouseholdMember.NAME);
        member.birthYear = MapperSupport.asInt(row.get(MealFieldKeys.HouseholdMember.BIRTH_YEAR));
        member.gender = MapperSupport.asEnum(HouseholdMember.Gender.class, row.get(MealFieldKeys.HouseholdMember.GENDER), null);
        member.weightKg = MapperSupport.asInt(row.get(MealFieldKeys.HouseholdMember.WEIGHT_KG));
        member.heightCm = MapperSupport.asInt(row.get(MealFieldKeys.HouseholdMember.HEIGHT_CM));
        member.targetWeightKg = MapperSupport.asInt(row.get(MealFieldKeys.HouseholdMember.TARGET_WEIGHT_KG));
        member.activityLevel = MapperSupport.asEnum(HouseholdMember.ActivityLevel.class, row.get(MealFieldKeys.HouseholdMember.ACTIVITY_LEVEL), null);
        member.isActive = MapperSupport.asBoolean(row.get(MealFieldKeys.HouseholdMember.IS_ACTIVE));
        return member;
    }
}
