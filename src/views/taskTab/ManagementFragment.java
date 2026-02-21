package views.taskTab;

public class ManagementFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        TaskViewModel vm = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        RecyclerView recyclerView = view.findViewById(R.id.TaskList);
        TaskRowAdapter adapter = new TaskRowAdapter(new ArrayList<>(), slot -> vm.checkOff(slot));
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        vm.getCheckList().observe(getViewLifecycleOwner(), content -> {
            adapter.setList(content);
        });
            
        Button button = view.findViewById(R.id.Button);
        button.setOnClickListener(v -> {
            vm.updateList();
        });
    }
}
