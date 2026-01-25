package scheduling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Registriert den täglichen Alarm nach Geräte-Neustart erneut.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            DailyPlanningScheduler.scheduleDaily(context);
        }
    }
}
