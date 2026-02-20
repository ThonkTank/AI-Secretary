package views.mainView;

//Android
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;

import views.taskTab.TaskRowAdapter;
import views.models.ViewTask;
import com.autosecretary.R;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MainViewModel vm = new ViewModelProvider(this).get(MainViewModel.class);

        RecyclerView recyclerView = findViewById(R.id.TaskList);
        TaskRowAdapter adapter = new TaskRowAdapter(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        vm.getCheckList().observe(this, content -> {
            adapter.setList(content);
        });
        
        Button button = findViewById(R.id.Button);
        button.setOnClickListener(v -> {
            vm.updateList();
        });
    }
}
