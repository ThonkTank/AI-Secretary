package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.WeeklyFoodTarget;

import java.util.HashMap;
import java.util.Map;

public class WeeklyFoodTargetRowMapper implements RowMapper<WeeklyFoodTarget> {
    @Override
    public Map<String, Object> toRow(WeeklyFoodTarget target) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", target.id);
        row.put("periodKey", target.periodKey);
        row.put("grainGrams", target.grainGrams);
        row.put("potatoGrams", target.potatoGrams);
        row.put("vegetableGrams", target.vegetableGrams);
        row.put("fruitGrams", target.fruitGrams);
        row.put("dairyGrams", target.dairyGrams);
        row.put("meatGrams", target.meatGrams);
        row.put("fishGrams", target.fishGrams);
        row.put("eggGrams", target.eggGrams);
        row.put("fatGrams", target.fatGrams);
        row.put("legumeGrams", target.legumeGrams);
        row.put("nutGrams", target.nutGrams);
        row.put("grainPlanned", target.grainPlanned);
        row.put("potatoPlanned", target.potatoPlanned);
        row.put("vegetablePlanned", target.vegetablePlanned);
        row.put("fruitPlanned", target.fruitPlanned);
        row.put("dairyPlanned", target.dairyPlanned);
        row.put("meatPlanned", target.meatPlanned);
        row.put("fishPlanned", target.fishPlanned);
        row.put("eggPlanned", target.eggPlanned);
        row.put("fatPlanned", target.fatPlanned);
        row.put("legumePlanned", target.legumePlanned);
        row.put("nutPlanned", target.nutPlanned);
        return row;
    }

    @Override
    public WeeklyFoodTarget fromRow(Map<String, Object> row) {
        WeeklyFoodTarget target = new WeeklyFoodTarget();
        target.id = MapperSupport.asNullableLong(row.get("id"));
        target.periodKey = (String) row.get("periodKey");
        target.grainGrams = MapperSupport.asInt(row.get("grainGrams"));
        target.potatoGrams = MapperSupport.asInt(row.get("potatoGrams"));
        target.vegetableGrams = MapperSupport.asInt(row.get("vegetableGrams"));
        target.fruitGrams = MapperSupport.asInt(row.get("fruitGrams"));
        target.dairyGrams = MapperSupport.asInt(row.get("dairyGrams"));
        target.meatGrams = MapperSupport.asInt(row.get("meatGrams"));
        target.fishGrams = MapperSupport.asInt(row.get("fishGrams"));
        target.eggGrams = MapperSupport.asInt(row.get("eggGrams"));
        target.fatGrams = MapperSupport.asInt(row.get("fatGrams"));
        target.legumeGrams = MapperSupport.asInt(row.get("legumeGrams"));
        target.nutGrams = MapperSupport.asInt(row.get("nutGrams"));
        target.grainPlanned = MapperSupport.asInt(row.get("grainPlanned"));
        target.potatoPlanned = MapperSupport.asInt(row.get("potatoPlanned"));
        target.vegetablePlanned = MapperSupport.asInt(row.get("vegetablePlanned"));
        target.fruitPlanned = MapperSupport.asInt(row.get("fruitPlanned"));
        target.dairyPlanned = MapperSupport.asInt(row.get("dairyPlanned"));
        target.meatPlanned = MapperSupport.asInt(row.get("meatPlanned"));
        target.fishPlanned = MapperSupport.asInt(row.get("fishPlanned"));
        target.eggPlanned = MapperSupport.asInt(row.get("eggPlanned"));
        target.fatPlanned = MapperSupport.asInt(row.get("fatPlanned"));
        target.legumePlanned = MapperSupport.asInt(row.get("legumePlanned"));
        target.nutPlanned = MapperSupport.asInt(row.get("nutPlanned"));
        return target;
    }
}
