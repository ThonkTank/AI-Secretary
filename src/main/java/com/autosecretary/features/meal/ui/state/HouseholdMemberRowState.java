package com.autosecretary.features.meal.ui.state;

import com.autosecretary.features.meal.domain.HouseholdMember;

public record HouseholdMemberRowState(
        HouseholdMember member,
        int age,
        int tdee) {
}
