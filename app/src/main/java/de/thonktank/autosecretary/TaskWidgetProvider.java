package de.thonktank.autosecretary;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Responsive second view of the same task service, never a separate data path. */
public class TaskWidgetProvider extends AppWidgetProvider {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private enum Size { SMALL, WIDE, TALL, LARGE }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        PendingResult pending = goAsync(); Context app = context.getApplicationContext();
        WORKER.execute(() -> { try { for (int id : ids) manager.updateAppWidget(id, build(app,id,manager.getAppWidgetOptions(id))); }
            finally { pending.finish(); } });
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        PendingResult pending=goAsync();Context app=context.getApplicationContext();WORKER.execute(()->{try{manager.updateAppWidget(id,build(app,id,options));}finally{pending.finish();}});
    }

    static void updateAll(Context context) {
        Context app=context.getApplicationContext();WORKER.execute(()->{AppWidgetManager manager=AppWidgetManager.getInstance(app);
            int[] ids=manager.getAppWidgetIds(new ComponentName(app,TaskWidgetProvider.class));for(int id:ids)manager.updateAppWidget(id,build(app,id,manager.getAppWidgetOptions(id)));});
    }

    private static RemoteViews build(Context context,int id,Bundle options) {
        Size size=size(options);int layout=size==Size.SMALL?R.layout.task_widget:size==Size.WIDE?R.layout.task_widget_wide:size==Size.TALL?R.layout.task_widget_tall:R.layout.task_widget_large;
        RemoteViews view=new RemoteViews(context.getPackageName(),layout);DashboardState state=new TaskService(DatabaseProvider.get(context)).dashboard();
        DayPalette.Mode mode;try{mode=DayPalette.Mode.valueOf(context.getSharedPreferences("forest_ui",Context.MODE_PRIVATE).getString("theme_mode",DayPalette.Mode.AUTO.name()));}catch(Exception ignored){mode=DayPalette.Mode.AUTO;}
        DayPalette palette=DayPalette.at(LocalTime.now(),mode);styleBase(view,palette);view.setOnClickPendingIntent(R.id.widget_root,openApp(context));view.setOnClickPendingIntent(R.id.widget_title,openApp(context));
        TaskSnapshot focus=state.firstOpen();if(focus==null){view.setTextViewText(R.id.widget_marker,"heute");view.setTextViewText(R.id.widget_title,"Gerade ist nichts offen.");if(size!=Size.LARGE){view.setTextViewText(R.id.widget_action,"Aufgabe anlegen");view.setTextColor(R.id.widget_action,palette.accentText);if(Build.VERSION.SDK_INT>=31)view.setColorStateList(R.id.widget_action,"setBackgroundTintList",ColorStateList.valueOf(palette.accent));view.setOnClickPendingIntent(R.id.widget_action,openEditor(context));}if(size==Size.LARGE){view.setTextColor(R.id.widget_add,palette.lightText);if(Build.VERSION.SDK_INT>=31)view.setColorStateList(R.id.widget_add,"setBackgroundTintList",ColorStateList.valueOf(palette.light));view.setOnClickPendingIntent(R.id.widget_add,openEditor(context));}hideOptional(view,size);return view;}
        view.setTextViewText(R.id.widget_marker,focus.overdue?"überfällig":"jetzt");view.setTextColor(R.id.widget_marker,focus.overdue?palette.bad:palette.accent);view.setTextViewText(R.id.widget_title,focus.title);
        if(size==Size.SMALL){bindProgress(view,focus,palette);bindAction(view,focus,context,palette);}
        if(size==Size.WIDE){bindSteps(view,focus,context,palette,3);bindAction(view,focus,context,palette);int extra=Math.max(0,focus.steps.size()-3);view.setTextViewText(R.id.widget_more,extra>0?"und "+extra+" weitere":"");}
        if(size==Size.TALL){bindSteps(view,focus,context,palette,3);bindAction(view,focus,context,palette);bindCalendar(view,context,palette);}
        if(size==Size.LARGE){bindSteps(view,focus,context,palette,3);view.setOnClickPendingIntent(R.id.widget_add,openEditor(context));view.setTextColor(R.id.widget_add,palette.lightText);if(Build.VERSION.SDK_INT>=31)view.setColorStateList(R.id.widget_add,"setBackgroundTintList",ColorStateList.valueOf(palette.light));bindAfter(view,state,focus,palette);bindCalendar(view,context,palette);}
        return view;
    }

    private static void styleBase(RemoteViews view,DayPalette p){view.setTextColor(R.id.widget_marker,p.accent);view.setTextColor(R.id.widget_title,p.ink);
        if(Build.VERSION.SDK_INT>=31){view.setColorStateList(R.id.widget_root,"setBackgroundTintList",ColorStateList.valueOf(p.background));view.setColorStateList(R.id.widget_forest,"setImageTintList",ColorStateList.valueOf(p.tree));}}

    private static void bindProgress(RemoteViews view,TaskSnapshot task,DayPalette p){int[] ids={R.id.widget_progress_1,R.id.widget_progress_2,R.id.widget_progress_3};for(int i=0;i<ids.length;i++){boolean done=i<task.steps.size()&&task.steps.get(i).done;if(Build.VERSION.SDK_INT>=31)view.setColorStateList(ids[i],"setBackgroundTintList",ColorStateList.valueOf(done?p.accent:alpha(p.dot,.4f)));}}

    private static void bindSteps(RemoteViews view,TaskSnapshot task,Context context,DayPalette p,int count){int[] rows={R.id.widget_step_row_1,R.id.widget_step_row_2,R.id.widget_step_row_3};int[] dots={R.id.widget_step_dot_1,R.id.widget_step_dot_2,R.id.widget_step_dot_3};int[] texts={R.id.widget_step_text_1,R.id.widget_step_text_2,R.id.widget_step_text_3};for(int i=0;i<count;i++){boolean visible=i<task.steps.size();view.setViewVisibility(rows[i],visible?View.VISIBLE:View.GONE);if(!visible)continue;TaskStepSnapshot step=task.steps.get(i);view.setTextViewText(dots[i],step.done?"●":"○");view.setTextColor(dots[i],step.done?p.accent:p.dot);CharSequence label=step.done?strike(step.label):step.label;view.setTextViewText(texts[i],label);view.setTextColor(texts[i],step.done?p.done:p.ink);view.setOnClickPendingIntent(rows[i],step(context,step.id));}}

    private static void bindAction(RemoteViews view,TaskSnapshot task,Context context,DayPalette p){view.setTextViewText(R.id.widget_action,task.actionLabel());view.setTextColor(R.id.widget_action,p.accentText);if(Build.VERSION.SDK_INT>=31)view.setColorStateList(R.id.widget_action,"setBackgroundTintList",ColorStateList.valueOf(p.accent));PendingIntent action=task.terminalCondition?close(context,task.taskId):complete(context,task.occurrenceId);view.setOnClickPendingIntent(R.id.widget_action,action);}

    private static void bindAfter(RemoteViews view,DashboardState state,TaskSnapshot focus,DayPalette p){TaskSnapshot after=null;for(TaskSnapshot task:state.tasks)if(!task.done&&task!=focus){after=task;break;}view.setViewVisibility(R.id.widget_after_leaf,after==null?View.GONE:View.VISIBLE);if(after!=null){view.setTextViewText(R.id.widget_after_title,after.title);view.setTextColor(R.id.widget_after_title,p.ink);if(Build.VERSION.SDK_INT>=31)view.setColorStateList(R.id.widget_after_leaf,"setBackgroundTintList",ColorStateList.valueOf(p.leaf2));}}

    private static void bindCalendar(RemoteViews view,Context context,DayPalette p){List<CalendarEventSnapshot> events=new CalendarRepository(context).today();boolean show=!events.isEmpty();view.setViewVisibility(R.id.widget_calendar_leaf,show?View.VISIBLE:View.GONE);if(!show)return;CalendarEventSnapshot event=events.get(0);view.setTextViewText(R.id.widget_calendar_time,event.time);view.setTextViewText(R.id.widget_calendar_title,event.title);view.setTextColor(R.id.widget_calendar_time,p.calendarInk);view.setTextColor(R.id.widget_calendar_title,p.calendarInk);if(Build.VERSION.SDK_INT>=31)view.setColorStateList(R.id.widget_calendar_leaf,"setBackgroundTintList",ColorStateList.valueOf(p.calendar));}

    private static void hideOptional(RemoteViews view,Size size){if(size==Size.SMALL)view.setViewVisibility(R.id.widget_progress,View.GONE);if(size==Size.WIDE)view.setViewVisibility(R.id.widget_steps,View.GONE);if(size==Size.TALL){view.setViewVisibility(R.id.widget_steps,View.GONE);view.setViewVisibility(R.id.widget_calendar_leaf,View.GONE);}if(size==Size.LARGE){view.setViewVisibility(R.id.widget_steps,View.GONE);view.setViewVisibility(R.id.widget_after_leaf,View.GONE);view.setViewVisibility(R.id.widget_calendar_leaf,View.GONE);}}

    private static Size size(Bundle options){int width=options==null?160:options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,160);int height=options==null?160:options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,160);if(height<220)return width<220?Size.SMALL:Size.WIDE;return width<310?Size.TALL:Size.LARGE;}
    private static PendingIntent complete(Context c,String occurrence){return broadcast(c,TaskActionReceiver.COMPLETE,"occurrence_id",occurrence);}
    private static PendingIntent step(Context c,String id){return broadcast(c,TaskActionReceiver.TOGGLE_STEP,"step_id",id);}
    private static PendingIntent close(Context c,String id){return broadcast(c,TaskActionReceiver.CLOSE,"task_id",id);}
    private static PendingIntent broadcast(Context c,String action,String key,String value){Intent i=new Intent(c,TaskActionReceiver.class).setAction(action).putExtra(key,value);return PendingIntent.getBroadcast(c,(action+value).hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static PendingIntent openApp(Context c){return PendingIntent.getActivity(c,1,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static PendingIntent openEditor(Context c){Intent i=new Intent(c,MainActivity.class).putExtra(MainActivity.OPEN_EDITOR,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);return PendingIntent.getActivity(c,2,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static SpannableString strike(String text){SpannableString s=new SpannableString(text);s.setSpan(new StrikethroughSpan(),0,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return s;}
    private static int alpha(int color,float a){return(Math.round(a*255)<<24)|(color&0xffffff);}
}
