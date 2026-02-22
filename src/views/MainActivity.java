package views;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.fragment.app.Fragment;

import com.autosecretary.R;

import views.taskTab.ListFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.Container, new ListFragment())
                .commit();
        }
        BottomNavigationView tabBar = findViewById(R.id.TabBar);

        tabBar.setOnItemSelectedListener(item -> {
            Fragment fragment;
            if (item.getItemId() == R.id.tab_schedule) {
                fragment = new ListFragment();
            } else {
                //Placeholder
                fragment = new ListFragment();
            }
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.Container, fragment)
                .commit();
            return true;
        });
    }
}