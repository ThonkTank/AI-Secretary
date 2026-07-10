package com.autosecretary.features.meal.application;

import com.autosecretary.features.meal.domain.HouseholdMember;

public record HouseholdMemberOverview(
        HouseholdMember member,
        int age,
        int tdee) {
}
