package de.thonktank.autosecretary.presentation.alltasks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Executable boundary around the phase-six management rendering split. */
public final class AllTasksRenderingArchitectureTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/de/thonktank/autosecretary/presentation/alltasks");

    @Test public void publicFacadeOnlyComposesTheRenderingResponsibilities() throws Exception {
        String facade = read("AllTasksView.java");
        assertTrue(facade.contains("AllTasksControlsView controls"));
        assertTrue(facade.contains("AllTasksListAdapter adapter"));
        assertTrue(facade.contains("AllTasksReorderController reorder"));
        for (String implementation : new String[]{
                "class RowAdapter", "class RowHolder", "class DragCallback",
                "RecyclerView.ViewHolder", "ItemTouchHelper", "extends Drawable",
                "RoundedHighlightSpan", "controlsKey", "removeAllViews("})
            assertFalse(implementation + " moved into AllTasksView",
                    facade.contains(implementation));
    }

    @Test public void extractedComponentsRemainConcretePackageBoundaries() throws Exception {
        for (String file : new String[]{
                "AllTasksControlsView.java", "AllTasksListAdapter.java",
                "AllTasksTaskCardViewHolder.java", "AllTasksStepViewHolder.java",
                "AllTasksScheduleViewHolder.java", "AllTasksEmptyViewHolder.java",
                "AllTasksReorderController.java", "AllTasksSearchHighlighter.java",
                "AllTasksCardDrawable.java"})
            assertTrue(file + " is missing", Files.isRegularFile(SOURCE.resolve(file)));
        assertTrue(read("AllTasksControlsView.java")
                .contains("void bind(AllTasksUiState state, DayPalette palette)"));
    }

    @Test public void holderBindsCannotRebuildTheirChildHierarchy() throws Exception {
        for (String file : new String[]{
                "AllTasksTaskCardViewHolder.java", "AllTasksStepViewHolder.java",
                "AllTasksScheduleViewHolder.java", "AllTasksEmptyViewHolder.java"}) {
            String source = read(file);
            assertFalse(file + " rebuilds children", source.contains("removeAllViews("));
            int bind = source.indexOf("@Override void bind");
            assertTrue(file + " has no typed bind", bind >= 0);
            assertFalse(file + " adds children after construction",
                    source.substring(bind).contains(".addView("));
        }
    }

    private static String read(String file) throws Exception {
        return new String(Files.readAllBytes(SOURCE.resolve(file)), StandardCharsets.UTF_8);
    }
}
