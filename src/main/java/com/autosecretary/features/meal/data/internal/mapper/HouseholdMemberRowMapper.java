package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.HouseholdMember;

import java.util.HashMap;
import java.util.Map;

public class HouseholdMemberRowMapper implements RowMapper<HouseholdMember> {
    @Override
    public Map<String, Object> toRow(HouseholdMember member) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", member.id);
        row.put("name", member.name);
        row.put("birthYear", member.birthYear);
        row.put("gender", member.gender == null ? null : member.gender.name());
        row.put("weightKg", member.weightKg);
        row.put("heightCm", member.heightCm);
        row.put("targetWeightKg", member.targetWeightKg);
        row.put("activityLevel", member.activityLevel == null ? null : member.activityLevel.name());
        row.put("isActive", member.isActive);
        return row;
    }

    @Override
    public HouseholdMember fromRow(Map<String, Object> row) {
        HouseholdMember member = new HouseholdMember();
        member.id = MapperSupport.asNullableLong(row.get("id"));
        member.name = (String) row.get("name");
        member.birthYear = MapperSupport.asInt(row.get("birthYear"));
        member.gender = MapperSupport.asEnum(HouseholdMember.Gender.class, row.get("gender"), null);
        member.weightKg = MapperSupport.asInt(row.get("weightKg"));
        member.heightCm = MapperSupport.asInt(row.get("heightCm"));
        member.targetWeightKg = MapperSupport.asInt(row.get("targetWeightKg"));
        member.activityLevel = MapperSupport.asEnum(HouseholdMember.ActivityLevel.class, row.get("activityLevel"), null);
        member.isActive = MapperSupport.asBoolean(row.get("isActive"));
        return member;
    }
}
