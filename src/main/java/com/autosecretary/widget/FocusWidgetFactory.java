package com.autosecretary.widget;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.autosecretary.R;
import com.autosecretary.app.SecretaryRepository;
import com.autosecretary.core.PlanItem;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class FocusWidgetFactory implements RemoteViewsService.RemoteViewsFactory {
    private final Context context;
    private final SecretaryRepository repository;
    private List<PlanItem> items = new ArrayList<>();

    FocusWidgetFactory(Context context, SecretaryRepository repository) {
        this.context = context;
        this.repository = repository;
    }

    @Override public void onCreate() { }

    @Override
    public void onDataSetChanged() {
        items = new ArrayList<>(repository.loadDashboardBlocking(3).focus());
    }

    @Override public int getCount() { return items.size(); }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        PlanItem item = items.get(position);
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_focus_row);
        row.setTextViewText(R.id.WidgetPosition,
                context.getString(position == 0 ? R.string.now : position == 1 ? R.string.next : R.string.later));
        row.setTextViewText(R.id.WidgetTitle, item.obligation().title);
        row.setViewVisibility(R.id.WidgetSteps, item.steps().isEmpty() ? View.GONE : View.VISIBLE);
        row.setTextViewText(R.id.WidgetSteps, String.join("  →  ", item.steps()));
        String time = item.suggestedStart() == null
                ? context.getString(R.string.as_soon_as_possible)
                : item.suggestedStart().format(DateTimeFormatter.ofPattern("HH:mm"));
        row.setTextViewText(R.id.WidgetMeta, time + " · " + item.obligation().durationMinutes + " Min");

        Intent complete = new Intent().setAction(FocusWidgetProvider.ACTION_COMPLETE)
                .putExtra(FocusWidgetProvider.EXTRA_ID, item.obligation().id);
        row.setOnClickFillInIntent(R.id.WidgetDone, complete);
        Intent later = new Intent().setAction(FocusWidgetProvider.ACTION_LATER)
                .putExtra(FocusWidgetProvider.EXTRA_ID, item.obligation().id);
        row.setOnClickFillInIntent(R.id.WidgetLater, later);
        return row;
    }

    @Override public RemoteViews getLoadingView() { return null; }
    @Override public int getViewTypeCount() { return 1; }
    @Override public long getItemId(int position) { return items.get(position).obligation().id.hashCode(); }
    @Override public boolean hasStableIds() { return true; }
    @Override public void onDestroy() { items.clear(); }
}
