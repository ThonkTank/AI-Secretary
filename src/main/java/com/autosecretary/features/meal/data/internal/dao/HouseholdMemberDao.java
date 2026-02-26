package com.autosecretary.features.meal.data.internal.dao;

import com.autosecretary.features.meal.data.internal.MealCollections;
import com.autosecretary.features.meal.data.internal.mapper.HouseholdMemberRowMapper;
import com.autosecretary.features.meal.data.internal.storage.MealStorage;
import com.autosecretary.features.meal.domain.HouseholdMember;

public class HouseholdMemberDao extends BaseCollectionDao<HouseholdMember> {
    public HouseholdMemberDao(MealStorage storage) {
        super(MealCollections.HOUSEHOLD_MEMBERS, storage, new HouseholdMemberRowMapper(), member -> member.id, (member, id) -> member.id = id);
    }
}
