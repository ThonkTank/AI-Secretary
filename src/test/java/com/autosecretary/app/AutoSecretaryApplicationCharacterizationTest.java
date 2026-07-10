package com.autosecretary.app;

import static org.junit.Assert.assertEquals;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.shared.WidgetRefreshNotifier;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.junit.Test;

import java.lang.reflect.Field;

public final class AutoSecretaryApplicationCharacterizationTest extends AutoSecretaryRobolectricTest {
    @Test
    public void unlockRefreshDelegatesToWidgetRefreshNotifierInvariant() throws Exception {
        AutoSecretaryApplication application = new AutoSecretaryApplication();
        CountingWidgetRefreshNotifier notifier = new CountingWidgetRefreshNotifier();
        setCompositionRoot(application, new TestCompositionRoot(notifier));

        application.refreshTaskWidgetsAfterUnlock();

        assertEquals(1, notifier.taskRefreshCount);
        assertEquals(0, notifier.budgetRefreshCount);
    }

    private static void setCompositionRoot(AutoSecretaryApplication application,
                                           AppCompositionRoot compositionRoot) throws Exception {
        Field field = AutoSecretaryApplication.class.getDeclaredField("appCompositionRoot");
        field.setAccessible(true);
        field.set(application, compositionRoot);
    }

    private static final class TestCompositionRoot extends AppCompositionRoot {
        private final WidgetRefreshNotifier notifier;

        private TestCompositionRoot(WidgetRefreshNotifier notifier) {
            super((Application) ApplicationProvider.getApplicationContext());
            this.notifier = notifier;
        }

        @Override
        public WidgetRefreshNotifier getWidgetRefreshNotifier() {
            return notifier;
        }
    }

    private static final class CountingWidgetRefreshNotifier implements WidgetRefreshNotifier {
        private int taskRefreshCount;
        private int budgetRefreshCount;

        @Override
        public void refreshTaskWidgets() {
            taskRefreshCount++;
        }

        @Override
        public void refreshBudgetWidgets() {
            budgetRefreshCount++;
        }
    }
}
