package views.mainView;

//Android
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;

import com.autosecretary.R;


public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MainViewModel vm = new ViewModelProvider(this).get(MainViewModel.class);

        TextView test = findViewById(R.id.Text);
        vm.getText().observe(this, content -> {
            test.setText(content);
        });
        
        Button button = findViewById(R.id.Button);
        button.setOnClickListener(v -> {
            vm.updateText();
        });
    }
}
