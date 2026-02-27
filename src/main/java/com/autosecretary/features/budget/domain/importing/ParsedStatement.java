package com.autosecretary.features.budget.domain.importing;

import java.time.LocalDate;
import java.util.List;

public record ParsedStatement(
        List<ParsedTransaction> transactions,
        LocalDate periodStart,
        LocalDate periodEnd
) {
}
