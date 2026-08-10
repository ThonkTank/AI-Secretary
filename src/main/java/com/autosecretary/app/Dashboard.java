package com.autosecretary.app;

import com.autosecretary.core.Obligation;
import com.autosecretary.core.PlanItem;

import java.util.List;

public record Dashboard(List<PlanItem> focus, List<Obligation> obligations) {
}
