package de.thonktank.autosecretary;

import android.Manifest;
import android.animation.LayoutTransition;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The clock-colored daily workspace. All UI is native Android Views. */
public class MainActivity extends ComponentActivity {
    public static final String CONFIRM_TASK = "confirm_task";
    public static final String OPEN_EDITOR = "open_editor";
    private static final String RELEASES_URL = "https://github.com/ThonkTank/AI-Secretary/releases/latest";
    private static final String UI_PREFS = "forest_ui";
    private static final String THEME_MODE = "theme_mode";
    private static final String CALENDAR_ASKED = "calendar_asked";

    private final Handler minuteHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService calendarWorker = Executors.newSingleThreadExecutor();
    private final List<CalendarEventSnapshot> calendarEvents = new ArrayList<>();
    private TaskViewModel viewModel;
    private DashboardState dashboard = new DashboardState(0, Collections.emptyList());
    private ForestBackdropView forest;
    private LinearLayout screen, header, content, footer;
    private ScrollView scroll;
    private DayPalette palette;
    private Typeface newsreader, newsreaderItalic, alegreya, alegreyaBold;
    private String surface = "today";

    private final ActivityResultLauncher<String> calendarPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { loadCalendar(); render(); });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); loadFonts(); configureWindow(); buildShell();
        viewModel = new ViewModelProvider(this, new TaskViewModel.Factory(this)).get(TaskViewModel.class);
        viewModel.state().observe(this, value -> { dashboard = value; render(); TaskWidgetProvider.updateAll(this); });
        viewModel.errors().observe(this, message -> { if (message != null) leafDialog("geht so nicht", message, null); });
        if (DatabaseProvider.wasReset(this)) new AlertDialog.Builder(this).setTitle("Neuer, stabiler Start")
                .setMessage("Die Testdaten der ersten Version wurden bewusst zurückgesetzt. Neue Aufgaben werden jetzt zuverlässig lokal gespeichert.")
                .setPositiveButton("Verstanden", (d, w) -> DatabaseProvider.acknowledgeReset(this)).show();
        String confirmTask = getIntent().getStringExtra(CONFIRM_TASK); if (confirmTask != null) confirmClose(confirmTask, "dieses Vorhaben", 0);
        if (getIntent().getBooleanExtra(OPEN_EDITOR, false)) showCreateDialog(null);
        loadCalendar(); minuteHandler.post(minuteTick);
    }

    @Override protected void onResume() { super.onResume(); if (viewModel != null) viewModel.load(); loadCalendar(); }
    @Override protected void onDestroy() { minuteHandler.removeCallbacksAndMessages(null); calendarWorker.shutdownNow(); super.onDestroy(); }

    private final Runnable minuteTick = new Runnable() {
        @Override public void run() { applyPalette(); render(); minuteHandler.postDelayed(this, 60_000L); }
    };

    private void loadFonts() {
        newsreader = getResources().getFont(R.font.newsreader); newsreaderItalic = getResources().getFont(R.font.newsreader_italic);
        alegreya = getResources().getFont(R.font.alegreya_sans); alegreyaBold = getResources().getFont(R.font.alegreya_sans_bold);
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this); forest = new ForestBackdropView(this); root.addView(forest, matchFrame());
        screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); root.addView(screen, matchFrame());
        header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL);
        screen.addView(header, new LinearLayout.LayoutParams(-1, dp(70)));
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setLayoutTransition(new LayoutTransition());
        content.getLayoutTransition().setDuration(240); scroll.addView(content, match());
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setGravity(Gravity.CENTER_VERTICAL);
        screen.addView(footer, new LinearLayout.LayoutParams(-1, dp(70))); setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop(), bottom = insets.getSystemWindowInsetBottom();
            screen.setPadding(0, top, 0, bottom); return insets;
        }); applyPalette();
    }

    private void applyPalette() {
        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE); DayPalette.Mode mode;
        try { mode = DayPalette.Mode.valueOf(prefs.getString(THEME_MODE, DayPalette.Mode.AUTO.name())); }
        catch (Exception ignored) { mode = DayPalette.Mode.AUTO; }
        palette = DayPalette.at(LocalTime.now(), mode); if (forest != null) forest.setPalette(palette);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (luminance(palette.background) > .55) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        else flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void render() {
        if (content == null) return; applyPalette(); renderHeader(); content.removeAllViews(); renderFooter();
        if ("options".equals(surface)) renderOptions(); else if ("all".equals(surface)) renderAllPlaceholder(); else renderToday();
    }

    private void renderHeader() {
        header.removeAllViews(); header.setPadding(dp(76), 0, dp(22), 0);
        TextView greeting = serif(DayPalette.greeting(LocalTime.now()), 19, palette.status, true, 300);
        header.addView(greeting, new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = actionText("＋", 23, palette.lightText, palette.light, 40); add.setContentDescription("Aufgabe anlegen");
        add.setOnClickListener(v -> leafFlight(() -> showCreateDialog(null))); header.addView(add, new LinearLayout.LayoutParams(dp(40), dp(40)));
    }

    private void renderFooter() {
        footer.removeAllViews(); footer.setPadding(dp(60), 0, dp(22), 0);
        addNav("heute", "today"); addNav("alles ansehen", "all"); addNav("optionen", "options");
    }

    private void addNav(String label, String target) {
        boolean active = surface.equals(target); TextView text = sans(label, 17, active ? palette.ink2 : palette.status, false);
        text.setGravity(Gravity.CENTER); text.setMinHeight(dp(48)); text.setPadding(0, 0, 0, active ? dp(3) : 0);
        if (active) text.setBackground(underline(palette.light)); text.setOnClickListener(v -> { surface = target; scroll.scrollTo(0,0); render(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -1); params.setMargins(0,0,dp(26),0); footer.addView(text,params);
    }

    private void renderToday() {
        content.setPadding(dp(60), dp(8), dp(22), dp(26)); TaskSnapshot focus = dashboard.firstOpen();
        if (focus == null) { content.setPadding(dp(60), dp(120), dp(22), dp(26)); content.addView(emptyLeaf()); return; }
        TextView xp = serif(dashboard.xp + " XP · jeder Schritt zählt", 14, palette.muted, true, 300);
        LinearLayout.LayoutParams xpParams = match(); xpParams.setMargins(0,0,0,dp(12)); content.addView(xp,xpParams);
        content.addView(focusStack(focus), match());
        List<Block> blocks = secondaryBlocks(focus); int shown = Math.min(3, blocks.size()); boolean overdueShown = focus.overdue;
        for (int i=0;i<shown;i++) { Block block=blocks.get(i); View view;
            if (block.event != null) view = calendarLeaf(block.event); else {
                boolean bad = block.task.overdue && !overdueShown; overdueShown |= bad;
                String marker = block.task.done ? "geschafft" : bad ? "überfällig" : i == 0 ? "danach" : "später, sobald Platz ist";
                view = taskLeaf(block.task, marker, i > 0);
            }
            LinearLayout.LayoutParams params = match(); params.setMargins(0,dp(i==0?22:14),0,0); content.addView(view,params);
        }
        if (blocks.size()>shown) { TextView more=serif((blocks.size()-shown)+" weitere – sie laufen nicht weg.",14,palette.muted,true,300);
            LinearLayout.LayoutParams p=match(); p.setMargins(0,dp(16),0,0); content.addView(more,p); }
    }

    private List<Block> secondaryBlocks(TaskSnapshot focus) {
        List<Block> blocks = new ArrayList<>();
        for (TaskSnapshot task : dashboard.tasks) if (task != focus) blocks.add(new Block(task, null, slotMinute(task.slot), task.displayOrder));
        for (CalendarEventSnapshot event : calendarEvents) blocks.add(new Block(null,event,event.minuteOfDay,event.minuteOfDay));
        blocks.sort(Comparator.comparingInt((Block b)->b.minute).thenComparingLong(b->b.order)); return blocks;
    }

    private FrameLayout focusStack(TaskSnapshot task) {
        FrameLayout stack = new FrameLayout(this); stack.setClipChildren(false); int estimate = dp(task.steps.isEmpty()?225:365);
        if (dashboard.tasks.size()>1 || !calendarEvents.isEmpty()) {
            View back=decorativeLeaf(palette.leaf3,2.2f); FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,dp(82));
            bp.setMargins(dp(18),dp(34),dp(4),0); stack.addView(back,bp);
            View mid=decorativeLeaf(palette.leaf2,-1.5f); FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(-1,dp(88));
            mp.setMargins(dp(8),dp(18),dp(12),0); stack.addView(mid,mp);
        }
        View focus=focusLeaf(task); FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-1,-2); stack.addView(focus,fp);
        stack.setMinimumHeight(estimate); return stack;
    }

    private View decorativeLeaf(int color,float rotation) { View view=new View(this); view.setBackground(leaf(color,edgeColor(.16f),8,56,8,56));
        view.setRotation(rotation); view.setElevation(dp(5)); return view; }

    private View focusLeaf(TaskSnapshot task) {
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24),dp(24),dp(28),dp(24));
        card.setBackground(leaf(palette.leaf1,edgeColor(.32f),10,64,10,64)); card.setRotation(-.7f); card.setElevation(dp(12));
        FrameLayout titleRow=new FrameLayout(this); LinearLayout titleBlock=new LinearLayout(this); titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.addView(serif(task.overdue?"überfällig":"jetzt",20,task.overdue?palette.bad:palette.accent,true,300));
        TextView title=serif(task.title,task.title.length()>26?30:37,palette.ink,false,200); title.setLineSpacing(0,.96f);
        titleBlock.addView(title,match()); TextView sub=sans(task.softTime,17,palette.hint,false); LinearLayout.LayoutParams sp=match(); sp.setMargins(0,dp(8),0,0); titleBlock.addView(sub,sp);
        FrameLayout.LayoutParams tbp=new FrameLayout.LayoutParams(-1,-2); if(task.ringWeeks>0) tbp.setMargins(0,0,dp(66),0); titleRow.addView(titleBlock,tbp);
        if(task.ringWeeks>0){ YearRing ring=new YearRing(task.ringWeeks); FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.TOP|Gravity.END); titleRow.addView(ring,rp); }
        card.addView(titleRow,match());
        if(!task.steps.isEmpty()){ LinearLayout steps=new LinearLayout(this); steps.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp=match(); lp.setMargins(0,dp(18),0,0); card.addView(steps,lp);
            for(TaskStepSnapshot step:task.steps){ LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
                DewDot dot=new DewDot(step.done,false); dot.setContentDescription((step.done?"Erledigt: ":"Offen: ")+step.label);
                dot.setOnClickListener(v->{ v.animate().scaleX(1.14f).scaleY(1.14f).setDuration(90).withEndAction(()->{v.animate().scaleX(1).scaleY(1).setDuration(90); viewModel.toggleStep(step.id);}); });
                row.addView(dot,new LinearLayout.LayoutParams(dp(48),dp(48))); TextView label=sans(step.label,19,step.done?palette.done:palette.ink,false);
                if(step.done)label.setText(strike(step.label)); LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,-2,1); l.setMargins(dp(7),0,0,0); row.addView(label,l);
                LinearLayout.LayoutParams r=match(); r.setMargins(0,0,0,dp(1)); steps.addView(row,r); }
        }
        LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL); LinearLayout.LayoutParams ap=match(); ap.setMargins(0,dp(22),0,0); card.addView(actions,ap);
        TextView primary=primaryButton(task.actionLabel()); primary.setOnClickListener(v->{ if(task.terminalCondition)confirmClose(task.taskId,task.title,task.ringWeeks); else viewModel.complete(task.occurrenceId); });
        actions.addView(primary,new LinearLayout.LayoutParams(-2,dp(52))); if(openCount()>1){ TextView later=textLink("später",false); later.setOnClickListener(v->{card.animate().rotation(1.5f).alpha(.78f).setDuration(180).withEndAction(()->viewModel.defer(task.occurrenceId.isEmpty()?task.taskId:task.occurrenceId));});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(52)); lp.setMargins(dp(18),0,0,0); actions.addView(later,lp); }
        return card;
    }

    private View taskLeaf(TaskSnapshot task,String marker,boolean deep){
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24),dp(18),dp(18),dp(18));
        card.setBackground(leaf(deep?palette.leaf3:palette.leaf2,edgeColor(deep?.18f:.24f),56,8,56,8)); card.setRotation(deep?1.5f:1.1f); card.setElevation(dp(deep?5:7));
        TextView markerView=serif(marker,16,task.overdue&&!task.done?palette.bad:palette.muted,true,300); card.addView(markerView);
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); TextView title=serif(task.title,deep?21:23,task.done?palette.ink2:palette.ink,false,400);
        if(task.done)title.setText(strike(task.title)); row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        DewDot dot=new DewDot(task.done,task.done); dot.setEnabled(!task.done); if(!task.done)dot.setOnClickListener(v->{ if(task.terminalCondition)confirmClose(task.taskId,task.title,task.ringWeeks); else viewModel.complete(task.occurrenceId); });
        row.addView(dot,new LinearLayout.LayoutParams(dp(48),dp(48))); if(!task.done){TextView menu=sans("⋮",24,palette.dot,false); menu.setGravity(Gravity.CENTER); menu.setContentDescription("Aufgabenmenü"); menu.setOnClickListener(v->showTaskMenu(task)); row.addView(menu,new LinearLayout.LayoutParams(dp(42),dp(48)));} card.addView(row,match());
        if(!task.softTime.isEmpty()){TextView sub=sans(task.softTime,15,palette.hint,false); card.addView(sub,match());}
        if(task.steps.size()>1&&!task.done){ LinearLayout progress=new LinearLayout(this); progress.setGravity(Gravity.CENTER_VERTICAL); LinearLayout.LayoutParams pp=match(); pp.setMargins(0,dp(10),0,0); card.addView(progress,pp);
            int complete=0; for(TaskStepSnapshot step:task.steps){ if(step.done)complete++; View bar=new View(this); bar.setBackground(pill(step.done?palette.accent:withAlpha(palette.dot,.4f),3)); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(22),dp(5)); bp.setMargins(0,0,dp(6),0); progress.addView(bar,bp); }
            TextView label=serif(complete+" von "+task.steps.size(),14,palette.muted,true,300); progress.addView(label); }
        return card;
    }

    private View calendarLeaf(CalendarEventSnapshot event){ LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24),dp(16),dp(24),dp(16));
        card.setBackground(leaf(palette.calendar,withAlpha(palette.calendarInk,.28f),8,56,8,56)); card.setRotation(-1f); card.setElevation(dp(7));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); TextView time=sans(event.time,19,palette.calendarInk,true); row.addView(time); TextView title=serif(event.title,22,palette.calendarInk,false,400); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1); tp.setMargins(dp(9),0,0,0); row.addView(title,tp); card.addView(row,match()); card.addView(serif("im Kalender, fest",15,palette.calendarLabel,true,300)); return card; }

    private View emptyLeaf(){ LinearLayout leaf=new LinearLayout(this); leaf.setOrientation(LinearLayout.VERTICAL); leaf.setPadding(dp(28),dp(26),dp(28),dp(26)); leaf.setBackground(dashedBackground()); leaf.setRotation(-.5f);
        leaf.addView(serif("Gerade ist nichts offen. Das ist auch gut.",30,palette.ink,false,200)); TextView sub=sans("Lege nur die eine Sache an, die dir gerade helfen würde.",16,palette.hint,false); sub.setLineSpacing(0,1.25f); LinearLayout.LayoutParams sp=match(); sp.setMargins(0,dp(10),0,0); leaf.addView(sub,sp); TextView action=primaryButton("Aufgabe anlegen"); action.setOnClickListener(v->showCreateDialog(null)); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,dp(52)); ap.setMargins(0,dp(18),0,0); leaf.addView(action,ap); return leaf; }

    private void renderAllPlaceholder(){ content.setPadding(dp(60),dp(120),dp(22),dp(26)); LinearLayout leaf=new LinearLayout(this); leaf.setOrientation(LinearLayout.VERTICAL); leaf.setPadding(dp(28),dp(26),dp(28),dp(26)); leaf.setBackground(dashedBackground()); leaf.setRotation(-.5f); leaf.addView(serif("Alles ansehen kommt später.",30,palette.ink,false,200)); TextView sub=sans("Heute bleibt vorerst dein ruhiger Arbeitsbereich.",16,palette.hint,false); LinearLayout.LayoutParams p=match(); p.setMargins(0,dp(10),0,0); leaf.addView(sub,p); content.addView(leaf,match()); }

    private void renderOptions(){ content.setPadding(dp(60),dp(18),dp(22),dp(26)); content.addView(serif("optionen",20,palette.accent,true,300));
        content.addView(optionLeaf("Darstellung", "Farben folgen der Uhr. Der Sonnenstand bleibt immer in Bewegung.", themeChoices()), marginTop(14));
        boolean granted=checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED;
        boolean asked=getSharedPreferences(UI_PREFS,MODE_PRIVATE).getBoolean(CALENDAR_ASKED,false);
        boolean settings=!granted&&asked&&!shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR);
        LinearLayout calendarActions=new LinearLayout(this); calendarActions.setOrientation(LinearLayout.VERTICAL); TextView calendarAction=outlineButton(granted||settings?"App-Einstellungen öffnen":"Kalender freigeben");
        calendarAction.setOnClickListener(v->{if(granted||settings)openAppSettings();else{getSharedPreferences(UI_PREFS,MODE_PRIVATE).edit().putBoolean(CALENDAR_ASKED,true).apply();calendarPermission.launch(Manifest.permission.READ_CALENDAR);}}); calendarActions.addView(calendarAction,new LinearLayout.LayoutParams(-2,dp(46)));
        content.addView(optionLeaf("Google Kalender",granted?"Alle sichtbaren Kalender · nur lesen":"Noch nicht freigegeben · nur lesen",calendarActions),marginTop(14));
        String version="0.1.0"; try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        LinearLayout updateActions=new LinearLayout(this); TextView update=primaryButton("Nach Updates sehen"); update.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))); updateActions.addView(update,new LinearLayout.LayoutParams(-2,dp(52)));
        content.addView(optionLeaf("Updates","Installierte Version "+version,updateActions),marginTop(14)); }

    private View themeChoices(){ LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); String stored=getSharedPreferences(UI_PREFS,MODE_PRIVATE).getString(THEME_MODE,DayPalette.Mode.AUTO.name());
        String[] labels={"automatisch","hell","dunkel"}; DayPalette.Mode[] modes={DayPalette.Mode.AUTO,DayPalette.Mode.LIGHT,DayPalette.Mode.DARK};
        for(int i=0;i<labels.length;i++){boolean selected=stored.equals(modes[i].name()); TextView chip=sans(labels[i],15,selected?palette.accentText:palette.ink2,true); chip.setGravity(Gravity.CENTER); chip.setBackground(pill(selected?palette.accent:Color.TRANSPARENT,20)); if(!selected)((GradientDrawable)chip.getBackground()).setStroke(dp(1),palette.dot); final DayPalette.Mode mode=modes[i]; chip.setOnClickListener(v->{getSharedPreferences(UI_PREFS,MODE_PRIVATE).edit().putString(THEME_MODE,mode.name()).apply();render();TaskWidgetProvider.updateAll(this);}); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(40)); cp.setMargins(0,0,dp(8),0); chip.setPadding(dp(14),0,dp(14),0); row.addView(chip,cp);} return row; }

    private View optionLeaf(String title,String subtitle,View actions){ LinearLayout leaf=new LinearLayout(this); leaf.setOrientation(LinearLayout.VERTICAL); leaf.setPadding(dp(24),dp(18),dp(24),dp(18)); leaf.setBackground(leaf(palette.leaf2,edgeColor(.24f),56,8,56,8)); leaf.setElevation(dp(6)); leaf.addView(serif(title,23,palette.ink,false,400)); TextView sub=sans(subtitle,15,palette.hint,false); LinearLayout.LayoutParams sp=match(); sp.setMargins(0,dp(5),0,0); leaf.addView(sub,sp); LinearLayout.LayoutParams ap=match(); ap.setMargins(0,dp(14),0,0); leaf.addView(actions,ap); return leaf; }

    private void showTaskMenu(TaskSnapshot task){ new AlertDialog.Builder(this).setTitle(task.title).setItems(new String[]{"bearbeiten","verschieben","löschen"},(d,which)->{if(which==0)showCreateDialog(task);else if(which==1)showMoveDialog(task);else confirmDelete(task);}).show(); }
    private void showMoveDialog(TaskSnapshot task){String[] slots={TaskSlots.MORNING,TaskSlots.MIDDAY,TaskSlots.EVENING,TaskSlots.LATER};new AlertDialog.Builder(this).setTitle("verschieben").setSingleChoiceItems(slots,Arrays.asList(slots).indexOf(task.slot),(d,w)->{viewModel.move(task.taskId,slots[w]);d.dismiss();}).setNegativeButton("abbrechen",null).show();}
    private void confirmDelete(TaskSnapshot task){String loss=task.routine()?"Die Routine und ihr Jahresring von "+task.ringWeeks+" Wochen gehen verloren.":"Die Aufgabe geht verloren.";new AlertDialog.Builder(this).setTitle("„"+task.title+"“ löschen?").setMessage(loss).setNegativeButton("Behalten",null).setPositiveButton("Löschen",(d,w)->viewModel.delete(task.taskId)).show();}
    private void confirmClose(String taskId,String title,int ring){String suffix=ring>0?" Der Jahresring von "+ring+" Wochen bleibt als Fortschritt gespeichert.":"";new AlertDialog.Builder(this).setTitle("Vorhaben abschließen?").setMessage("„"+title+"“ wird geschlossen."+suffix).setNegativeButton("abbrechen",null).setPositiveButton("Bedingung erfüllt",(d,w)->viewModel.close(taskId)).show();}

    private void showCreateDialog(TaskSnapshot editing){ ScrollView sc=new ScrollView(this); LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(22),dp(8),dp(22),dp(8)); sc.addView(form);
        EditText name=input("Name der Aufgabe, z. B. Morgenroutine"); if(editing!=null)name.setText(editing.title); form.addView(name); form.addView(formLabel("Bevorzugte Tageszeit")); String[] slots={TaskSlots.MORNING,TaskSlots.MIDDAY,TaskSlots.EVENING,TaskSlots.LATER}; Spinner slot=spinner(slots); if(editing!=null)slot.setSelection(Arrays.asList(slots).indexOf(editing.slot)); form.addView(slot);
        Spinner repeat=null; EditText interval=null; CheckBox[] boxes=null; EditText steps=null; CheckBox ongoing=null; EditText condition=null;
        if(editing==null){ form.addView(formLabel("Wiederholung")); repeat=spinner(new String[]{"Einmalig","Täglich","Alle N Tage","Wochentage"});form.addView(repeat);interval=input("Bei „Alle N Tage“: Abstand, z. B. 2");interval.setInputType(InputType.TYPE_CLASS_NUMBER);form.addView(interval);form.addView(formLabel("Wochentage"));LinearLayout days=new LinearLayout(this);boxes=new CheckBox[7];String[] labels={"Mo","Di","Mi","Do","Fr","Sa","So"};for(int i=0;i<7;i++){boxes[i]=new CheckBox(this);boxes[i].setText(labels[i]);days.addView(boxes[i]);}form.addView(days);steps=input("Schritte – einer pro Zeile");steps.setMinLines(3);steps.setGravity(Gravity.TOP);form.addView(steps);ongoing=new CheckBox(this);ongoing.setText("Fortlaufendes Vorhaben");form.addView(ongoing);condition=input("Erledigungskondition, z. B. „Praktikum angenommen“");form.addView(condition);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(editing==null?"Neue Aufgabe":"Aufgabe bearbeiten").setView(sc).setNegativeButton("abbrechen",null).setPositiveButton("speichern",null).create(); final Spinner repeatF=repeat;final EditText intervalF=interval,stepsF=steps,conditionF=condition;final CheckBox[] boxesF=boxes;final CheckBox ongoingF=ongoing;
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(name.getText().toString().trim().isEmpty()){name.setError("geht so nicht: Ein kurzer Name reicht.");return;}if(editing!=null)viewModel.update(editing.taskId,name.getText().toString(),String.valueOf(slot.getSelectedItem()));else{int every=2;try{every=Integer.parseInt(intervalF.getText().toString());}catch(Exception ignored){}boolean[] selected=new boolean[7];for(int i=0;i<7;i++)selected[i]=boxesF[i].isChecked();String value=String.valueOf(repeatF.getSelectedItem());String recurrence="Einmalig".equals(value)?"ONCE":"Täglich".equals(value)?"DAILY":"Alle N Tage".equals(value)?"INTERVAL":"WEEKDAYS";viewModel.create(name.getText().toString(),String.valueOf(slot.getSelectedItem()),recurrence,every,ScheduleCalculator.weekdayMask(selected),new ArrayList<>(Arrays.asList(stepsF.getText().toString().split("\\n"))),ongoingF.isChecked(),conditionF.getText().toString());}dialog.dismiss();}));dialog.show(); }

    private void loadCalendar(){ if(isFinishing())return; calendarWorker.execute(()->{List<CalendarEventSnapshot> loaded=new CalendarRepository(this).today();runOnUiThread(()->{calendarEvents.clear();calendarEvents.addAll(loaded);if("today".equals(surface))render();TaskWidgetProvider.updateAll(this);});}); }
    private void openAppSettings(){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    private void leafFlight(Runnable end){ if(!android.animation.ValueAnimator.areAnimatorsEnabled()){end.run();return;} TextView leaf=serif("◜",32,palette.light,true,300);FrameLayout root=(FrameLayout)forest.getParent();FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.END);p.setMargins(0,dp(70),dp(28),0);root.addView(leaf,p);leaf.animate().translationX(-dp(180)).translationY(dp(120)).rotation(180).alpha(0).setDuration(420).withEndAction(()->{root.removeView(leaf);end.run();});}

    private TextView serif(String value,float size,int color,boolean italic,int weight){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setTypeface(italic?newsreaderItalic:newsreader);view.setFontVariationSettings("'wght' "+weight);view.setIncludeFontPadding(false);return view;}
    private TextView sans(String value,float size,int color,boolean bold){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setTypeface(bold?alegreyaBold:alegreya);view.setIncludeFontPadding(false);return view;}
    private TextView primaryButton(String value){TextView view=sans(value,17,palette.accentText,true);view.setGravity(Gravity.CENTER);view.setPadding(dp(28),0,dp(28),0);view.setMinHeight(dp(52));view.setBackground(pill(palette.accent,26));view.setElevation(dp(5));return view;}
    private TextView outlineButton(String value){TextView view=sans(value,16,palette.ink2,true);view.setGravity(Gravity.CENTER);view.setPadding(dp(22),0,dp(22),0);GradientDrawable bg=pill(Color.TRANSPARENT,23);bg.setStroke(dp(1),palette.dot);view.setBackground(bg);return view;}
    private TextView textLink(String value,boolean bad){TextView view=sans(value,17,bad?palette.bad:palette.hint,false);view.setGravity(Gravity.CENTER);view.setBackground(underline(palette.dot));return view;}
    private TextView actionText(String value,float size,int color,int background,int radius){TextView view=sans(value,size,color,false);view.setGravity(Gravity.CENTER);view.setBackground(pill(background,radius/2f));return view;}
    private TextView formLabel(String value){TextView label=serif(value,16,palette.muted,true,300);label.setPadding(0,dp(14),0,dp(3));return label;}
    private EditText input(String hint){EditText input=new EditText(this);input.setHint(hint);input.setTextColor(palette.ink);input.setHintTextColor(palette.muted);input.setTypeface(alegreya);return input;}
    private Spinner spinner(String[] values){Spinner spinner=new Spinner(this);spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));return spinner;}
    private void leafDialog(String title,String message,Runnable action){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Okay",(d,w)->{if(action!=null)action.run();}).show();}
    private GradientDrawable leaf(int color,int edge,float tl,float tr,float br,float bl){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadii(new float[]{dp(tl),dp(tl),dp(tr),dp(tr),dp(br),dp(br),dp(bl),dp(bl)});d.setStroke(Math.max(1,dp(1)),edge);return d;}
    private GradientDrawable pill(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable underline(int color){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.TRANSPARENT,Color.TRANSPARENT,color});d.setGradientCenter(.5f,.94f);return d;}
    private GradientDrawable dashedBackground(){GradientDrawable d=new GradientDrawable();d.setColor(Color.TRANSPARENT);d.setCornerRadii(new float[]{dp(10),dp(10),dp(64),dp(64),dp(10),dp(10),dp(64),dp(64)});d.setStroke(dp(1),palette.dot,dp(6),dp(5));return d;}
    private int edgeColor(float alpha){return withAlpha(palette.light,alpha);}
    private static int withAlpha(int color,float alpha){return (Math.round(alpha*255)<<24)|(color&0x00ffffff);}
    private static double luminance(int c){return(.2126*((c>>16)&255)+.7152*((c>>8)&255)+.0722*(c&255))/255d;}
    private SpannableString strike(String value){SpannableString s=new SpannableString(value);s.setSpan(new StrikethroughSpan(),0,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return s;}
    private int openCount(){int n=0;for(TaskSnapshot t:dashboard.tasks)if(!t.done)n++;return n;}
    private int slotMinute(String slot){if(TaskSlots.MORNING.equals(slot))return 8*60;if(TaskSlots.MIDDAY.equals(slot))return 12*60;if(TaskSlots.EVENING.equals(slot))return 18*60;return 21*60;}
    private LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(-1,-2);}
    private FrameLayout.LayoutParams matchFrame(){return new FrameLayout.LayoutParams(-1,-1);}
    private LinearLayout.LayoutParams marginTop(int value){LinearLayout.LayoutParams p=match();p.setMargins(0,dp(value),0,0);return p;}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private final class DewDot extends View { final boolean on,dim; final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); DewDot(boolean on,boolean dim){super(MainActivity.this);this.on=on;this.dim=dim;setClickable(true);setMinimumWidth(dp(48));setMinimumHeight(dp(48));}
        @Override protected void onDraw(Canvas canvas){float cx=getWidth()/2f,cy=getHeight()/2f,r=dp(13);paint.setStyle(on?Paint.Style.FILL:Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setColor(on?(dim?withAlpha(palette.dot,.2f):palette.accent):palette.dot);canvas.drawCircle(cx,cy,r,paint);if(on){paint.setStyle(Paint.Style.FILL);paint.setColor(dim?palette.done:palette.accentText);Path p=new Path();float s=dp(dim?8:10);p.moveTo(cx,cy-s*.55f);p.cubicTo(cx+s*.7f,cy-s*.1f,cx+s*.6f,cy+s*.55f,cx,cy+s*.65f);p.cubicTo(cx-s*.6f,cy+s*.55f,cx-s*.7f,cy-s*.1f,cx,cy-s*.55f);p.close();canvas.drawPath(p,paint);}}}
    private final class YearRing extends View {final int value;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);YearRing(int value){super(MainActivity.this);this.value=value;}
        @Override protected void onDraw(Canvas canvas){float c=getWidth()/2f;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setColor(palette.light);canvas.drawCircle(c,c,c-dp(1),paint);paint.setAlpha(153);canvas.drawCircle(c,c,c*.62f,paint);paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);paint.setTypeface(newsreader);paint.setTextSize(dp(17));paint.setTextAlign(Paint.Align.CENTER);Paint.FontMetrics fm=paint.getFontMetrics();canvas.drawText(String.valueOf(value),c,c-(fm.ascent+fm.descent)/2,paint);}}
    private static final class Block {final TaskSnapshot task;final CalendarEventSnapshot event;final int minute;final long order;Block(TaskSnapshot task,CalendarEventSnapshot event,int minute,long order){this.task=task;this.event=event;this.minute=minute;this.order=order;}}
}
