package com.autosecretary.features.budget.ui.internal;

import com.autosecretary.features.budget.domain.BalanceTimelinePoint;
import com.autosecretary.features.budget.ui.BudgetViewModel;
import com.autosecretary.features.budget.ui.state.BudgetChartPoint;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BudgetChartStateMapper {
    private final DateTimeFormatter dailyLabelFormatter;
    private final DateTimeFormatter monthlyLabelFormatter;

    public BudgetChartStateMapper(DateTimeFormatter dailyLabelFormatter,
                                  DateTimeFormatter monthlyLabelFormatter) {
        this.dailyLabelFormatter = dailyLabelFormatter;
        this.monthlyLabelFormatter = monthlyLabelFormatter;
    }

    public List<BudgetChartPoint> map(BudgetViewModel.TimeRangeFilter filter,
                                      List<BalanceTimelinePoint> series) {
        List<BudgetChartPoint> points = new ArrayList<>();
        for (BalanceTimelinePoint point : series) {
            String label = filter == BudgetViewModel.TimeRangeFilter.DAYS_30
                    ? point.getDate().format(dailyLabelFormatter)
                    : point.getDate().format(monthlyLabelFormatter);
            points.add(new BudgetChartPoint(label, point.getBalanceCents()));
        }
        return points;
    }
}
