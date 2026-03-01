package com.autosecretary.features.meal.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.autosecretary.features.meal.domain.HouseholdMember;

import java.util.UUID;

/**
 * Persisted representation of a household member used for DGE energy-requirement calculations
 * and meal planning.
 *
 * {@code isActive} controls whether this member is included in weekly food target calculations.
 * Inactive members are skipped entirely — they do not contribute to food targets or portion scaling.
 *
 * {@code birthYear} is the only age input; the derived age may be off by up to 1 year before
 * the member's actual birthday in a given year. This is an accepted precision trade-off.
 */
@Entity(tableName = "household_member")
public class MealHouseholdMemberEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    public String name;

    public int birthYear;

    public HouseholdMember.Gender gender;

    public int weightKg;

    public int heightCm;

    public int targetWeightKg;

    public HouseholdMember.ActivityLevel activityLevel;

    public boolean isActive;
}
