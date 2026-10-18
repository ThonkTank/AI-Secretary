package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;

import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.ui.leaf.LeafSurface;
import de.thonktank.autosecretary.ui.leaf.WoodGrainView;
import de.thonktank.autosecretary.ui.today.FocusTaskView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w412dp-h892dp-xhdpi")
public final class FocusStepGrainVisibilityRobolectricTest {
    @Test public void initialHeightLimitExcludesUnlaidOutFollowingSteps() {
        Fixture fixture = new Fixture();
        fixture.layout(1000);
        fixture.assertOnlyVisibleSources();
    }

    @Test public void heightChangesRemoveAndRestoreSourcesWithoutRebinding() {
        Fixture fixture = new Fixture();
        fixture.layout(2400);
        int expanded = fixture.anchorCount();
        fixture.assertOnlyVisibleSources();
        fixture.layout(1000);
        fixture.assertOnlyVisibleSources();
        assertTrue("short card must hide steps", fixture.anchorCount() < expanded);
        fixture.layout(2400);
        fixture.assertOnlyVisibleSources();
        assertEquals(expanded, fixture.anchorCount());
    }

    @Test public void rebindWhileShortDoesNotLoseSourcesWhenCardGrows() {
        Fixture fixture = new Fixture();
        fixture.layout(1000);
        fixture.bind();
        fixture.layout(1000);
        fixture.assertOnlyVisibleSources();
        fixture.layout(2400);
        fixture.assertOnlyVisibleSources();
        assertEquals(12, fixture.anchorCount());
    }

    private static final class Fixture {
        final FrameLayout root;
        final FocusTaskView card;
        final WoodGrainView grain;
        final FocusTaskUiModel task;
        final DayPalette palette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT);

        Fixture() {
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            List<FocusStepUiModel> steps = new ArrayList<>();
            steps.add(FocusTaskFixtures.step("done", "Schon fertig").done(true).build());
            for (int index = 0; index < 11; index++)
                steps.add(FocusTaskFixtures.step("step-" + index, "Schritt " + index)
                        .combo(index + 1).build());
            task = FocusTaskFixtures.task("garden", "Animal Crossing")
                    .allowDefer(true).steps(steps).build();
            root = new FrameLayout(activity);
            card = new FocusTaskView(activity);
            bind();
            root.addView(card, new FrameLayout.LayoutParams(-1, -1));
            activity.setContentView(root);
            LeafSurface surface = ReflectionHelpers.getField(card, "surface");
            grain = ReflectionHelpers.getField(surface, "grain");
        }

        void bind() { card.bind(FocusCardTestModels.of(task, palette), false, action -> { }); }

        void layout(int height) {
            root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 824, height);
        }

        int anchorCount() {
            List<WoodGrainView.Anchor> anchors = ReflectionHelpers.getField(grain, "anchors");
            return anchors.size();
        }

        void assertOnlyVisibleSources() {
            int visibleSteps = 1 + ViewTestQueries.visibleFollowingStepRows(card);
            assertEquals("one vessel plus visible step rewards", visibleSteps + 1, anchorCount());
        }
    }
}
