package com.example.budgetapp.ui.ledger;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.text.*;

import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import com.example.budgetapp.*;
import com.example.budgetapp.ui.category.EntryCategoryDialogFragment;

import java.util.*;

public final class EntryEditorFragment extends BottomSheetDialogFragment {
    private LedgerViewModel vm;
    private boolean binding;

    public static final String TAG = "entry_editor_sheet";

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_entry_editor,
                container,
                false
        );
    }

    @Override
    public void onStart() {
        super.onStart();

        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog == null) {
            return;
        }

        // 打开时直接展开，避免只显示表单的一小部分。
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MainActivity activity = (MainActivity) requireActivity();
        vm = activity.ledgerViewModel();
        bind(view);

        getChildFragmentManager().setFragmentResultListener(EntryCategoryDialogFragment.RESULT, getViewLifecycleOwner(), (request, result) -> {
            if (!Objects.equals(vm.editorToken(), result.getString(EntryCategoryDialogFragment.TOKEN))
                    || !vm.field("kind", "EXPENSE").equals(result.getString(EntryCategoryDialogFragment.FLOW))) return;
            vm.chooseCategory(result.getString(EntryCategoryDialogFragment.ID));
            Toast.makeText(requireContext(), getString(R.string.entry_category_created, result.getString(EntryCategoryDialogFragment.NAME)), Toast.LENGTH_SHORT).show();
        });
        view.findViewById(R.id.entry_category_add).setOnClickListener(v -> {
            if (!Boolean.TRUE.equals(vm.busy().getValue()) && vm.isEditorReady())
                EntryCategoryDialogFragment.show(getChildFragmentManager(), vm.field("kind", "EXPENSE"), vm.editorToken());
        });

        view.findViewById(R.id.entry_close)
                .setOnClickListener(v -> dismiss());

        CheckBox remember = view.findViewById(R.id.entry_rule);
        remember.setChecked("true".equals(vm.field("makeRule", "false")));
        remember.setOnCheckedChangeListener((button, checked) -> vm.field("makeRule", Boolean.toString(checked), true));

        vm.editing.observe(getViewLifecycleOwner(), tx -> {
            if (tx != null && tx.id.equals(vm.editingId())) bind(view);
        });
        vm.categoryChoices().observe(getViewLifecycleOwner(), choices -> {
            if (choices != null) categories(view, choices);
        });
        vm.error.observe(getViewLifecycleOwner(), message -> ((TextView) view.findViewById(R.id.entry_error)).setText(message == null ? "" : getString(message)));
        vm.busy().observe(getViewLifecycleOwner(), busy -> enabled(view));


        vm.saved.observe(getViewLifecycleOwner(), saved -> {
            if (Boolean.TRUE.equals(saved)) {
                vm.saved.setValue(false);
                Toast.makeText(requireContext(), R.string.entry_saved, Toast.LENGTH_SHORT).show();
            }
        });
        vm.closed.observe(getViewLifecycleOwner(), closed -> {
            if (Boolean.TRUE.equals(closed)) {
                // 清掉已经处理过的关闭信号。
                vm.closed.setValue(false);

                // 关闭当前记账弹层。
                dismiss();
            }
        });
        vm.duplicate.observe(getViewLifecycleOwner(), duplicate -> {
            if (Boolean.TRUE.equals(duplicate)) {
                vm.duplicate.setValue(false);
                new AlertDialog.Builder(requireContext()).setMessage(R.string.entry_duplicate).setNegativeButton(R.string.entry_cancel, null).setPositiveButton(R.string.entry_distinct, (dialog, which) -> vm.save(true)).show();
            }
        });
        view.findViewById(R.id.entry_save).setOnClickListener(v -> vm.save(false));
        view.findViewById(R.id.entry_delete).setVisibility(vm.editingId() == null ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.entry_delete).setOnClickListener(v -> new AlertDialog.Builder(requireContext()).setTitle(R.string.delete_message).setMessage(getString(R.string.entry_delete_summary, vm.field("merchant", ""), vm.field("amount", ""))).setNegativeButton(R.string.entry_cancel, null).setPositiveButton(R.string.delete_confirm, (dialog, which) -> vm.delete()).show());
        vm.refreshCategoryChoices();
    }

    private void enabled(View view) {
        boolean ready =
                !Boolean.TRUE.equals(vm.busy().getValue())
                        && vm.isEditorReady();

        int[] ids = {
                R.id.entry_amount,
                R.id.entry_date,
                R.id.entry_expense,
                R.id.entry_income,
                R.id.entry_merchant,
                R.id.entry_note,
                R.id.entry_rule,
                R.id.entry_save,
                R.id.entry_delete
        };

        for (int id : ids) {
            view.findViewById(id).setEnabled(ready);
        }

        GridLayout grid = view.findViewById(R.id.entry_category);

        boolean categoryReady =
                ready && vm.categoryChoices().getValue() != null;
        view.findViewById(R.id.entry_category_add).setEnabled(categoryReady);

        for (int i = 0; i < grid.getChildCount(); i++) {
            grid.getChildAt(i).setEnabled(categoryReady);
        }
    }


    private void bind(View view) {
        binding = true;

        text(view, R.id.entry_amount, "amount", "");
        if (vm.editingId() == null) {
            ((EditText) view.findViewById(R.id.entry_date)).setText(R.string.entry_time_on_save);
        } else {
            text(view, R.id.entry_date, "date", vm.today());
        }
        text(view, R.id.entry_merchant, "merchant", "");
        text(view, R.id.entry_note, "note", "");

        RadioGroup group = view.findViewById(R.id.entry_kind);

        // 重新绑定时先移除监听，避免初始化触发业务修改。
        group.setOnCheckedChangeListener(null);

        boolean income = "INCOME".equals(
                vm.field("kind", "EXPENSE")
        );

        group.check(income
                ? R.id.entry_income
                : R.id.entry_expense);

        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            if (binding) {
                return;
            }

            String kind = checkedId == R.id.entry_income
                    ? "INCOME"
                    : "EXPENSE";

            // 继续使用现有逻辑，切换收支时刷新分类。
            vm.field("kind", kind, true);
            categoryHeading(view);
        });
        categoryHeading(view);

        // 点击日期打开日期选择器。
        view.findViewById(R.id.entry_date).setOnClickListener(v -> {
            if (vm.editingId() == null) return;
            java.time.LocalDate current = java.time.LocalDate.parse(
                    vm.field("date", vm.today())
            );

            android.app.DatePickerDialog picker =
                    new android.app.DatePickerDialog(
                            requireContext(),
                            (dateView, year, month, day) -> {
                                String date = java.time.LocalDate.of(
                                        year, month + 1, day
                                ).toString();

                                // 已有 TextWatcher 会把值写回 ViewModel。
                                ((EditText) view.findViewById(R.id.entry_date))
                                        .setText(date);
                            },
                            current.getYear(),
                            current.getMonthValue() - 1,
                            current.getDayOfMonth()
                    );

            // 原型不允许选择未来日期。
            long maxDate = java.time.LocalDate.parse(vm.today())
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();

            picker.getDatePicker().setMaxDate(maxDate);
            picker.show();
        });

        binding = false;
        enabled(view);
    }

    private void categoryHeading(View view) {
        ((TextView) view.findViewById(R.id.entry_category_label)).setText(
                "INCOME".equals(vm.field("kind", "EXPENSE")) ? R.string.entry_category_income : R.string.entry_category_expense);
    }

    private void text(View view, int id, String key, String fallback) {
        EditText input = view.findViewById(id);
        input.setText(vm.field(key, fallback));
        if (input.getTag() != null) return;
        input.setTag(key);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (!binding) vm.field(key, text.toString(), true);
            }

            public void afterTextChanged(Editable text) {
            }
        });
    }

    private void categories(
            View view,
            LedgerViewModel.EditorCategories choices
    ) {
        GridLayout grid = view.findViewById(R.id.entry_category);
        grid.removeAllViews();

        for (int i = 0; i < choices.items.size(); i++) {
            var category = choices.items.get(i);

            View cell = getLayoutInflater().inflate(
                    R.layout.item_entry_category,
                    grid,
                    false
            );

            TextView name = cell.findViewById(R.id.category_name);
            ImageView icon = cell.findViewById(R.id.category_icon);

            name.setText(category.name);
            icon.setImageResource(com.example.budgetapp.ui.common.CategoryIcons.forKey(category.iconKey));

            boolean selected = category.id.equals(choices.selectedId);

            cell.setSelected(selected);
            cell.setContentDescription(category.name);

            int color = android.graphics.Color.parseColor(
                    selected ? "#355D44" : "#92A184"
            );

            name.setTextColor(color);
            icon.setImageTintList(
                    android.content.res.ColorStateList.valueOf(color)
            );

            GridLayout.LayoutParams params =
                    new GridLayout.LayoutParams();

            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(i % 4, 1f);
            params.rowSpec = GridLayout.spec(i / 4);

            int gap = dp(2);
            params.setMargins(gap, gap, gap, gap);

            cell.setLayoutParams(params);

            cell.setOnClickListener(v -> {
                if (!Boolean.TRUE.equals(vm.busy().getValue())
                        && vm.isEditorReady()) {
                    vm.chooseCategory(category.id);
                }
            });

            grid.addView(cell);
        }

        enabled(view);
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    private int categoryIcon(String name) {
        return com.example.budgetapp.ui.common.CategoryIcons.forName(name);
    }

    private void spinner(View view, int id, String key, String[] values, String[] labels) {
        Spinner picker = view.findViewById(id);
        picker.setOnItemSelectedListener(null);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        picker.setAdapter(adapter);
        int selected = Arrays.asList(values).indexOf(vm.field(key, values[0]));
        picker.setSelection(Math.max(0, selected));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {
            }

            public void onItemSelected(AdapterView<?> parent, View row, int position, long item) {
                if (binding || parent.getAdapter() != adapter || position != parent.getSelectedItemPosition())
                    return;
                String value = values[position];
                if (!value.equals(vm.field(key, values[0]))) vm.field(key, value, true);
            }
        });
    }
}
